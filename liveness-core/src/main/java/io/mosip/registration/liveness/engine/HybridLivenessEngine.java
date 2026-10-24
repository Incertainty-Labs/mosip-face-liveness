package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.EngineInfo;
import io.mosip.registration.liveness.api.LivenessCallback;
import io.mosip.registration.liveness.api.LivenessContext;
import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessSession;

/**
 * The real engine (TECHNICAL_DESIGN §6): passive PAD first, then random challenges when the policy asks for them,
 * then a neutral hold. One per application; thread-safe. Build it with {@code LivenessEngines.hybrid}.
 */
public final class HybridLivenessEngine implements LivenessEngine {

    private final EngineParts parts;

    HybridLivenessEngine(EngineParts parts) {
        this.parts = parts;
    }

    @Override
    public LivenessSession startSession(LivenessContext context, LivenessPolicy policy, LivenessCallback callback) {
        return new HybridSession(parts, context, policy, callback);
    }

    @Override
    public EngineInfo info() {
        return parts.info;
    }
}
