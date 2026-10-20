package io.mosip.registration.liveness.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Engine identity for audit and packet evidence. Immutable. */
public final class EngineInfo {

    private final String engineVersion;
    private final Map<String, String> modelVersions;
    private final String executionProvider;

    /**
     * @param engineVersion     engine name and version
     * @param modelVersions     model name to version; copied
     * @param executionProvider the ONNX Runtime execution provider actually in use, for example {@code CPU}
     */
    public EngineInfo(String engineVersion, Map<String, String> modelVersions, String executionProvider) {
        this.engineVersion = engineVersion;
        this.modelVersions = Collections.unmodifiableMap(new LinkedHashMap<String, String>(modelVersions));
        this.executionProvider = executionProvider;
    }

    /** @return engine name and version */
    public String engineVersion() {
        return engineVersion;
    }

    /** @return model name to version; unmodifiable */
    public Map<String, String> modelVersions() {
        return modelVersions;
    }

    /** @return the execution provider in use */
    public String executionProvider() {
        return executionProvider;
    }
}
