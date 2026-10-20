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
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    static Stream<Arguments> outOfRangeValues() {
        return Stream.of(
                Arguments.of("passive.threshold", "1.5", 0.80, (Function<LivenessPolicy, Number>) LivenessPolicy::passiveThreshold),
                Arguments.of("passive.threshold", "-0.1", 0.80, (Function<LivenessPolicy, Number>) LivenessPolicy::passiveThreshold),
                Arguments.of("passive.min.frames", "2", 8, (Function<LivenessPolicy, Number>) LivenessPolicy::passiveMinFrames),
                Arguments.of("passive.min.frames", "61", 8, (Function<LivenessPolicy, Number>) LivenessPolicy::passiveMinFrames),
                Arguments.of("challenge.min.count", "0", 2, (Function<LivenessPolicy, Number>) LivenessPolicy::challengeMinCount),
                Arguments.of("challenge.max.count", "6", 3, (Function<LivenessPolicy, Number>) LivenessPolicy::challengeMaxCount),
                Arguments.of("challenge.timeout.ms", "1999", 5000L, (Function<LivenessPolicy, Number>) LivenessPolicy::challengeTimeoutMs),
                Arguments.of("challenge.timeout.ms", "15001", 5000L, (Function<LivenessPolicy, Number>) LivenessPolicy::challengeTimeoutMs),
                Arguments.of("session.timeout.ms", "9999", 30000L, (Function<LivenessPolicy, Number>) LivenessPolicy::sessionTimeoutMs),
                Arguments.of("session.timeout.ms", "120001", 30000L, (Function<LivenessPolicy, Number>) LivenessPolicy::sessionTimeoutMs),
                Arguments.of("blink.min.fps", "0", 15.0, (Function<LivenessPolicy, Number>) LivenessPolicy::blinkMinFps),
                Arguments.of("max.retries", "0", 3, (Function<LivenessPolicy, Number>) LivenessPolicy::maxRetries),
                Arguments.of("max.retries", "11", 3, (Function<LivenessPolicy, Number>) LivenessPolicy::maxRetries),
                Arguments.of("bind.min.similarity", "1.01", 0.60, (Function<LivenessPolicy, Number>) LivenessPolicy::bindMinSimilarity));
    }

    @ParameterizedTest(name = "{0}={1} falls back to {2}")
    @MethodSource("outOfRangeValues")
    @DisplayName("An out-of-range value falls back to the default and is reported")
    void outOfRangeValueFallsBackToDefaultWithWarning(String setting, String value, Number expected,
            Function<LivenessPolicy, Number> getter) {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness." + setting, value);

        LivenessPolicy policy = LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);

        assertAll(
                () -> assertEquals(expected.doubleValue(), getter.apply(policy).doubleValue(), 1e-9),
                () -> assertEquals(1, policy.warnings().size()),
                () -> assertTrue(policy.warnings().get(0).contains("mosip.registration.face.liveness." + setting)));
    }

    @Test
    @DisplayName("A minimum challenge count above the maximum resets both counts to their defaults")
    void minAboveMaxResetsBothCounts() {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness.challenge.min.count", "4");
        config.put("mosip.registration.face.liveness.challenge.max.count", "3");

        LivenessPolicy policy = LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);

        assertAll(
                () -> assertEquals(2, policy.challengeMinCount()),
                () -> assertEquals(3, policy.challengeMaxCount()),
                () -> assertEquals(1, policy.warnings().size()),
                () -> assertTrue(policy.warnings().get(0).contains("challenge.min.count")));
    }

    @Test
    @DisplayName("TC-CFG-04: a configured challenge pool is used as given, per workflow")
    void tcCfg04_configuredPoolIsUsedPerWorkflow() {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness.challenge.pool", " smile , TURN_LEFT ");
        config.put("mosip.registration.face.liveness.policy.eod_auth.challenge.pool", "OPEN_MOUTH");

        assertAll(
                () -> assertEquals(Arrays.asList(ChallengeType.SMILE, ChallengeType.TURN_LEFT),
                        LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION).challengePool()),
                () -> assertEquals(Collections.singletonList(ChallengeType.OPEN_MOUTH),
                        LivenessConfigLoader.load(config, Workflow.SUPERVISOR_EOD_AUTH).challengePool()));
    }

    @ParameterizedTest(name = "pool \"{0}\" is rejected")
    @ValueSource(strings = {"SMILE,WINK", "NEUTRAL_HOLD", "SMILE,,BLINK"})
    @DisplayName("A pool with an unknown or non-selectable entry falls back to the default pool and is reported")
    void invalidPoolFallsBackToDefault(String pool) {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness.challenge.pool", pool);

        LivenessPolicy policy = LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);

        assertAll(
                () -> assertEquals(LivenessConfigLoader.DEFAULT_CHALLENGE_POOL, policy.challengePool()),
                () -> assertEquals(1, policy.warnings().size()),
                () -> assertTrue(policy.warnings().get(0).contains("challenge.pool")));
    }

    @ParameterizedTest(name = "{0}={1} is rejected")
    @CsvSource({"mode,FAST", "on.max.retries,IGNORE", "enabled,maybe", "active.enabled,1"})
    @DisplayName("An unknown enum or boolean value keeps the default and is reported")
    void unknownEnumOrBooleanKeepsDefault(String setting, String value) {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness." + setting, value);

        LivenessPolicy policy = LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);

        assertAll(
                () -> assertEquals(LivenessMode.HYBRID, policy.mode()),
                () -> assertEquals(MaxRetriesAction.BLOCK, policy.onMaxRetries()),
                () -> assertTrue(policy.enabled()),
                () -> assertTrue(policy.activeEnabled()),
                () -> assertEquals(1, policy.warnings().size()));
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
