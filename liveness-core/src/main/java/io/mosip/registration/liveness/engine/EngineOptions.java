package io.mosip.registration.liveness.engine;

import java.util.Locale;

/**
 * How {@link LivenessEngines#hybrid} runs the models (API.md §3.7). Only the CPU execution provider is built in; any
 * other request (NNAPI, XNNPACK) falls back to CPU, and {@code EngineInfo} reports the provider actually used.
 * Acceleration changes speed only, so the fallback is not a fail-closed case. Immutable.
 */
public final class EngineOptions {

    private final String requestedProvider;

    private EngineOptions(String requestedProvider) {
        this.requestedProvider = requestedProvider;
    }

    /** @return the defaults: provider AUTO */
    public static EngineOptions defaults() {
        return new EngineOptions("AUTO");
    }

    /**
     * @param provider {@code accel} from the config: AUTO, CPU, NNAPI or XNNPACK
     * @return options asking for that provider
     */
    public static EngineOptions withProvider(String provider) {
        return new EngineOptions(provider == null ? "AUTO" : provider.trim().toUpperCase(Locale.ROOT));
    }

    /** @return the provider asked for */
    public String requestedProvider() {
        return requestedProvider;
    }

    /** @return the provider actually used: always CPU in this version */
    public String effectiveProvider() {
        return "CPU";
    }
}
