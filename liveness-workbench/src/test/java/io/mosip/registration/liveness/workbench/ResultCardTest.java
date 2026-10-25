package io.mosip.registration.liveness.workbench;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.mosip.registration.liveness.api.ChallengeOutcome;
import io.mosip.registration.liveness.api.ChallengeProgress;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.Decision;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.SessionState;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultCardTest {

    @Test
    @DisplayName("The challenges line says none were drawn, none were finished (vetoed at the first), or lists them")
    void challengesLine() {
        LivenessResult passive = LivenessResult.builder(SessionState.PASSED, Decision.LIVE).build();
        LivenessResult vetoed = LivenessResult.builder(SessionState.FAILED, Decision.ATTACK_SUSPECTED).activeUsed(true)
                .errorCode(LivenessErrorCode.PAD_FAILED).build();
        LivenessResult done = LivenessResult.builder(SessionState.PASSED, Decision.LIVE).activeUsed(true)
                .challenges(Collections.singletonList(new ChallengeOutcome(ChallengeType.SMILE,
                        ChallengeProgress.PASSED, 900)))
                .build();

        assertAll(() -> assertEquals("none drawn", new ResultCard("W", passive, null).lines().get("Challenges")),
                () -> assertEquals("drawn, none finished", new ResultCard("W", vetoed, null).lines().get("Challenges")),
                () -> assertEquals(done.challengeSummary(), new ResultCard("W", done, null).lines().get("Challenges")));
    }
}
