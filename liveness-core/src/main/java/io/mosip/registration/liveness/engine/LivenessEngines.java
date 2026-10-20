package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.LivenessEngine;
import java.util.Map;

/** Factory for {@link LivenessEngine} implementations. */
public final class LivenessEngines {

    private LivenessEngines() {
    }

    /**
     * A scripted engine for UI work and integration before the real engine exists. It never looks at pixels: it
     * follows the scenario named by {@code mosip.registration.face.liveness.stub.force} (API.md §5) on the timeline
     * of the frames it receives. Callbacks run on the thread that calls {@code onFrame}.
     *
     * @param config the flat configuration map; only the {@code stub.force} key is read
     * @return the stub engine
     */
    public static LivenessEngine stub(Map<String, String> config) {
        return new StubLivenessEngine(StubScenario.from(config));
    }
}
