package io.mosip.registration.liveness.workbench;

import io.mosip.registration.liveness.api.LivenessMode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import java.util.HashMap;
import java.util.Map;

/** Policies for the flow tests: liveness on, the scene check off (the stub keeps no frames), a given mode. */
final class WorkbenchConfigTestSupport {

    private WorkbenchConfigTestSupport() {
    }

    static LivenessPolicy policy(LivenessMode mode) {
        Map<String, String> config = new HashMap<String, String>();
        config.put(LivenessConfigLoader.PREFIX + "enabled", "true");
        config.put(LivenessConfigLoader.PREFIX + "bind.capture.enabled", "false");
        config.put(LivenessConfigLoader.PREFIX + "mode", mode.name());
        return LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);
    }
}
