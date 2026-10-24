package io.mosip.registration.liveness.engine;

import ai.onnxruntime.OrtException;
import io.mosip.registration.liveness.api.EngineInfo;
import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessException;
import io.mosip.registration.liveness.api.PadDetector;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import io.mosip.registration.liveness.engine.onnx.MiniFasNetPadDetector;
import io.mosip.registration.liveness.engine.onnx.OnnxFaceAnalyzer;
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
        final OnnxFaceAnalyzer analyzer;
        final MiniFasNetPadDetector v2;
        final MiniFasNetPadDetector v1se;
        try {
            analyzer = OnnxFaceAnalyzer.load(models, LivenessConfigLoader.DEFAULT_DETECTOR_MIN_SCORE);
            v2 = MiniFasNetPadDetector.load(models, "minifasnet_v2", 2.7);
            v1se = MiniFasNetPadDetector.load(models, "minifasnet_v1se", 4.0);
        } catch (OrtException | RuntimeException e) {
            throw new LivenessException(LivenessErrorCode.ENGINE_ERROR, "Cannot load the models: " + e.getMessage());
        }
        ExecutorService workers = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "liveness-worker");
            thread.setDaemon(true);
            return thread;
        });
        return new HybridLivenessEngine(new EngineParts(
                analyzer::withMinScore,
                // The static-stream veto is stateful, so each session gets its own
                () -> Arrays.<PadDetector>asList(v2, v1se, new StaticStreamDetector()),
                new RandomChallengeGenerator(true),
                new SecureRandom(),
                workers,
                new EngineInfo(HYBRID_VERSION, models.versions(), options.effectiveProvider())));
    }

    /** The real engine's version, reported in every result and in the packet evidence. */
    static final String HYBRID_VERSION = "liveness-core 0.2.0";
}
