package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.config.LivenessConfigLoader;
import java.util.Locale;
import java.util.Map;

/** The scripted outcomes the stub engine can play, selected by {@code stub.force}. */
enum StubScenario {
    /** Passive check, random challenges, neutral hold, LIVE. The default. */
    PASS_ACTIVE(true),
    /** Passive check passes; neutral hold; LIVE without challenges. */
    PASS_PASSIVE(false),
    /** No face ever appears: face-not-found guidance until the session timeout, then LIV-004. */
    NO_FACE(false),
    /** The first challenge is done wrong: LIV-009. */
    FAIL_CHALLENGE(true),
    /** The first challenge is not done in time: LIV-010 after the challenge timeout. */
    TIMEOUT(true),
    /** The passive check suspects an attack: LIV-008. */
    PAD(false),
    /** The engine fails on the first frame: LIV-013. */
    ENGINE_ERROR(false);

    private final boolean escalates;

    StubScenario(boolean escalates) {
        this.escalates = escalates;
    }

    /** @return whether the scripted passive score is low enough to start challenges */
    boolean escalates() {
        return escalates;
    }

    /** Config key that selects the scenario. */
    static final String KEY = LivenessConfigLoader.PREFIX + "stub.force";

    /** @return the scenario named in the config, or {@link #PASS_ACTIVE} when missing or unknown */
    static StubScenario from(Map<String, String> config) {
        String value = config.get(KEY);
        if (value == null) {
            return PASS_ACTIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PASS_ACTIVE;
        }
    }
}
