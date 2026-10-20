package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.Decision;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.LivenessContext;
import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.PromptLevel;
import io.mosip.registration.liveness.api.UserPrompt;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.LivenessSession;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StubLivenessEngineTest {

    private static final long FRAME_INTERVAL_MS = 100L;
    private static final long MAX_FEED_MS = 120_000L;

    @Test
    @DisplayName("PASS_ACTIVE (default): passive check, two challenges from the pool, neutral hold, then LIVE")
    void passActiveRunsTwoChallengesThenPasses() {
        RecordingCallback callback = new RecordingCallback();
        LivenessPolicy policy = policy(Collections.<String, String>emptyMap());

        LivenessResult result = run(engine(Collections.<String, String>emptyMap()), policy, callback);

        assertAll(
                () -> assertEquals(Arrays.asList(SessionState.WAITING_FOR_FACE, SessionState.PASSIVE_CHECK,
                        SessionState.ACTIVE_CHALLENGE, SessionState.NEUTRAL_HOLD, SessionState.PASSED),
                        callback.distinctStates()),
                () -> assertEquals(Arrays.asList("1/2", "2/2"), callback.challengeSteps()),
                () -> assertTrue(policy.challengePool().containsAll(callback.challengeTypes())),
                () -> assertEquals(SessionState.PASSED, result.finalState()),
                () -> assertEquals(Decision.LIVE, result.decision()),
                () -> assertNull(result.errorCode()),
                () -> assertTrue(result.activeUsed()),
                () -> assertEquals(3, result.challenges().size()),
                () -> assertEquals(ChallengeType.NEUTRAL_HOLD, result.challenges().get(2).type()),
                () -> assertEquals(5000L, result.durationMs()),
                () -> assertEquals(1, callback.resultCount()));
    }

    @Test
    @DisplayName("PASS_PASSIVE: passive check and neutral hold only, then LIVE without challenges")
    void passPassiveSkipsChallenges() {
        RecordingCallback callback = new RecordingCallback();
        LivenessPolicy policy = policy(Collections.<String, String>emptyMap());

        LivenessResult result = run(engine(force("PASS_PASSIVE")), policy, callback);

        assertAll(
                () -> assertEquals(Arrays.asList(SessionState.WAITING_FOR_FACE, SessionState.PASSIVE_CHECK,
                        SessionState.NEUTRAL_HOLD, SessionState.PASSED), callback.distinctStates()),
                () -> assertTrue(callback.challengeSteps().isEmpty()),
                () -> assertEquals(Decision.LIVE, result.decision()),
                () -> assertTrue(!result.activeUsed()),
                () -> assertTrue(result.passiveScore() >= policy.passiveThreshold()),
                () -> assertEquals("NEUTRAL_HOLD:PASSED", result.challengeSummary()),
                () -> assertEquals(2000L, result.durationMs()));
    }

    @ParameterizedTest(name = "{0} ends {1} {2} {3} after {4} ms")
    @DisplayName("Failure scenarios end with the state, decision and error of API.md §3.6, prompting the error's message")
    @CsvSource({
            // scenario,      final state, decision,         error code,        duration, last challenge outcome
            "PAD,            FAILED,      ATTACK_SUSPECTED, PAD_FAILED,        1000,     ''",
            "FAIL_CHALLENGE, FAILED,      INCONCLUSIVE,     CHALLENGE_FAILED,  2500,     FAILED",
            "TIMEOUT,        FAILED,      INCONCLUSIVE,     CHALLENGE_TIMEOUT, 6000,     TIMED_OUT",
            "ENGINE_ERROR,   ERROR,       INCONCLUSIVE,     ENGINE_ERROR,      0,        ''",
            "NO_FACE,        FAILED,      INCONCLUSIVE,     FACE_NOT_DETECTED, 30000,    ''",
    })
    void failureScenariosEndAsSpecified(String scenario, SessionState finalState, Decision decision,
            LivenessErrorCode errorCode, long durationMs, String lastOutcome) {
        RecordingCallback callback = new RecordingCallback();

        LivenessResult result = run(engine(force(scenario)), policy(Collections.<String, String>emptyMap()), callback);

        UserPrompt last = callback.prompts().get(callback.prompts().size() - 1);
        assertAll(
                () -> assertEquals(finalState, result.finalState()),
                () -> assertEquals(decision, result.decision()),
                () -> assertEquals(errorCode, result.errorCode()),
                () -> assertEquals(durationMs, result.durationMs()),
                () -> assertEquals(errorCode.messageKey(), last.key()),
                () -> assertEquals(PromptLevel.ERROR, last.level()),
                () -> assertTrue(lastOutcome.isEmpty() ? result.challenges().isEmpty()
                        : result.challengeSummary().endsWith(":" + lastOutcome), result.challengeSummary()),
                () -> assertEquals(1, callback.resultCount()));
    }

    @Test
    @DisplayName("NO_FACE keeps asking the user to position their face and never starts the passive check")
    void noFaceShowsGuidanceUntilTimeout() {
        RecordingCallback callback = new RecordingCallback();

        run(engine(force("NO_FACE")), policy(Collections.<String, String>emptyMap()), callback);

        assertAll(
                () -> assertEquals(Arrays.asList(SessionState.WAITING_FOR_FACE, SessionState.FAILED),
                        callback.distinctStates()),
                () -> assertTrue(callback.prompts().stream().anyMatch(
                        p -> p.key().equals("liveness.guidance.face_not_found") && p.level() == PromptLevel.GUIDANCE)));
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private static LivenessEngine engine(Map<String, String> config) {
        return LivenessEngines.stub(config);
    }

    private static LivenessPolicy policy(Map<String, String> config) {
        return LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);
    }

    private static LivenessContext context() {
        return LivenessContext.builder().workflow(Workflow.RESIDENT_REGISTRATION).refId("10001100010001")
                .attempt(1).build();
    }

    /** Feeds synthetic frames every 100 ms until the session reports a result, and returns that result. */
    private static LivenessResult run(LivenessEngine engine, LivenessPolicy policy, RecordingCallback callback) {
        LivenessSession session = engine.startSession(context(), policy, callback);
        for (long t = 0, seq = 0; t <= MAX_FEED_MS && callback.result() == null; t += FRAME_INTERVAL_MS, seq++) {
            session.onFrame(new FaceFrame(2, 2, new byte[12], t, seq, "test:frames"));
        }
        assertNotNull(callback.result(), "session never produced a result");
        return callback.result();
    }

    static Map<String, String> force(String scenario) {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness.stub.force", scenario);
        return config;
    }
}
