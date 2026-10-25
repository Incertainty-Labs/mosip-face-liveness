package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessException;
import io.mosip.registration.liveness.model.ModelRegistry;

/** Test access to the engine factory with a provider forced past the availability check. */
public final class LivenessEnginesTesting {

    private LivenessEnginesTesting() {
    }

    /**
     * @param models   the bundle
     * @param options  the options
     * @param provider the provider to try, whether or not ONNX Runtime lists it
     * @return the engine
     * @throws LivenessException LIV-013 if the models cannot load even on CPU
     */
    public static LivenessEngine hybridTrying(ModelRegistry models, EngineOptions options, String provider)
            throws LivenessException {
        return LivenessEngines.hybrid(models, options, provider);
    }
}
