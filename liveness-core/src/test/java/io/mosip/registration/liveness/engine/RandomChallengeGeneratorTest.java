package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.SourceCapabilities;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RandomChallengeGeneratorTest {

    private static final LivenessPolicy DEFAULTS = LivenessConfigLoader.load(Collections.<String, String>emptyMap(),
            Workflow.RESIDENT_REGISTRATION);
    private static final SourceCapabilities FPS_20 = SourceCapabilities.builder().fps(20, 20).build();

    @Test
    @DisplayName("TC-ENG-06: with min 2 and max 3, every sequence has 2 or 3 challenges, then NEUTRAL_HOLD")
    void countWithinMinMaxThenNeutralHold() {
        RandomChallengeGenerator generator = new RandomChallengeGenerator(true);
        SecureRandom random = new SecureRandom();
        Set<Integer> counts = new HashSet<Integer>();

        for (int i = 0; i < 1000; i++) {
            List<Challenge> sequence = generator.generate(DEFAULTS, FPS_20, random);
            int challenges = sequence.size() - 1;
            counts.add(challenges);
            assertTrue(challenges >= 2 && challenges <= 3, "challenges " + challenges);
            assertEquals(ChallengeType.NEUTRAL_HOLD, sequence.get(sequence.size() - 1).type());
        }
        assertEquals(new HashSet<Integer>(Arrays.asList(2, 3)), counts, "both counts occur");
    }

    @Test
    @DisplayName("TC-ENG-04: over 10,000 sequences, no type twice in a row, only pool types, and no sequence favoured:"
            + " each stays within 6 standard deviations of its expected count, and all 42 two-challenge ones occur")
    void randomWithoutRepeats() {
        RandomChallengeGenerator generator = new RandomChallengeGenerator(true);
        SecureRandom random = new SecureRandom();
        Map<List<ChallengeType>, Integer> seen = new HashMap<List<ChallengeType>, Integer>();
        int runs = 10000;

        for (int i = 0; i < runs; i++) {
            List<ChallengeType> types = types(generator.generate(DEFAULTS, FPS_20, random));
            for (int k = 0; k < types.size() - 1; k++) {
                assertTrue(DEFAULTS.challengePool().contains(types.get(k)), "pool type " + types.get(k));
                assertTrue(k == 0 || types.get(k) != types.get(k - 1), "repeat in " + types);
            }
            Integer n = seen.get(types);
            seen.put(types, n == null ? 1 : n + 1);
        }
        // Pool of 7, no repeats: half the sessions have 2 challenges (7 x 6 = 42 sequences, p = 1/84 each), half have 3
        // (7 x 6 x 6 = 252 sequences, p = 1/504 each). A flat "2%" cap would fail by chance: 1/84 is already 1.19%.
        int twos = 0;
        for (Map.Entry<List<ChallengeType>, Integer> e : seen.entrySet()) {
            double p = e.getKey().size() == 3 ? 1.0 / 84 : 1.0 / 504;
            double expected = runs * p;
            double limit = expected + 6 * Math.sqrt(runs * p * (1 - p));
            assertTrue(e.getValue() <= limit, e.getKey() + " came " + e.getValue() + " times, expected " + expected);
            twos += e.getKey().size() == 3 ? 1 : 0;
        }
        assertEquals(42, twos, "every two-challenge sequence occurs");
    }

    @ParameterizedTest(name = "nominal {0} fps, measured {1} fps: BLINK {2}")
    @CsvSource({
        "30, 20, possible",
        "30,  5, never",
        "30,  0, possible",
        " 5,  0, never",
        " 0,  0, never"})
    @DisplayName("TC-ENG-05: BLINK needs blink.min.fps (15); an unknown measured rate (0) falls back to the nominal one,"
            + " and with neither BLINK is left out")
    void blinkFollowsTheFrameRate(double nominal, double measured, String blink) {
        RandomChallengeGenerator generator = new RandomChallengeGenerator(true);
        SourceCapabilities source = SourceCapabilities.builder().fps(nominal, measured).build();

        boolean seen = false;
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 1000; i++) {
            seen |= types(generator.generate(DEFAULTS, source, random)).contains(ChallengeType.BLINK);
        }

        assertEquals("possible".equals(blink), seen);
    }

    @Test
    @DisplayName("Without eye measurements (no dense landmarks) CLOSE_EYES and BLINK are never asked for")
    void noEyeChallengesWithoutEyeMeasurements() {
        RandomChallengeGenerator generator = new RandomChallengeGenerator(false);
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < 1000; i++) {
            List<ChallengeType> types = types(generator.generate(DEFAULTS, FPS_20, random));
            assertFalse(types.contains(ChallengeType.CLOSE_EYES) || types.contains(ChallengeType.BLINK), "" + types);
        }
    }

    @Test
    @DisplayName("Every challenge gets challenge.timeout.ms from the policy")
    void timeoutComesFromThePolicy() {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness.challenge.timeout.ms", "8000");
        LivenessPolicy policy = LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);

        for (Challenge c : new RandomChallengeGenerator(true).generate(policy, FPS_20, new SecureRandom())) {
            assertEquals(8000L, c.timeoutMs(), c.type().name());
        }
    }

    private static List<ChallengeType> types(List<Challenge> sequence) {
        List<ChallengeType> types = new ArrayList<ChallengeType>();
        for (Challenge c : sequence) {
            types.add(c.type());
        }
        return types;
    }
}
