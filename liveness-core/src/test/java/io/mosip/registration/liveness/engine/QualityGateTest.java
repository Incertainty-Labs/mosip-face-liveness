package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.Rect;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QualityGateTest {

    private static final int W = 640;
    private static final int H = 480;
    private static final LivenessPolicy DEFAULTS = LivenessConfigLoader.load(Collections.<String, String>emptyMap(),
            Workflow.RESIDENT_REGISTRATION);

    @Test
    @DisplayName("A well-framed, sharp, well-lit face passes")
    void goodFacePasses() {
        assertNull(QualityGate.check(face(256, 240, 0.7f, 0.5f), W, H, DEFAULTS));
    }

    @Test
    @DisplayName("No face is LIV-004 and two faces are LIV-005, each with its guidance message")
    void faceCountGuidance() {
        QualityGate.Verdict none = QualityGate.check(observation(0, null, 0.7f, 0.5f), W, H, DEFAULTS);
        QualityGate.Verdict two = QualityGate.check(observation(2, box(256, 240), 0.7f, 0.5f), W, H, DEFAULTS);

        assertAll(() -> assertEquals(LivenessErrorCode.FACE_NOT_DETECTED, none.code()),
                () -> assertEquals("liveness.guidance.face_not_found", none.messageKey()),
                () -> assertEquals(LivenessErrorCode.MULTIPLE_FACES, two.code()),
                () -> assertEquals("liveness.guidance.one_face_only", two.messageKey()));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "too small (15% of width),   96, 320, 240, 0.7, 0.5, liveness.guidance.face_not_found",
        "off-centre (centre at 85%), 256, 544, 240, 0.7, 0.5, liveness.guidance.face_not_found",
        "too dark,                  256, 320, 240, 0.7, 0.10, liveness.guidance.improve_lighting",
        "too bright,                256, 320, 240, 0.7, 0.90, liveness.guidance.improve_lighting",
        "blurred,                   256, 320, 240, 0.2, 0.5, liveness.guidance.improve_lighting"})
    @DisplayName("Framing and image problems are LIV-006 with the matching guidance")
    void qualityProblemsAreLiv006(String why, int width, int centreX, int centreY, float sharpness, float brightness,
            String messageKey) {
        QualityGate.Verdict verdict = QualityGate.check(
                observation(1, new Rect(centreX - width / 2, centreY - width * 4 / 6, width, width * 4 / 3), sharpness,
                        brightness), W, H, DEFAULTS);

        assertAll(() -> assertEquals(LivenessErrorCode.POOR_FACE_QUALITY, verdict.code(), why),
                () -> assertEquals(messageKey, verdict.messageKey(), why));
    }

    @Test
    @DisplayName("The limits come from the policy: a lower minimum face width accepts a smaller face")
    void limitsComeFromThePolicy() {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness.quality.min.face.width", "0.12");
        LivenessPolicy policy = LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);

        assertNull(QualityGate.check(observation(1, box(96, 240), 0.7f, 0.5f), W, H, policy));
    }

    private static FaceObservation face(int width, int centreY, float sharpness, float brightness) {
        return observation(1, box(width, centreY), sharpness, brightness);
    }

    /** A face of the given width (4:3 tall) centred horizontally at x = 320. */
    private static Rect box(int width, int centreY) {
        int height = width * 4 / 3;
        return new Rect(320 - width / 2, centreY - height / 2, width, height);
    }

    private static FaceObservation observation(int faces, Rect box, float sharpness, float brightness) {
        return FaceObservation.builder().faces(faces, box).quality(sharpness, brightness).build();
    }
}
