package io.mosip.registration.liveness.engine.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.mosip.registration.liveness.api.FaceFrame;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Face detection with OpenCV Zoo's YuNet 2023mar (fixed 640x640 input). The frame is scaled so its longer side is
 * 640, placed top-left on a black 640x640 canvas, and fed as BGR 0-255; outputs are decoded and filtered the way
 * OpenCV's {@code FaceDetectorYN} does (score = sqrt(class x objectness), score at least 0.9, NMS at IoU 0.3), and
 * mapped back to frame coordinates. Thread-safe; {@link #close()} releases the model.
 */
public final class YuNetDetector implements AutoCloseable {

    static final int SIZE = 640;
    private static final int[] STRIDES = {8, 16, 32};
    private static final float SCORE_THRESHOLD = 0.9f;
    private static final float NMS_THRESHOLD = 0.3f;

    private final OrtEnvironment env;
    private final OrtSession session;

    private YuNetDetector(OrtEnvironment env, OrtSession session) {
        this.env = env;
        this.session = session;
    }

    /**
     * @param model the YuNet 2023mar ONNX file
     * @return the detector
     * @throws OrtException if the model cannot be loaded
     */
    public static YuNetDetector load(Path model) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        return new YuNetDetector(env, env.createSession(model.toString(), new OrtSession.SessionOptions()));
    }

    /**
     * @param model the YuNet 2023mar ONNX file's bytes, for example from a verified {@code ModelRegistry}
     * @return the model
     * @throws OrtException if the model cannot be loaded
     */
    public static YuNetDetector load(byte[] model) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        return new YuNetDetector(env, env.createSession(model, new OrtSession.SessionOptions()));
    }

    /**
     * @param frame an RGB frame of any size
     * @return the faces found with OpenCV's default minimum score (0.9), highest score first; empty when none
     * @throws IllegalStateException if inference fails
     */
    public List<FaceDetection> detect(FaceFrame frame) {
        return detect(frame, SCORE_THRESHOLD);
    }

    /**
     * @param frame    an RGB frame of any size
     * @param minScore the minimum confidence for a face to count ({@code LivenessPolicy.detectorMinScore()})
     * @return the faces found, highest score first; empty when there is none
     * @throws IllegalStateException if inference fails
     */
    public List<FaceDetection> detect(FaceFrame frame, float minScore) {
        float scale = (float) SIZE / Math.max(frame.width(), frame.height());
        float[] input = letterboxBgr(frame, scale);
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), new long[] {1, 3, SIZE, SIZE});
                OrtSession.Result result = session.run(Collections.singletonMap("input", tensor))) {
            List<FaceDetection> candidates = new ArrayList<FaceDetection>();
            for (int stride : STRIDES) {
                decode(stride, values(result, "cls_" + stride), values(result, "obj_" + stride),
                        values(result, "bbox_" + stride), values(result, "kps_" + stride), scale, minScore, candidates);
            }
            return nms(candidates);
        } catch (OrtException e) {
            throw new IllegalStateException("YuNet inference failed", e);
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            throw new IllegalStateException("Closing YuNet failed", e);
        }
    }

    private static float[] values(OrtSession.Result result, String name) {
        OnnxValue value = result.get(name).orElseThrow(() -> new IllegalStateException("YuNet output missing: " + name));
        FloatBuffer buffer = ((OnnxTensor) value).getFloatBuffer();
        float[] out = new float[buffer.remaining()];
        buffer.get(out);
        return out;
    }

    /** Bilinear resize with half-pixel centres (as OpenCV INTER_LINEAR), BGR planes, black padding right and bottom. */
    static float[] letterboxBgr(FaceFrame frame, float scale) {
        int srcW = frame.width();
        int srcH = frame.height();
        int dstW = Math.min(SIZE, Math.round(srcW * scale));
        int dstH = Math.min(SIZE, Math.round(srcH * scale));
        byte[] rgb = frame.rgb();
        float[] out = new float[3 * SIZE * SIZE];
        int plane = SIZE * SIZE;
        for (int dy = 0; dy < dstH; dy++) {
            float sy = Math.max(0f, (dy + 0.5f) / scale - 0.5f);
            int y0 = Math.min((int) sy, srcH - 1);
            int y1 = Math.min(y0 + 1, srcH - 1);
            float fy = sy - y0;
            for (int dx = 0; dx < dstW; dx++) {
                float sx = Math.max(0f, (dx + 0.5f) / scale - 0.5f);
                int x0 = Math.min((int) sx, srcW - 1);
                int x1 = Math.min(x0 + 1, srcW - 1);
                float fx = sx - x0;
                int o = dy * SIZE + dx;
                for (int c = 0; c < 3; c++) {
                    float top = (1 - fx) * channel(rgb, srcW, x0, y0, c) + fx * channel(rgb, srcW, x1, y0, c);
                    float bottom = (1 - fx) * channel(rgb, srcW, x0, y1, c) + fx * channel(rgb, srcW, x1, y1, c);
                    // RGB channel c goes to BGR plane 2 - c
                    out[(2 - c) * plane + o] = (1 - fy) * top + fy * bottom;
                }
            }
        }
        return out;
    }

    private static int channel(byte[] rgb, int width, int x, int y, int c) {
        return rgb[(y * width + x) * 3 + c] & 0xFF;
    }

    private static void decode(int stride, float[] cls, float[] obj, float[] bbox, float[] kps, float scale,
            float minScore, List<FaceDetection> out) {
        int cols = SIZE / stride;
        for (int i = 0; i < cls.length; i++) {
            float score = (float) Math.sqrt(clamp(cls[i]) * clamp(obj[i]));
            if (score < minScore) {
                continue;
            }
            int r = i / cols;
            int c = i % cols;
            float cx = (c + bbox[i * 4]) * stride;
            float cy = (r + bbox[i * 4 + 1]) * stride;
            float w = (float) Math.exp(bbox[i * 4 + 2]) * stride;
            float h = (float) Math.exp(bbox[i * 4 + 3]) * stride;
            float[] points = new float[10];
            for (int n = 0; n < 5; n++) {
                points[2 * n] = (kps[i * 10 + 2 * n] + c) * stride / scale;
                points[2 * n + 1] = (kps[i * 10 + 2 * n + 1] + r) * stride / scale;
            }
            out.add(new FaceDetection((cx - w / 2) / scale, (cy - h / 2) / scale, w / scale, h / scale, points, score));
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static List<FaceDetection> nms(List<FaceDetection> candidates) {
        List<FaceDetection> sorted = new ArrayList<FaceDetection>(candidates);
        Collections.sort(sorted, new Comparator<FaceDetection>() {
            @Override
            public int compare(FaceDetection a, FaceDetection b) {
                return Float.compare(b.score(), a.score());
            }
        });
        List<FaceDetection> kept = new ArrayList<FaceDetection>();
        for (FaceDetection candidate : sorted) {
            boolean overlaps = false;
            for (FaceDetection k : kept) {
                if (iou(candidate, k) > NMS_THRESHOLD) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private static float iou(FaceDetection a, FaceDetection b) {
        float ix = Math.max(0f, Math.min(a.x() + a.width(), b.x() + b.width()) - Math.max(a.x(), b.x()));
        float iy = Math.max(0f, Math.min(a.y() + a.height(), b.y() + b.height()) - Math.max(a.y(), b.y()));
        float inter = ix * iy;
        float union = a.width() * a.height() + b.width() * b.height() - inter;
        return union <= 0 ? 0f : inter / union;
    }
}
