package io.mosip.registration.liveness.engine.onnx;

import ai.onnxruntime.OrtException;
import io.mosip.registration.liveness.api.FaceAnalyzer;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.Rect;
import io.mosip.registration.liveness.engine.FrameQuality;
import java.nio.file.Path;
import java.util.List;

/**
 * The on-device face analyzer: YuNet finds the faces, and the largest one is measured with Face Mesh V2 (dense
 * landmarks, head pose) and Blendshape V2 (eye openness = 1 - eyeBlink, smile = mean of the two mouthSmile scores,
 * mouth openness = jawOpen); image quality comes from {@link FrameQuality} inside the face box. Left and right are the
 * person's own. With no face every measure is NaN. Thread-safe; {@link #close()} releases the models.
 */
public final class OnnxFaceAnalyzer implements FaceAnalyzer, AutoCloseable {

    static final String DETECTOR_FILE = "face_detection_yunet_2023mar.onnx";
    static final String MESH_FILE = "facemesh_v2_256.onnx";
    static final String BLENDSHAPE_FILE = "blendshape_v2_146x2.onnx";

    private final YuNetDetector detector;
    private final FaceMeshLandmarker landmarker;
    private final BlendshapeScorer scorer;
    private final float minScore;

    private OnnxFaceAnalyzer(YuNetDetector detector, FaceMeshLandmarker landmarker, BlendshapeScorer scorer,
            float minScore) {
        this.detector = detector;
        this.landmarker = landmarker;
        this.scorer = scorer;
        this.minScore = minScore;
    }

    /**
     * @param modelDir the folder holding the model bundle ({@code models.json} and its ONNX files)
     * @param minScore the minimum detector confidence for a face to count ({@code LivenessPolicy.detectorMinScore()})
     * @return the analyzer
     * @throws OrtException if a model cannot be loaded
     */
    public static OnnxFaceAnalyzer load(Path modelDir, double minScore) throws OrtException {
        YuNetDetector detector = YuNetDetector.load(modelDir.resolve(DETECTOR_FILE));
        FaceMeshLandmarker landmarker = null;
        try {
            landmarker = FaceMeshLandmarker.load(modelDir.resolve(MESH_FILE));
            BlendshapeScorer scorer = BlendshapeScorer.load(modelDir.resolve(BLENDSHAPE_FILE));
            return new OnnxFaceAnalyzer(detector, landmarker, scorer, (float) minScore);
        } catch (OrtException | RuntimeException e) {
            detector.close();
            if (landmarker != null) {
                landmarker.close();
            }
            throw e;
        }
    }

    /**
     * @param frame the frame
     * @return what was seen; {@code faceCount = 0} and every measure NaN when there is no face
     * @throws IllegalStateException if inference fails
     */
    @Override
    public FaceObservation analyze(FaceFrame frame) {
        List<FaceDetection> faces = detector.detect(frame, minScore);
        if (faces.isEmpty()) {
            return FaceObservation.builder().build();
        }
        FaceDetection face = largest(faces);
        FaceMesh mesh = landmarker.landmarks(frame, face);
        float[] scores = scorer.score(mesh);
        HeadPose pose = HeadPose.of(mesh);
        FrameQuality quality = FrameQuality.of(frame, face.x(), face.y(), face.width(), face.height());

        float[] xy = new float[mesh.size() * 2];
        for (int i = 0; i < mesh.size(); i++) {
            xy[2 * i] = mesh.x(i);
            xy[2 * i + 1] = mesh.y(i);
        }
        Rect box = new Rect(Math.round(face.x()), Math.round(face.y()), Math.round(face.width()),
                Math.round(face.height()));
        return FaceObservation.builder()
                .faces(faces.size(), box)
                .landmarks(xy)
                .pose(pose.yawDeg(), pose.pitchDeg(), pose.rollDeg())
                .eyesOpen(1f - scores[BlendshapeScorer.EYE_BLINK_LEFT], 1f - scores[BlendshapeScorer.EYE_BLINK_RIGHT])
                .expression((scores[BlendshapeScorer.MOUTH_SMILE_LEFT] + scores[BlendshapeScorer.MOUTH_SMILE_RIGHT]) / 2f,
                        scores[BlendshapeScorer.JAW_OPEN])
                .quality(quality.sharpness(), quality.brightness())
                .build();
    }

    @Override
    public void close() {
        detector.close();
        landmarker.close();
        scorer.close();
    }

    private static FaceDetection largest(List<FaceDetection> faces) {
        FaceDetection best = faces.get(0);
        for (FaceDetection f : faces) {
            if (f.width() * f.height() > best.width() * best.height()) {
                best = f;
            }
        }
        return best;
    }
}
