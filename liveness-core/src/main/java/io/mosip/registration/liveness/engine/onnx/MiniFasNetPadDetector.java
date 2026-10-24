package io.mosip.registration.liveness.engine.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.PadDetector;
import io.mosip.registration.liveness.api.PadScore;
import io.mosip.registration.liveness.api.Rect;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Passive PAD with one Silent-Face-Anti-Spoofing MiniFASNet model (Minivision, Apache-2.0), preprocessed exactly as
 * upstream: the face box is scaled around its centre ({@code CropImage._get_new_box}: the scale is capped so the box
 * fits the image, a box past an edge is shifted back, not clipped), the crop is resized to 80x80 bit for bit as
 * {@code cv2.resize} INTER_LINEAR does on 8-bit images, and fed as BGR 0-255; the live probability is softmax index 1.
 * Thread-safe; {@link #close()} releases the model.
 */
public final class MiniFasNetPadDetector implements PadDetector, AutoCloseable {

    static final int SIZE = 80;
    /** OpenCV's INTER_RESIZE_COEF_SCALE: interpolation weights are integers out of 2048. */
    private static final int ONE = 2048;
    private static final int REAL = 1;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;
    private final double scale;
    private final String name;
    private final String version;

    private MiniFasNetPadDetector(OrtEnvironment env, OrtSession session, double scale, String name, String version) {
        this.env = env;
        this.session = session;
        this.inputName = session.getInputNames().iterator().next();
        this.scale = scale;
        this.name = name;
        this.version = version;
    }

    /**
     * @param model   the MiniFASNet ONNX file
     * @param scale   the crop scale the model was trained with (2.7 for MiniFASNetV2, 4.0 for MiniFASNetV1SE)
     * @param name    the cue name, also the {@code pad.weights} key, for example {@code minifasnet_v2}
     * @param version the model version from {@code models.json}
     * @return the detector
     * @throws OrtException if the model cannot be loaded
     */
    public static MiniFasNetPadDetector load(Path model, double scale, String name, String version)
            throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        return new MiniFasNetPadDetector(env, env.createSession(model.toString(), new OrtSession.SessionOptions()),
                scale, name, version);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String modelVersion() {
        return version;
    }

    /**
     * @param frame       the frame
     * @param observation the analyzer's output; its box is the face to judge
     * @return the live probability, never a veto; the cue map holds it under {@link #name()}
     * @throws IllegalArgumentException if the observation has no face box
     * @throws IllegalStateException    if inference fails
     */
    @Override
    public PadScore score(FaceFrame frame, FaceObservation observation) {
        Rect box = observation.box();
        if (box == null) {
            throw new IllegalArgumentException("No face to judge");
        }
        int[] crop = cropBox(frame.width(), frame.height(), box, scale);
        float[] input = resizeBgr(frame, crop[0], crop[1], crop[2], crop[3]);
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), new long[] {1, 3, SIZE, SIZE});
                OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            FloatBuffer buffer = ((OnnxTensor) result.get(0)).getFloatBuffer();
            float[] logits = new float[buffer.remaining()];
            buffer.get(logits);
            double live = softmax(logits)[REAL];
            return new PadScore(live, false, Collections.singletonMap(name, live));
        } catch (OrtException e) {
            throw new IllegalStateException(name + " inference failed", e);
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            throw new IllegalStateException("Closing " + name + " failed", e);
        }
    }

    /** Upstream {@code CropImage._get_new_box}: left, top, right, bottom, all inclusive. */
    static int[] cropBox(int srcW, int srcH, Rect box, double scale) {
        double s = Math.min(Math.min((srcH - 1) / (double) box.height(), (srcW - 1) / (double) box.width()), scale);
        double newW = box.width() * s;
        double newH = box.height() * s;
        double cx = box.x() + box.width() / 2.0;
        double cy = box.y() + box.height() / 2.0;
        double left = cx - newW / 2;
        double top = cy - newH / 2;
        double right = cx + newW / 2;
        double bottom = cy + newH / 2;
        if (left < 0) {
            right -= left;
            left = 0;
        }
        if (top < 0) {
            bottom -= top;
            top = 0;
        }
        if (right > srcW - 1) {
            left -= right - srcW + 1;
            right = srcW - 1;
        }
        if (bottom > srcH - 1) {
            top -= bottom - srcH + 1;
            bottom = srcH - 1;
        }
        return new int[] {(int) left, (int) top, (int) right, (int) bottom};
    }

    /**
     * The inclusive crop resized to 80x80 exactly as OpenCV 4 {@code cv2.resize} INTER_LINEAR does on 8-bit images,
     * as BGR planes. OpenCV works in fixed point: weights out of 2048, a horizontal pass into ints, then a vertical
     * pass with its SIMD rounding, {@code ((h0 >> 4) * b0 >> 16) + ((h1 >> 4) * b1 >> 16)}, rounded by 2 bits. The
     * source position is clamped to the image horizontally; vertically only the rows are clamped and the weights
     * kept. Checked against {@code cv2.resize} (4.10) on 1000 random crops: identical.
     */
    private static float[] resizeBgr(FaceFrame frame, int left, int top, int right, int bottom) {
        int w = right - left + 1;
        int h = bottom - top + 1;
        int stride = frame.width();
        byte[] rgb = frame.rgb();
        int[] xs = new int[SIZE];
        int[] xa = new int[2 * SIZE];
        weights(w, xs, xa, true);
        int[] ys = new int[SIZE];
        int[] yb = new int[2 * SIZE];
        weights(h, ys, yb, false);

        int[] horizontal = new int[h * SIZE * 3];
        for (int y = 0; y < h; y++) {
            int row = ((top + y) * stride + left) * 3;
            for (int dx = 0; dx < SIZE; dx++) {
                int i0 = row + xs[dx] * 3;
                int o = (y * SIZE + dx) * 3;
                for (int c = 0; c < 3; c++) {
                    int p0 = rgb[i0 + c] & 0xFF;
                    horizontal[o + c] = xs[dx] >= w - 1 ? p0 * ONE
                            : p0 * xa[2 * dx] + (rgb[i0 + 3 + c] & 0xFF) * xa[2 * dx + 1];
                }
            }
        }

        int plane = SIZE * SIZE;
        float[] out = new float[3 * plane];
        for (int dy = 0; dy < SIZE; dy++) {
            int r0 = Math.min(Math.max(ys[dy], 0), h - 1) * SIZE * 3;
            int r1 = Math.min(Math.max(ys[dy] + 1, 0), h - 1) * SIZE * 3;
            int b0 = yb[2 * dy];
            int b1 = yb[2 * dy + 1];
            for (int dx = 0; dx < SIZE; dx++) {
                for (int c = 0; c < 3; c++) {
                    int k = dx * 3 + c;
                    int v = ((horizontal[r0 + k] >> 4) * b0 >> 16) + ((horizontal[r1 + k] >> 4) * b1 >> 16);
                    // RGB channel c goes to BGR plane 2 - c
                    out[(2 - c) * plane + dy * SIZE + dx] = Math.min(255, Math.max(0, (v + 2) >> 2));
                }
            }
        }
        return out;
    }

    /**
     * OpenCV's linear-resize source positions and weights for {@code src} pixels onto {@link #SIZE}: position
     * {@code floor((d + 0.5) * scale - 0.5)} in float, weights {@code rint((1 - f) * 2048)} and {@code rint(f * 2048)}.
     *
     * @param clamp whether a position outside the image is clamped with its weight zeroed (OpenCV's horizontal pass)
     */
    private static void weights(int src, int[] pos, int[] weight, boolean clamp) {
        double scale = 1.0 / ((double) SIZE / src);
        for (int d = 0; d < SIZE; d++) {
            float f = (float) ((d + 0.5) * scale - 0.5);
            int s = (int) Math.floor(f);
            f -= s;
            if (clamp && s < 0) {
                f = 0;
                s = 0;
            }
            if (clamp && s >= src - 1) {
                f = 0;
                s = src - 1;
            }
            pos[d] = s;
            weight[2 * d] = (int) Math.rint((1f - f) * ONE);
            weight[2 * d + 1] = (int) Math.rint(f * ONE);
        }
    }

    private static double[] softmax(float[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (float v : logits) {
            max = Math.max(max, v);
        }
        double sum = 0;
        double[] p = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            p[i] = Math.exp(logits[i] - max);
            sum += p[i];
        }
        for (int i = 0; i < p.length; i++) {
            p[i] /= sum;
        }
        return p;
    }
}
