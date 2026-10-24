package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.ChallengeProgress;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.ChallengeVerifier;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.Rect;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Scripted observations at 50 ms steps (20 fps) against a baseline measured on a real webcam: the camera reads 11.5°
 * of pitch on a straight face, smile and mouth openness rest at 0, the eyes at about 0.8 open.
 */
class ChallengeVerifiersTest {

    private static final Baseline BASELINE = Baseline.of(Collections.singletonList(face(-3.7f, 11.5f, 3.5f, 0f, 0f,
            0.81f)));
    private static final long STEP = 50;

    @Test
    @DisplayName("TC-ENG-07: TURN_LEFT passes once yaw is 20° past the baseline and held 300 ms, not before")
    void turnLeftPassesAfterTheHold() {
        Challenge challenge = new Challenge(ChallengeType.TURN_LEFT, 5000);
        ChallengeVerifier verifier = ChallengeVerifiers.create(ChallengeType.TURN_LEFT, BASELINE);

        long t = 0;
        assertEquals(ChallengeProgress.IN_PROGRESS, verifier.update(challenge, neutral(), t));
        t += STEP;
        assertEquals(ChallengeProgress.IN_PROGRESS, verifier.update(challenge, yaw(-3.7f + 10), t), "on the way");
        long turned = t + STEP;
        for (t = turned; t < turned + 300; t += STEP) {
            assertEquals(ChallengeProgress.IN_PROGRESS, verifier.update(challenge, yaw(-3.7f + 25), t),
                    "turned for " + (t - turned) + " ms");
        }
        assertEquals(ChallengeProgress.PASSED, verifier.update(challenge, yaw(-3.7f + 25), t), "held 300 ms");
    }

    @Test
    @DisplayName("TC-ENG-08: turning the wrong way never passes TURN_LEFT; it times out at challenge.timeout.ms (LIV-010)")
    void wrongDirectionTimesOut() {
        Challenge challenge = new Challenge(ChallengeType.TURN_LEFT, 5000);
        ChallengeVerifier verifier = ChallengeVerifiers.create(ChallengeType.TURN_LEFT, BASELINE);

        assertEquals(ChallengeProgress.IN_PROGRESS, verifier.update(challenge, neutral(), 1000));
        for (long t = 1000 + STEP; t < 6000; t += STEP) {
            assertEquals(ChallengeProgress.IN_PROGRESS, verifier.update(challenge, yaw(-3.7f - 25), t), "at " + t);
        }
        assertEquals(ChallengeProgress.TIMED_OUT, verifier.update(challenge, yaw(-3.7f - 25), 6000),
                "5000 ms after the first frame");
    }

    @Test
    @DisplayName("A head already turned when the challenge starts does not pass: it must first come back near the"
            + " baseline (within 10°)")
    void startConditionNeedsTheBaselineFirst() {
        Challenge challenge = new Challenge(ChallengeType.TURN_LEFT, 5000);
        ChallengeVerifier verifier = ChallengeVerifiers.create(ChallengeType.TURN_LEFT, BASELINE);

        long t = 0;
        for (; t < 1000; t += STEP) {
            assertEquals(ChallengeProgress.IN_PROGRESS, verifier.update(challenge, yaw(-3.7f + 25), t),
                    "still turned from before, " + t + " ms");
        }
        assertEquals(ChallengeProgress.IN_PROGRESS, verifier.update(challenge, yaw(-3.7f + 8), t), "back near neutral");
        long turned = t + STEP;
        for (t = turned; t < turned + 300; t += STEP) {
            assertEquals(ChallengeProgress.IN_PROGRESS, verifier.update(challenge, yaw(-3.7f + 25), t));
        }
        assertEquals(ChallengeProgress.PASSED, verifier.update(challenge, yaw(-3.7f + 25), t), "a fresh turn");
    }

