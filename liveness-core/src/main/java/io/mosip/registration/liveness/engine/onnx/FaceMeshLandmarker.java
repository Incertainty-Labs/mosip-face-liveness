package io.mosip.registration.liveness.engine.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.mosip.registration.liveness.api.FaceFrame;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;

/**
 * Dense face landmarks with MediaPipe Face Mesh V2 (PINTO ONNX conversion). The face is cropped as a square of 1.5x
 * the detector box's longer side around its centre (pixels outside the frame are black), resized to 256x256 and fed
 * as RGB in [0, 1]; the 478 points are mapped back to frame pixels. Thread-safe; {@link #close()} releases the model.
 */
public final class FaceMeshLandmarker implements AutoCloseable {

    static final int SIZE = 256;
    private static final float CROP_SCALE = 1.5f;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;

    private FaceMeshLandmarker(OrtEnvironment env, OrtSession session) {
        this.env = env;
        this.session = session;
        this.inputName = session.getInputNames().iterator().next();
    }

    /**
     * @param model the Face Mesh V2 ONNX file
     * @return the landmarker
     * @throws OrtException if the model cannot be loaded
     */
    public static FaceMeshLandmarker load(Path model) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        return new FaceMeshLandmarker(env, env.createSession(model.toString(), new OrtSession.SessionOptions()));
    }

    /**
     * @param model the Face Mesh V2 ONNX file's bytes, for example from a verified {@code ModelRegistry}
     * @return the model
     * @throws OrtException if the model cannot be loaded
     */
    public static FaceMeshLandmarker load(byte[] model) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        return new FaceMeshLandmarker(env, env.createSession(model, new OrtSession.SessionOptions()));
    }

    /**
     * @param frame the frame
     * @param face  the face found in it by {@link YuNetDetector}
     * @return the 478 landmarks in frame pixels
     * @throws IllegalStateException if inference fails
     */
    public FaceMesh landmarks(FaceFrame frame, FaceDetection face) {
        float size = Math.max(face.width(), face.height()) * CROP_SCALE;
        float x0 = face.x() + face.width() / 2 - size / 2;
        float y0 = face.y() + face.height() / 2 - size / 2;
        float step = size / SIZE;
        float[] input = crop(frame, x0, y0, step);
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), new long[] {1, 3, SIZE, SIZE});
                OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            Iterator<java.util.Map.Entry<String, ai.onnxruntime.OnnxValue>> outputs = result.iterator();
            float[] points = floats(outputs.next().getValue());
            float presenceLogit = floats(outputs.next().getValue())[0];
            float[] xyz = new float[FaceMesh.POINTS * 3];
            for (int i = 0; i < FaceMesh.POINTS; i++) {
                xyz[i * 3] = x0 + points[i * 3] * step;
                xyz[i * 3 + 1] = y0 + points[i * 3 + 1] * step;
                xyz[i * 3 + 2] = points[i * 3 + 2] * step;
            }
            return new FaceMesh(xyz, (float) (1 / (1 + Math.exp(-presenceLogit))));
        } catch (OrtException e) {
            throw new IllegalStateException("Face Mesh inference failed", e);
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            throw new IllegalStateException("Closing Face Mesh failed", e);
        }
    }

    private static float[] floats(ai.onnxruntime.OnnxValue value) {
        FloatBuffer buffer = ((OnnxTensor) value).getFloatBuffer();
        float[] out = new float[buffer.remaining()];
        buffer.get(out);
        return out;
    }

    /** Samples the square crop like OpenCV warpAffine (bilinear, constant black border) into RGB planes in [0, 1]. */
    private static float[] crop(FaceFrame frame, float x0, float y0, float step) {
        int w = frame.width();
        int h = frame.height();
        byte[] rgb = frame.rgb();
        int plane = SIZE * SIZE;
        float[] out = new float[3 * plane];
        for (int v = 0; v < SIZE; v++) {
            float sy = y0 + v * step;
            int iy = (int) Math.floor(sy);
            float fy = sy - iy;
            for (int u = 0; u < SIZE; u++) {
                float sx = x0 + u * step;
                int ix = (int) Math.floor(sx);
                float fx = sx - ix;
                int o = v * SIZE + u;
                for (int c = 0; c < 3; c++) {
                    float top = (1 - fx) * pixel(rgb, w, h, ix, iy, c) + fx * pixel(rgb, w, h, ix + 1, iy, c);
                    float bottom = (1 - fx) * pixel(rgb, w, h, ix, iy + 1, c) + fx * pixel(rgb, w, h, ix + 1, iy + 1, c);
                    out[c * plane + o] = ((1 - fy) * top + fy * bottom) / 255f;
                }
            }
        }
        return out;
    }

    private static int pixel(byte[] rgb, int w, int h, int x, int y, int c) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return 0;
        }
        return rgb[(y * w + x) * 3 + c] & 0xFF;
    }
}
