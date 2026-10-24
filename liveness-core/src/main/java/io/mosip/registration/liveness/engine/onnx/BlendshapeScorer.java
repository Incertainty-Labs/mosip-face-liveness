package io.mosip.registration.liveness.engine.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Expression scores with MediaPipe Blendshape V2 (PINTO ONNX conversion): 146 of the 478 Face Mesh points (x and y
 * in frame pixels, MediaPipe's {@code kLandmarksSubsetIdxs}) in, 52 scores in MediaPipe's {@code kBlendshapeNames}
 * order out. Left and right are the person's own. Thread-safe; {@link #close()} releases the model.
 */
public final class BlendshapeScorer implements AutoCloseable {

    /** Score index: person's left eye closing. */
    public static final int EYE_BLINK_LEFT = 9;
    /** Score index: person's right eye closing. */
    public static final int EYE_BLINK_RIGHT = 10;
    /** Score index: jaw open. */
    public static final int JAW_OPEN = 25;
    /** Score index: left mouth corner smiling. */
    public static final int MOUTH_SMILE_LEFT = 44;
    /** Score index: right mouth corner smiling. */
    public static final int MOUTH_SMILE_RIGHT = 45;

    /** From mediapipe/tasks/cc/vision/face_landmarker/face_blendshapes_graph.cc (Apache-2.0). */
    static final int[] SUBSET = {0, 1, 4, 5, 6, 7, 8, 10, 13, 14, 17, 21, 33, 37, 39, 40, 46, 52, 53, 54, 55, 58, 61,
        63, 65, 66, 67, 70, 78, 80, 81, 82, 84, 87, 88, 91, 93, 95, 103, 105, 107, 109, 127, 132, 133, 136, 144, 145,
        146, 148, 149, 150, 152, 153, 154, 155, 157, 158, 159, 160, 161, 162, 163, 168, 172, 173, 176, 178, 181, 185,
        191, 195, 197, 234, 246, 249, 251, 263, 267, 269, 270, 276, 282, 283, 284, 285, 288, 291, 293, 295, 296, 297,
        300, 308, 310, 311, 312, 314, 317, 318, 321, 323, 324, 332, 334, 336, 338, 356, 361, 362, 365, 373, 374, 375,
        377, 378, 379, 380, 381, 382, 384, 385, 386, 387, 388, 389, 390, 397, 398, 400, 402, 405, 409, 415, 454, 466,
        468, 469, 470, 471, 472, 473, 474, 475, 476, 477};

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;

    private BlendshapeScorer(OrtEnvironment env, OrtSession session) {
        this.env = env;
        this.session = session;
        this.inputName = session.getInputNames().iterator().next();
    }

    /**
     * @param model the Blendshape V2 ONNX file
     * @return the scorer
     * @throws OrtException if the model cannot be loaded
     */
    public static BlendshapeScorer load(Path model) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        return new BlendshapeScorer(env, env.createSession(model.toString(), new OrtSession.SessionOptions()));
    }

    /**
     * @param model the Blendshape V2 ONNX file's bytes, for example from a verified {@code ModelRegistry}
     * @return the model
     * @throws OrtException if the model cannot be loaded
     */
    public static BlendshapeScorer load(byte[] model) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        return new BlendshapeScorer(env, env.createSession(model, new OrtSession.SessionOptions()));
    }

    /**
     * @param mesh the face's landmarks
     * @return the 52 blendshape scores, 0..1
     * @throws IllegalStateException if inference fails
     */
    public float[] score(FaceMesh mesh) {
        float[] points = new float[SUBSET.length * 2];
        for (int i = 0; i < SUBSET.length; i++) {
            points[i * 2] = mesh.x(SUBSET[i]);
            points[i * 2 + 1] = mesh.y(SUBSET[i]);
        }
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(points), new long[] {1, SUBSET.length, 2});
                OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            FloatBuffer buffer = ((OnnxTensor) result.get(0)).getFloatBuffer();
            float[] scores = new float[buffer.remaining()];
            buffer.get(scores);
            return scores;
        } catch (OrtException e) {
            throw new IllegalStateException("Blendshape inference failed", e);
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            throw new IllegalStateException("Closing Blendshape failed", e);
        }
    }
}
