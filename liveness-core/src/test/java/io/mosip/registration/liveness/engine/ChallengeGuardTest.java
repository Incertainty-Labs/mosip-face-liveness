package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.Rect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChallengeGuardTest {

    @Test
    @DisplayName("A second face during the challenges fails the attempt with LIV-005")
    void secondFaceFails() {
        ChallengeGuard guard = new ChallengeGuard();

        assertNull(guard.check(face(200, 120, 0), 0));
        assertEquals(LivenessErrorCode.MULTIPLE_FACES, guard.check(
                FaceObservation.builder().faces(2, new Rect(200, 120, 190, 240)).pose(0, 0, 0).build(), 50));
    }

    @Test
    @DisplayName("TC-ENG-10: the face may drift, but a box that jumps (IoU below 0.3 with the last face) fails with"
            + " LIV-009")
    void faceJumpFails() {
        ChallengeGuard guard = new ChallengeGuard();

        assertNull(guard.check(face(200, 120, 0), 0));
        assertNull(guard.check(face(230, 125, 0), 50), "a 30 px drift (IoU 0.70)");
        assertEquals(LivenessErrorCode.CHALLENGE_FAILED, guard.check(face(400, 125, 0), 100),
                "a 170 px jump (IoU 0.06)");
    }

    @Test
    @DisplayName("A head turns smoothly: yaw jumping more than 15° between frames under 100 ms apart fails with"
            + " LIV-009 (a cut in a replay); the same change over 200 ms does not")
    void implausibleMotionFails() {
        ChallengeGuard smooth = new ChallengeGuard();
        assertNull(smooth.check(face(200, 120, 0), 0));
        assertNull(smooth.check(face(200, 120, 8), 50), "8° in 50 ms, a natural turn");
        assertNull(smooth.check(face(200, 120, 28), 250), "20° over 200 ms");

        ChallengeGuard cut = new ChallengeGuard();
        assertNull(cut.check(face(200, 120, 0), 0));
        assertEquals(LivenessErrorCode.CHALLENGE_FAILED, cut.check(face(200, 120, 20), 50), "20° in 50 ms");
    }

    @Test
    @DisplayName("A frame without a face does not fail, but continuity carries across it: a face back in place is"
            + " fine, a face back elsewhere is a swap (LIV-009)")
    void continuityCarriesAcrossLostFrames() {
        FaceObservation none = FaceObservation.builder().build();

        ChallengeGuard back = new ChallengeGuard();
        assertNull(back.check(face(200, 120, 0), 0));
        assertNull(back.check(none, 50), "face lost");
        assertNull(back.check(face(210, 120, 2), 400), "back in place");

        ChallengeGuard swapped = new ChallengeGuard();
        assertNull(swapped.check(face(200, 120, 0), 0));
        assertNull(swapped.check(none, 50));
        assertEquals(LivenessErrorCode.CHALLENGE_FAILED, swapped.check(face(420, 120, 0), 400), "back elsewhere");
    }

    static FaceObservation face(int x, int y, float yaw) {
        return FaceObservation.builder().faces(1, new Rect(x, y, 190, 240)).pose(yaw, 10, 2).build();
    }
}
