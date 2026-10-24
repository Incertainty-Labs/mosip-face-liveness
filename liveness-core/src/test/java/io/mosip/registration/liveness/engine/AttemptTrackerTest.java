package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.Decision;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttemptTrackerTest {

    private static final LivenessPolicy POLICY = LivenessConfigLoader.load(Collections.<String, String>emptyMap(),
            Workflow.RESIDENT_REGISTRATION);

    @Test
    @DisplayName("TC-ENG-16: with max.retries 3, the third counted failure allows no more attempts")
    void thirdFailureEndsTheAttempts() {
        AttemptTracker tracker = new AttemptTracker(POLICY, 0);

        assertAll(() -> assertEquals(1, tracker.current()),
                () -> assertTrue(tracker.record(failed(LivenessErrorCode.CHALLENGE_FAILED))),
                () -> assertTrue(tracker.record(failed(LivenessErrorCode.PAD_FAILED))),
                () -> assertEquals(3, tracker.current()),
                () -> assertFalse(tracker.record(failed(LivenessErrorCode.CHALLENGE_TIMEOUT)), "the third"));
    }

    @Test
    @DisplayName("A pass, a cancel or a device error never uses an attempt")
    void someOutcomesNeverCount() {
        AttemptTracker tracker = new AttemptTracker(POLICY, 0);

        tracker.record(LivenessResult.builder(SessionState.PASSED, Decision.LIVE).build());
        tracker.record(LivenessResult.builder(SessionState.CANCELLED, Decision.INCONCLUSIVE).build());
        tracker.record(LivenessResult.builder(SessionState.ERROR, Decision.INCONCLUSIVE)
                .errorCode(LivenessErrorCode.DEVICE_CONNECTION_FAILED).build());

        assertEquals(1, tracker.current());
    }

    @Test
    @DisplayName("The limit is the smaller of max.retries and the client's own limit; reset() starts again")
    void limitAndReset() {
        AttemptTracker tracker = new AttemptTracker(POLICY, 2);
        assertEquals(2, tracker.limit());
        assertEquals(3, new AttemptTracker(POLICY, 5).limit());

        tracker.record(failed(LivenessErrorCode.CHALLENGE_FAILED));
        assertFalse(tracker.record(failed(LivenessErrorCode.CHALLENGE_FAILED)));
        tracker.reset();

        assertEquals(1, tracker.current());
    }

    private static LivenessResult failed(LivenessErrorCode code) {
        return LivenessResult.builder(SessionState.FAILED, Decision.INCONCLUSIVE).errorCode(code).build();
    }
}
