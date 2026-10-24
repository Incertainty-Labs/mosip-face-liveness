package io.mosip.registration.liveness.engine.onnx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.PadDetector;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import io.mosip.registration.liveness.engine.PassiveAssessor;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A-09 acceptance on a team member's own 2 s bursts, kept on the developer's machine only (never in git): with the
 * default policy, the passive score of every bona fide clip is above 0.8 and no presentation attack (a phone photo or
 * video, a monitor) ever reaches 0.5. Folders: system properties {@code liveness.localFaces} (bona fide, default
 * {@code ../../Mossip-local/faces/retake}) and {@code liveness.localAttacks} (default
 * {@code ../../Mossip-local/faces/attacks}); skipped when missing.
 */
@Tag("local-faces")
class LocalFacesPassiveTest {

    private static final File FACES = new File(System.getProperty("liveness.localFaces",
            "../../Mossip-local/faces/retake"));
    private static final File ATTACKS = new File(System.getProperty("liveness.localAttacks",
            "../../Mossip-local/faces/attacks"));
    private static final LivenessPolicy POLICY = LivenessConfigLoader.load(Collections.<String, String>emptyMap(),
            Workflow.RESIDENT_REGISTRATION);

    private static OnnxFaceAnalyzer analyzer;
    private static MiniFasNetPadDetector v2;
    private static MiniFasNetPadDetector v1se;

    @BeforeAll
    static void load() throws Exception {
        assumeTrue(FACES.isDirectory() && ATTACKS.isDirectory(), "local clips not present: " + FACES + ", " + ATTACKS);
        File models = new File("../models");
        analyzer = OnnxFaceAnalyzer.load(models.toPath(), POLICY.detectorMinScore());
        v2 = MiniFasNetPadDetector.load(new File(models, "minifasnet_v2_2.7_80x80.onnx").toPath(), 2.7,
                "minifasnet_v2", "1.0");
        v1se = MiniFasNetPadDetector.load(new File(models, "minifasnet_v1se_4.0_80x80.onnx").toPath(), 4.0,
                "minifasnet_v1se", "1.0");
    }

    @AfterAll
    static void close() {
        if (analyzer != null) {
            analyzer.close();
            v2.close();
            v1se.close();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"straight", "turn_left", "turn_right", "look_up", "smile", "eyes_closed", "mouth_open"})
    @DisplayName("A bona fide clip scores above 0.8 with the default policy")
    void bonaFideScoresAboveThreshold(String clip) throws IOException {
        double score = passiveScore(new File(FACES, clip + "_burst"));

        assertTrue(score > 0.8, clip + " scored " + score);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"live_dim"})
    @DisplayName("A bona fide clip in dim light still scores above 0.8 with the default policy")
    void dimBonaFideScoresAboveThreshold(String clip) throws IOException {
        double score = passiveScore(new File(ATTACKS, clip + "_burst"));

        assertTrue(score > 0.8, clip + " scored " + score);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"phone_photo_30cm", "phone_photo_50cm", "phone_video", "monitor_screen"})
    @DisplayName("A presentation attack never reaches 0.5 (a clip the quality gate keeps out is never scored at all)")
    void attackNeverReachesHalf(String clip) throws IOException {
        double score = passiveScore(new File(ATTACKS, clip + "_burst"));

        assertFalse(score >= 0.5, clip + " scored " + score);
    }

    /** The clip's frames, in order, through the analyzer and a fresh assessor; NaN if the window never filled. */
    private static double passiveScore(File burst) throws IOException {
        File[] frames = burst.listFiles((dir, name) -> name.endsWith(".jpg"));
        assumeTrue(frames != null && frames.length > 0, "clip not present: " + burst);
        Arrays.sort(frames);
        PassiveAssessor assessor = new PassiveAssessor(Arrays.<PadDetector>asList(v2, v1se), POLICY);
        for (File file : frames) {
            FaceFrame frame = YuNetDetectorTest.frame(ImageIO.read(file));
            assessor.add(frame, analyzer.analyze(frame));
        }
        return assessor.score();
    }
}
