package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.EngineInfo;
import io.mosip.registration.liveness.api.LivenessCallback;
import io.mosip.registration.liveness.api.LivenessContext;
import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessSession;
import java.security.SecureRandom;
import java.util.Collections;

/** Scripted engine; see {@link LivenessEngines#stub(java.util.Map)}. */
final class StubLivenessEngine implements LivenessEngine {

    /** Engine version reported in results and audit. */
    static final String VERSION = "liveness-core stub";

    private final StubScenario scenario;
    private final SecureRandom random = new SecureRandom();

    StubLivenessEngine(StubScenario scenario) {
        this.scenario = scenario;
    }

    @Override
    public LivenessSession startSession(LivenessContext context, LivenessPolicy policy, LivenessCallback callback) {
        return new StubSession(scenario, policy, callback, random);
    }

    @Override
    public EngineInfo info() {
        return new EngineInfo(VERSION, Collections.<String, String>emptyMap(), "NONE");
    }
}
