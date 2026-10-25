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
 * <li>{@code threads}: the inference threads. With XNNPACK they are XNNPACK's own pool (0 for every core) and ONNX
 * Runtime's pool is one thread; otherwise they are ONNX Runtime's intra-op threads (0 for its own default).</li>
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

    /** @return the defaults: provider AUTO, the default thread count, the detector on every frame */
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
     * @param count the inference threads (see the class comment); 0 or less for the default
     * @return a copy with that thread count
     */
    public EngineOptions threads(int count) {
        return new EngineOptions(requestedProvider, Math.max(0, count), detectEvery);
    }

    /**
     * @param frames run the face detector on every this many frames; 1 or less for every frame, at most
     *               {@value #MAX_DETECT_EVERY}
     * @return a copy with that interval
     */
    public EngineOptions detectEvery(int frames) {
        return new EngineOptions(requestedProvider, threads, Math.min(MAX_DETECT_EVERY, Math.max(1, frames)));
    }

    /**
     * The longest detection interval. Between detections the guard sees the last detection's face count and the
     * passive veto gets no new scores, so the gap must stay short.
     */
    public static final int MAX_DETECT_EVERY = 5;

    /** @return the provider asked for */
    public String requestedProvider() {
        return requestedProvider;
    }

    /** @return the inference threads; 0 for the default */
    public int threads() {
        return threads;
    }

    /** @return the face detector runs on every this many frames */
    public int detectEvery() {
        return detectEvery;
    }
}