    /**
     * Each action as the analyzer measured it on a real face (A-11 grilling): turns of about 50°, looking up about
     * 29° above the straight face, a smile of 0.80 with the mouth closed, an open mouth of 0.47 that also reads 0.59
     * of smile, closed eyes at 0.25 openness. Here a little smaller, nearer the thresholds.
     */
    static FaceObservation action(ChallengeType type) {
        switch (type) {
            case TURN_LEFT:
                return yaw(-3.7f + 25);
            case TURN_RIGHT:
                return yaw(-3.7f - 25);
            case LOOK_UP:
                return face(-3.7f, 11.5f + 20, 3.5f, 0f, 0f, 0.81f);
            case LOOK_DOWN:
                return face(-3.7f, 11.5f - 20, 3.5f, 0f, 0f, 0.81f);
            case SMILE:
                return face(-3.7f, 11.5f, 3.5f, 0.80f, 0.01f, 0.76f);
            case OPEN_MOUTH:
                return face(-3.7f, 11.5f, 3.5f, 0.59f, 0.47f, 0.72f);
            default:
                return face(-3.7f, 11.5f, 3.5f, 0.05f, 0f, 0.25f);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ChallengeType.class, names = {"TURN_LEFT", "TURN_RIGHT", "LOOK_UP", "LOOK_DOWN", "SMILE",
        "OPEN_MOUTH", "CLOSE_EYES"})
    @DisplayName("TC-ENG-09: each challenge passes on its own action only (an open mouth, which also reads as a"
            + " smile, does not pass SMILE)")
    void eachChallengePassesOnlyOnItsOwnAction(ChallengeType verifierType) {
        for (ChallengeType actionType : new ChallengeType[] {ChallengeType.TURN_LEFT, ChallengeType.TURN_RIGHT,
            ChallengeType.LOOK_UP, ChallengeType.LOOK_DOWN, ChallengeType.SMILE, ChallengeType.OPEN_MOUTH,
            ChallengeType.CLOSE_EYES}) {
            Challenge challenge = new Challenge(verifierType, 5000);
            ChallengeVerifier verifier = ChallengeVerifiers.create(verifierType, BASELINE);
            ChallengeProgress last = ChallengeProgress.IN_PROGRESS;
            long t = 0;
            for (; t < 200; t += STEP) {
                last = verifier.update(challenge, neutral(), t);
            }
            for (; t <= 1200 && last == ChallengeProgress.IN_PROGRESS; t += STEP) {
                last = verifier.update(challenge, action(actionType), t);
            }
            assertEquals(verifierType == actionType ? ChallengeProgress.PASSED : ChallengeProgress.IN_PROGRESS, last,
                    verifierType + " shown " + actionType + " for 1 s");
        }
    }

    @Test
    @DisplayName("NEUTRAL_HOLD passes after 1 s within 5° of the baseline (the camera's own 11.5° pitch included),"
            + " eyes open, mouth closed; 8° off or an open mouth never passes")
    void neutralHold() {
        Challenge challenge = new Challenge(ChallengeType.NEUTRAL_HOLD, 5000);

        assertEquals(1000L, passTime(challenge, face(-3.7f + 3, 11.5f - 3, 3.5f + 2, 0f, 0.05f, 0.80f)));
        assertEquals(-1L, passTime(challenge, face(-3.7f, 11.5f + 8, 3.5f, 0f, 0f, 0.81f)), "pitch 8° off");
        assertEquals(-1L, passTime(challenge, face(-3.7f, 11.5f, 3.5f, 0.59f, 0.47f, 0.72f)), "mouth open");
    }

    @Test
    @DisplayName("BLINK passes on open, closed for at least 2 frames, open again within 600 ms; one closed frame or"
            + " eyes kept closed 800 ms do not pass")
    void blink() {
        assertEquals(ChallengeProgress.PASSED, blinkWithClosedFrames(3), "closed 150 ms");
        assertEquals(ChallengeProgress.IN_PROGRESS, blinkWithClosedFrames(1), "one closed frame");
        assertEquals(ChallengeProgress.IN_PROGRESS, blinkWithClosedFrames(16), "closed 800 ms");
    }

    @Test
    @DisplayName("CLOSE_EYES passes on real closed eyes, which read unevenly (0.25 and up to 0.47), and never on a"
            + " wink (one eye open)")
    void closeEyesPerEye() {
        Challenge challenge = new Challenge(ChallengeType.CLOSE_EYES, 5000);

        assertEquals(750L, passTime(challenge, eyes(0.25f, 0.47f)), "closed from 50 ms, unevenly");
        assertEquals(-1L, passTime(challenge, eyes(0.10f, 0.80f)), "a wink");
    }

    /** A straight face with the given eye openness. */
    private static FaceObservation eyes(float left, float right) {
        return FaceObservation.builder().faces(1, new Rect(200, 120, 190, 240)).pose(-3.7f, 11.5f, 3.5f)
                .expression(0f, 0f).eyesOpen(left, right).quality(0.8f, 0.5f).build();
    }

    @Test
    @DisplayName("A frame without a face restarts the hold, and reset() starts the challenge over, start condition"
            + " included")
    void faceLossRestartsTheHoldAndResetStartsOver() {
        Challenge challenge = new Challenge(ChallengeType.TURN_LEFT, 5000);
        ChallengeVerifier verifier = ChallengeVerifiers.create(ChallengeType.TURN_LEFT, BASELINE);
        FaceObservation turned = yaw(-3.7f + 25);

        verifier.update(challenge, neutral(), 0);
        verifier.update(challenge, turned, 50);
        verifier.update(challenge, turned, 250);
        assertEquals(ChallengeProgress.IN_PROGRESS, verifier.update(challenge, FaceObservation.builder().build(), 300),
                "face lost");
        assertEquals(ChallengeProgress.IN_PROGRESS, verifier.update(challenge, turned, 400), "300 ms since the first"
                + " turned frame, but the hold restarted");
        assertEquals(ChallengeProgress.PASSED, verifier.update(challenge, turned, 700), "held 300 ms again");

        verifier.reset();
        assertEquals(ChallengeProgress.IN_PROGRESS, verifier.update(challenge, turned, 800), "start condition again");
        assertEquals(ChallengeProgress.IN_PROGRESS, verifier.update(challenge, turned, 1200));
    }

    /** Eyes open 200 ms, closed for {@code closed} frames, open 300 ms; @return the last progress */
    private static ChallengeProgress blinkWithClosedFrames(int closed) {
        Challenge challenge = new Challenge(ChallengeType.BLINK, 5000);
        ChallengeVerifier verifier = ChallengeVerifiers.create(ChallengeType.BLINK, BASELINE);
        FaceObservation open = neutral();
        FaceObservation shut = face(-3.7f, 11.5f, 3.5f, 0f, 0f, 0.25f);
        ChallengeProgress last = ChallengeProgress.IN_PROGRESS;
        long t = 0;
        for (int i = 0; i < 4; i++, t += STEP) {
            last = verifier.update(challenge, open, t);
        }
        for (int i = 0; i < closed && last == ChallengeProgress.IN_PROGRESS; i++, t += STEP) {
            last = verifier.update(challenge, shut, t);
        }
        for (int i = 0; i < 6 && last == ChallengeProgress.IN_PROGRESS; i++, t += STEP) {
            last = verifier.update(challenge, open, t);
        }
        return last;
    }

    /** @return when the challenge passed (a neutral face at 0 ms, {@code o} from 50 ms, 20 fps), or -1 within 3 s */
    private static long passTime(Challenge challenge, FaceObservation o) {
        ChallengeVerifier verifier = ChallengeVerifiers.create(challenge.type(), BASELINE);
        for (long t = 0; t <= 3000; t += STEP) {
            if (verifier.update(challenge, t == 0 ? neutral() : o, t) == ChallengeProgress.PASSED) {
                return t;
            }
        }
        return -1;
    }

    static FaceObservation neutral() {
        return face(-3.7f, 11.5f, 3.5f, 0f, 0f, 0.81f);
    }

    static FaceObservation yaw(float yaw) {
        return face(yaw, 11.5f, 3.5f, 0f, 0f, 0.81f);
    }

    static FaceObservation face(float yaw, float pitch, float roll, float smile, float mouth, float eyes) {
        return FaceObservation.builder().faces(1, new Rect(200, 120, 190, 240)).pose(yaw, pitch, roll)
                .expression(smile, mouth).eyesOpen(eyes, eyes).quality(0.8f, 0.5f).build();
    }
}
