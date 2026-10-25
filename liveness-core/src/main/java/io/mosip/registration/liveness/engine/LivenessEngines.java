package io.mosip.registration.liveness.engine;

import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.mosip.registration.liveness.api.EngineInfo;
import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessException;
import io.mosip.registration.liveness.api.PadDetector;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import io.mosip.registration.liveness.engine.onnx.MiniFasNetPadDetector;
import io.mosip.registration.liveness.engine.onnx.OnnxFaceAnalyzer;
import io.mosip.registration.liveness.engine.onnx.Sessions;
import io.mosip.registration.liveness.model.ModelRegistry;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    /**
     * The real engine: YuNet, Face Mesh and Blendshape for the face, MiniFASNet x2 and the static-stream veto for
     * passive PAD, random challenges with baseline-relative verifiers. The models are loaded once from the verified
     * bundle and shared by all sessions; each session runs on its own worker turn of a daemon thread pool.
     *
     * @param models  the verified model bundle
     * @param options how to run the models
     * @return the engine
     * @throws LivenessException LIV-013 if a model cannot be loaded
     */
    public static LivenessEngine hybrid(ModelRegistry models, EngineOptions options) throws LivenessException {
        return hybrid(models, options, Sessions.effectiveProvider(options.requestedProvider()));
    }

    /**
     * Builds the engine on {@code provider}; if an accelerator fails to start, it is built on CPU instead, since
     * acceleration changes speed only and must never stop liveness.
     */
    static LivenessEngine hybrid(ModelRegistry models, EngineOptions options, String provider)
            throws LivenessException {
        try {
            return build(models, options, provider);
        } catch (LivenessException e) {
            if ("CPU".equals(provider)) {
                throw e;
            }
            return build(models, options, "CPU");
        }
    }

    private static LivenessEngine build(ModelRegistry models, EngineOptions options, String provider)
            throws LivenessException {
        final OnnxFaceAnalyzer analyzer;
        final MiniFasNetPadDetector v2;
        final MiniFasNetPadDetector v1se;
        try {
            OrtSession.SessionOptions session = Sessions.options(provider, options.threads());
            analyzer = OnnxFaceAnalyzer.load(models, LivenessConfigLoader.DEFAULT_DETECTOR_MIN_SCORE, session);
            v2 = MiniFasNetPadDetector.load(models, "minifasnet_v2", 2.7, session);
            v1se = MiniFasNetPadDetector.load(models, "minifasnet_v1se", 4.0, session);
        } catch (OrtException | RuntimeException e) {
            throw new LivenessException(LivenessErrorCode.ENGINE_ERROR, "Cannot load the models on " + provider + ": "
                    + e.getMessage());
        }
        ExecutorService workers = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "liveness-worker");
            thread.setDaemon(true);
            return thread;
        });
        return new HybridLivenessEngine(new EngineParts(
                // Each session gets its own view; with sparse detection it is a per-session tracker
                minScore -> options.detectEvery() > 1 ? analyzer.tracking(minScore, options.detectEvery())
                        : analyzer.withMinScore(minScore),
                // The static-stream veto is stateful, so each session gets its own
                () -> Arrays.<PadDetector>asList(v2, v1se, new StaticStreamDetector()),
                new RandomChallengeGenerator(true),
                new SecureRandom(),
                workers,
                new EngineInfo(HYBRID_VERSION, models.versions(), provider)));
    }

    /** The real engine's version, reported in every result and in the packet evidence. */
    static final String HYBRID_VERSION = "liveness-core 0.2.0";
}
