package io.mosip.registration.liveness.engine.onnx;

import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessException;
import io.mosip.registration.liveness.engine.EngineOptions;
import io.mosip.registration.liveness.engine.LivenessEnginesTesting;
import io.mosip.registration.liveness.model.ModelRegistry;

/** Reaches the engine factory's provider step, to try a provider the platform may not have. */
final class LivenessEnginesAccess {

    private LivenessEnginesAccess() {
    }

    static LivenessEngine hybridTrying(ModelRegistry models, EngineOptions options, String provider)
            throws LivenessException {
        return LivenessEnginesTesting.hybridTrying(models, options, provider);
    }
}
