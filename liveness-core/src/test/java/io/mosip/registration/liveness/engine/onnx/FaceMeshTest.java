package io.mosip.registration.liveness.engine.onnx;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FaceMeshTest {

    private static YuNetDetector detector;
    private static FaceMeshLandmarker landmarker;
    private static BlendshapeScorer scorer;

    @BeforeAll
    static void loadModels() throws Exception {
        detector = YuNetDetector.load(new File("../models/face_detection_yunet_2023mar.onnx").toPath());
        landmarker = FaceMeshLandmarker.load(new File("../models/facemesh_v2_256.onnx").toPath());
        scorer = BlendshapeScorer.load(new File("../models/blendshape_v2_146x2.onnx").toPath());
    }

    @AfterAll
    static void close() {
        detector.close();
        landmarker.close();
        scorer.close();
    }

    @Test
    @DisplayName("Dense landmarks on the MOSIP face match the reference pipeline (OpenCV crop, same models)")
    void landmarksMatchReference() throws IOException {
        io.mosip.registration.liveness.api.FaceFrame frame = YuNetDetectorTest.frame(YuNetDetectorTest.face());
        FaceMesh mesh = landmarker.landmarks(frame, detector.detect(frame).get(0));

        // Reference: Python, OpenCV warpAffine crop (1.5x box, 256x256, RGB 0..1), same ONNX models (23 Oct 2026)
        float[][] expected = {
            {1, 120.96f, 171.33f, -31.56f}, {10, 125.16f, 89.99f, -9.94f}, {33, 80.26f, 136.87f, 7.44f},
            {152, 115.55f, 247.86f, 6.00f}, {263, 160.89f, 142.99f, 9.81f}, {468, 90.83f, 136.57f, 3.00f}};
        assertAll(() -> assertEquals(478, mesh.size()), () -> assertTrue(mesh.presence() > 0.9f, "presence " + mesh.presence()),
                () -> {
                    for (float[] e : expected) {
                        int i = (int) e[0];
                        assertNear(e[1], mesh.x(i), 2.5f, "x" + i);
                        assertNear(e[2], mesh.y(i), 2.5f, "y" + i);
                        assertNear(e[3], mesh.z(i), 2.5f, "z" + i);
                    }
                });
    }

    @Test
    @DisplayName("Blendshapes on the MOSIP face match the reference pipeline")
    void blendshapesMatchReference() throws IOException {
        io.mosip.registration.liveness.api.FaceFrame frame = YuNetDetectorTest.frame(YuNetDetectorTest.face());
        float[] scores = scorer.score(landmarker.landmarks(frame, detector.detect(frame).get(0)));

        assertAll(() -> assertEquals(52, scores.length),
                () -> assertNear(0.245f, scores[BlendshapeScorer.EYE_BLINK_LEFT], 0.05f, "eyeBlinkLeft"),
                () -> assertNear(0.179f, scores[BlendshapeScorer.EYE_BLINK_RIGHT], 0.05f, "eyeBlinkRight"),
                () -> assertNear(0.001f, scores[BlendshapeScorer.JAW_OPEN], 0.05f, "jawOpen"),
                () -> assertNear(0.576f, scores[BlendshapeScorer.MOUTH_SMILE_LEFT], 0.05f, "mouthSmileLeft"),
                () -> assertNear(0.564f, scores[BlendshapeScorer.MOUTH_SMILE_RIGHT], 0.05f, "mouthSmileRight"));
    }

    static void assertNear(float expected, float actual, float tolerance, String what) {
        assertTrue(Math.abs(expected - actual) <= tolerance, what + ": expected " + expected + " but was " + actual);
    }
}
