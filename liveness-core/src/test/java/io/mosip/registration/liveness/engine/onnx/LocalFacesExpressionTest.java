package io.mosip.registration.liveness.engine.onnx;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Expressions on a team member's own pose and expression shots, kept on the developer's machine only (never in git).
 * Folder: system property {@code liveness.localFaces}, default {@code ../../Mossip-local/faces/retake}; the tests are
 * skipped when it is missing.
 */
@Tag("local-faces")
class LocalFacesExpressionTest {

    private static final File DIR = new File(System.getProperty("liveness.localFaces", "../../Mossip-local/faces/retake"));
    private static YuNetDetector detector;
    private static FaceMeshLandmarker landmarker;
    private static BlendshapeScorer scorer;

    @BeforeAll
    static void loadModels() throws Exception {
        assumeTrue(new File(DIR, "straight.jpg").isFile(), "local face shots not present: " + DIR);
        detector = YuNetDetector.load(new File("../models/face_detection_yunet_2023mar.onnx").toPath());
        landmarker = FaceMeshLandmarker.load(new File("../models/facemesh_v2_256.onnx").toPath());
        scorer = BlendshapeScorer.load(new File("../models/blendshape_v2_146x2.onnx").toPath());
    }

    @AfterAll
    static void close() {
        if (detector != null) {
            detector.close();
            landmarker.close();
            scorer.close();
        }
    }

    @Test
    @DisplayName("Closed eyes, a smile and an open mouth each score clearly above the straight face")
    void expressionsSeparate() throws IOException {
        float[] straight = scores("straight");
        float[] closed = scores("eyes_closed");
        float[] smile = scores("smile");
        float[] open = scores("mouth_open");

        assertAll(
                () -> assertTrue(blink(closed) > blink(straight) + 0.25f,
                        "blink closed " + blink(closed) + " vs straight " + blink(straight)),
                () -> assertTrue(smile(smile) > smile(straight) + 0.4f,
                        "smile " + smile(smile) + " vs straight " + smile(straight)),
                () -> assertTrue(open[BlendshapeScorer.JAW_OPEN] > straight[BlendshapeScorer.JAW_OPEN] + 0.25f,
                        "jawOpen " + open[BlendshapeScorer.JAW_OPEN] + " vs straight " + straight[BlendshapeScorer.JAW_OPEN]));
    }

    private static float blink(float[] s) {
        return (s[BlendshapeScorer.EYE_BLINK_LEFT] + s[BlendshapeScorer.EYE_BLINK_RIGHT]) / 2;
    }

    private static float smile(float[] s) {
        return (s[BlendshapeScorer.MOUTH_SMILE_LEFT] + s[BlendshapeScorer.MOUTH_SMILE_RIGHT]) / 2;
    }

    private static float[] scores(String pose) throws IOException {
        FaceFrame frame = YuNetDetectorTest.frame(ImageIO.read(new File(DIR, pose + ".jpg")));
        return scorer.score(landmarker.landmarks(frame, detector.detect(frame).get(0)));
    }
}
