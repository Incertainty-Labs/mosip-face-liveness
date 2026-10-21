package io.mosip.registration.liveness.evidence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.mosip.registration.liveness.api.ChallengeOutcome;
import io.mosip.registration.liveness.api.ChallengeProgress;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.Decision;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PacketEvidenceTest {

    private static final String DIGEST = "9f2c4b1e8d7a6c5b4a3f2e1d0c9b8a7f6e5d4c3b2a1f0e9d8c7b6a5f4e3d2c1b";

    @Test
    @DisplayName("A passed hybrid session gives every API.md §9 key in the documented format")
    void passedHybridSessionGivesEveryKey() {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness.enabled", "true");
        LivenessPolicy policy = LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);
        Map<String, String> models = new LinkedHashMap<String, String>();
        models.put("yunet", "2023mar");
        models.put("minifasnet_v2", "1.0");
        LivenessResult result = LivenessResult.builder(SessionState.PASSED, Decision.LIVE)
                .passiveScore(0.9134)
                .activeUsed(true)
                .challenges(Arrays.asList(
                        new ChallengeOutcome(ChallengeType.TURN_LEFT, ChallengeProgress.PASSED, 900L),
                        new ChallengeOutcome(ChallengeType.NEUTRAL_HOLD, ChallengeProgress.PASSED, 1000L)))
                .versions("liveness-core 0.1.3", models)
                .evidenceDigest(DIGEST)
                .build();

        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put("LIVENESS_DECISION", "LIVE");
        expected.put("LIVENESS_SCORE", "0.91");
        expected.put("LIVENESS_THRESHOLD", "0.80");
        expected.put("LIVENESS_MODE", "HYBRID");
        expected.put("LIVENESS_CHALLENGES", "TURN_LEFT:PASSED,NEUTRAL_HOLD:PASSED");
        expected.put("LIVENESS_ENGINE_VERSION", "liveness-core 0.1.3; yunet=2023mar; minifasnet_v2=1.0");
        expected.put("LIVENESS_EVIDENCE_DIGEST", DIGEST);
        assertEquals(expected, PacketEvidence.of(result, policy));
    }

    @Test
    @DisplayName("A session without an evidence digest (stub engine) leaves the digest key out instead of writing null")
    void noDigestLeavesKeyOut() {
        LivenessPolicy policy = LivenessConfigLoader.load(new HashMap<String, String>(), Workflow.OPERATOR_LOGIN);
        LivenessResult result = LivenessResult.builder(SessionState.PASSED, Decision.LIVE).build();

        Map<String, String> keys = PacketEvidence.of(result, policy);

        assertAll(
                () -> assertFalse(keys.containsKey("LIVENESS_EVIDENCE_DIGEST")),
                () -> assertFalse(keys.containsValue(null)),
                () -> assertEquals(6, keys.size()));
    }

    @Test
    @DisplayName("Decimals use a dot whatever the JVM's default locale")
    void decimalsIgnoreDefaultLocale() {
        Locale saved = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            Map<String, String> config = new HashMap<String, String>();
            config.put("mosip.registration.face.liveness.passive.threshold", "0.75");
            LivenessPolicy policy = LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);
            LivenessResult result = LivenessResult.builder(SessionState.FAILED, Decision.ATTACK_SUSPECTED)
                    .passiveScore(0.4)
                    .build();

            Map<String, String> keys = PacketEvidence.of(result, policy);

            assertAll(
                    () -> assertEquals("0.40", keys.get("LIVENESS_SCORE")),
                    () -> assertEquals("0.75", keys.get("LIVENESS_THRESHOLD")));
        } finally {
            Locale.setDefault(saved);
        }
    }
}
