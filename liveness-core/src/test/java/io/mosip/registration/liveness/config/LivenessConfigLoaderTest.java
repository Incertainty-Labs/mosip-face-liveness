package io.mosip.registration.liveness.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.LivenessMode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.MaxRetriesAction;
import io.mosip.registration.liveness.api.Workflow;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LivenessConfigLoaderTest {

    @Test
    @DisplayName("TC-ENG-01: an empty config gives the defaults documented in API.md §5")
    void tcEng01_emptyConfigGivesDocumentedDefaults() {
        LivenessPolicy policy = LivenessConfigLoader.load(Collections.<String, String>emptyMap(),
                Workflow.RESIDENT_REGISTRATION);

        assertAll(
                () -> assertEquals(Workflow.RESIDENT_REGISTRATION, policy.workflow()),
                () -> assertTrue(policy.enabled()),
                () -> assertEquals(LivenessMode.HYBRID, policy.mode()),
                () -> assertEquals(0.80, policy.passiveThreshold(), 1e-9),
                () -> assertEquals(8, policy.passiveMinFrames()),
                () -> assertTrue(policy.activeEnabled()),
                () -> assertEquals(Arrays.asList(ChallengeType.TURN_LEFT, ChallengeType.TURN_RIGHT,
                        ChallengeType.LOOK_UP, ChallengeType.SMILE, ChallengeType.OPEN_MOUTH,
                        ChallengeType.CLOSE_EYES, ChallengeType.BLINK), policy.challengePool()),
                () -> assertEquals(2, policy.challengeMinCount()),
                () -> assertEquals(3, policy.challengeMaxCount()),
                () -> assertEquals(5000L, policy.challengeTimeoutMs()),
                () -> assertEquals(30000L, policy.sessionTimeoutMs()),
                () -> assertEquals(15.0, policy.blinkMinFps(), 1e-9),
                () -> assertEquals(3, policy.maxRetries()),
                () -> assertEquals(MaxRetriesAction.BLOCK, policy.onMaxRetries()),
                () -> assertTrue(policy.bindCaptureEnabled()),
                () -> assertEquals(0.60, policy.bindMinSimilarity(), 1e-9),
                () -> assertEquals(Collections.emptyList(), policy.warnings()));
    }

    @Test
    @DisplayName("TC-ENG-02: a per-workflow key overrides only that workflow")
    void tcEng02_perWorkflowOverrideAppliesOnlyToThatWorkflow() {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness.policy.eod_auth.mode", "ACTIVE");

        assertAll(
                () -> assertEquals(LivenessMode.ACTIVE,
                        LivenessConfigLoader.load(config, Workflow.SUPERVISOR_EOD_AUTH).mode()),
                () -> assertEquals(LivenessMode.HYBRID,
                        LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION).mode()),
                () -> assertEquals(LivenessMode.HYBRID,
                        LivenessConfigLoader.load(config, Workflow.SUPERVISOR_EXCEPTION_AUTH).mode()));
    }

    @Test
    @DisplayName("TC-ENG-03: an unparsable value falls back to the default and is reported")
    void tcEng03_unparsableValueFallsBackToDefaultWithWarning() {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness.passive.threshold", "abc");

        LivenessPolicy policy = LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);

        assertAll(
                () -> assertEquals(0.80, policy.passiveThreshold(), 1e-9),
                () -> assertEquals(1, policy.warnings().size()),
                () -> assertTrue(policy.warnings().get(0)
                        .contains("mosip.registration.face.liveness.passive.threshold")));
    }

    @Test
    @DisplayName("A global key applies to every workflow, and a per-workflow key still wins over it")
    void globalKeyAppliesEverywhereAndPerWorkflowKeyWins() {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness.passive.threshold", "0.9");
        config.put("mosip.registration.face.liveness.policy.registration.passive.threshold", "0.7");

        assertAll(
                () -> assertEquals(0.9,
                        LivenessConfigLoader.load(config, Workflow.OPERATOR_LOGIN).passiveThreshold(), 1e-9),
                () -> assertEquals(0.7,
                        LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION).passiveThreshold(), 1e-9));
    }
}
