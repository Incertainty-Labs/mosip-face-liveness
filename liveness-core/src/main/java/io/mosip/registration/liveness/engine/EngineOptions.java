package io.mosip.registration.liveness.engine;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * How {@link LivenessEngines#hybrid} runs the models (API.md §3.7). Immutable; each {@code with}-style method returns a
 * copy.
 * <ul>
 * <li>{@code provider}: AUTO, CPU, XNNPACK or NNAPI ({@code accel}). XNNPACK and NNAPI are used only where ONNX
 * Runtime has them (Android); otherwise the engine runs on CPU and says so in {@code EngineInfo}. Acceleration changes
 * speed only, so falling back is not a fail-closed case. AUTO is CPU until phone measurements pick a better
 * default.</li>
 * <li>{@code threads}: ONNX Runtime's intra-op threads; 0 for its own default.</li>
 * <li>{@code detectEvery}: run the face detector on every Nth frame and follow the face with Face Mesh between; PAD
 * runs on the detection frames only. 1 (the default) detects on every frame.</li>
 * </ul>
 */
public final class EngineOptions {

    private static final List<String> PROVIDERS = Arrays.asList("AUTO", "CPU", "XNNPACK", "NNAPI");

    private final String requestedProvider;
    private final int threads;
    private final int detectEvery;

    private EngineOptions(String requestedProvider, int threads, int detectEvery) {
        this.requestedProvider = requestedProvider;
        this.threads = threads;
        this.detectEvery = detectEvery;
    }

    /** @return the defaults: provider AUTO, ONNX Runtime's own thread count, the detector on every frame */
    public static EngineOptions defaults() {
        return new EngineOptions("AUTO", 0, 1);
    }

    /**
     * @param provider {@code accel} from the config: AUTO, CPU, XNNPACK or NNAPI; anything else means AUTO
     * @return options asking for that provider
     */
    public static EngineOptions withProvider(String provider) {
        String name = provider == null ? "AUTO" : provider.trim().toUpperCase(Locale.ROOT);
        return new EngineOptions(PROVIDERS.contains(name) ? name : "AUTO", 0, 1);
    }

    /**
     * @param count ONNX Runtime intra-op threads; 0 or less for its default
     * @return a copy with that thread count
     */
    public EngineOptions threads(int count) {
        return new EngineOptions(requestedProvider, Math.max(0, count), detectEvery);
    }

    /**
     * @param frames run the face detector on every this many frames; 1 or less for every frame
     * @return a copy with that interval
     */
    public EngineOptions detectEvery(int frames) {
        return new EngineOptions(requestedProvider, threads, Math.max(1, frames));
    }

    /** @return the provider asked for */
    public String requestedProvider() {
        return requestedProvider;
    }

    /** @return ONNX Runtime intra-op threads; 0 for its default */
    public int threads() {
        return threads;
    }

    /** @return the face detector runs on every this many frames */
    public int detectEvery() {
        return detectEvery;
    }
}
