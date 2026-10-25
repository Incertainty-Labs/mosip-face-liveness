package io.mosip.registration.liveness.workbench;

import io.mosip.registration.liveness.api.LivenessMode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The Workbench's liveness configuration: the Registration Client's keys (API.md §5) from a properties file, resolved
 * per workflow by the same {@link LivenessConfigLoader} the clients use, so its warnings and ranges apply. Immutable.
 */
public final class WorkbenchConfig {

    private static final String DEMO = "workbench-demo.properties";

    private final Map<String, String> keys;
    private final String source;

    private WorkbenchConfig(Map<String, String> keys, String source) {
        this.keys = Collections.unmodifiableMap(keys);
        this.source = source;
    }

    /**
     * @return the bundled demo config: liveness on, every other key at its default
     * @throws IOException if the bundled file cannot be read
     */
    public static WorkbenchConfig demo() throws IOException {
        try (InputStream in = WorkbenchConfig.class.getResourceAsStream(DEMO)) {
            Properties p = new Properties();
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            return new WorkbenchConfig(toMap(p), "bundled " + DEMO);
        }
    }

    /**
     * @param file a properties file with full key names, for example
     *             {@code mosip.registration.face.liveness.enabled=true}
     * @return its config
     * @throws IOException if it cannot be read
     */
    public static WorkbenchConfig load(Path file) throws IOException {
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(in);
            return new WorkbenchConfig(toMap(p), file.toString());
        }
    }

    /**
     * @param workflow the workflow to check
     * @param mode     the Workbench's mode override for it; null for the configured mode
     * @return its policy, warnings included
     */
    public LivenessPolicy policy(Workflow workflow, LivenessMode mode) {
        Map<String, String> resolved = new HashMap<String, String>(keys);
        if (mode != null) {
            // The workflow's own key, so it wins over the global one and also reaches the exception photo
            resolved.put(LivenessConfigLoader.PREFIX + "policy." + workflow.policyCode() + ".mode", mode.name());
        }
        return LivenessConfigLoader.load(resolved, workflow);
    }

    /** @return where the config came from, for the audit pane */
    public String source() {
        return source;
    }

    private static Map<String, String> toMap(Properties p) {
        Map<String, String> map = new HashMap<String, String>();
        for (String name : p.stringPropertyNames()) {
            map.put(name, p.getProperty(name).trim());
        }
        return map;
    }
}
