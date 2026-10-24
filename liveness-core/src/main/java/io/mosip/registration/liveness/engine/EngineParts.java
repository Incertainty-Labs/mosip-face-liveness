package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.ChallengeGenerator;
import io.mosip.registration.liveness.api.EngineInfo;
import io.mosip.registration.liveness.api.FaceAnalyzer;
import io.mosip.registration.liveness.api.PadDetector;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/** What {@link HybridLivenessEngine} is built from; real models in production, scripted parts in tests. */
final class EngineParts {

    /** The face analyzer for a session, given {@code detector.min.score}. */
    final DoubleFunction<FaceAnalyzer> analyzer;
    /** A session's PAD cues; stateful ones (the static-stream veto) must be new for each call. */
    final Supplier<List<PadDetector>> cues;
    final ChallengeGenerator generator;
    final SecureRandom random;
    /** Runs each session's analysis; a session never runs two frames at once. */
    final Executor executor;
    final EngineInfo info;

    EngineParts(DoubleFunction<FaceAnalyzer> analyzer, Supplier<List<PadDetector>> cues, ChallengeGenerator generator,
            SecureRandom random, Executor executor, EngineInfo info) {
        this.analyzer = analyzer;
        this.cues = cues;
        this.generator = generator;
        this.random = random;
        this.executor = executor;
        this.info = info;
    }
}
