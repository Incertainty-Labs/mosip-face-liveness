package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.AnalysedFrame;
import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.ChallengeOutcome;
import io.mosip.registration.liveness.api.ChallengeProgress;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.ChallengeVerifier;
import io.mosip.registration.liveness.api.Decision;
import io.mosip.registration.liveness.api.FaceAnalyzer;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessCallback;
import io.mosip.registration.liveness.api.LivenessContext;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessMode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.LivenessSession;
import io.mosip.registration.liveness.api.PromptLevel;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.UserPrompt;
import io.mosip.registration.liveness.binding.CaptureFreshness;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One attempt of {@link HybridLivenessEngine} (TECHNICAL_DESIGN §6.2). {@link #onFrame} only drops the frame into a
 * one-frame slot (keep-latest) and returns; the analysis runs on the engine's executor, one frame at a time, and
 * calls the callback there.
 */
final class HybridSession implements LivenessSession {

    /** Frames kept for the capture binder (API.md §3.5). */
    static final int RECENT_FRAMES = 5;
    /** During the challenges, a passive median below passive.threshold minus this fails with LIV-008. */
    static final double VETO_MARGIN = 0.2;

    private final LivenessContext context;
    private final LivenessPolicy policy;
    private final LivenessCallback callback;
    private final FaceAnalyzer analyzer;
    private final PassiveAssessor assessor;
    private final ChallengeGuard guard = new ChallengeGuard();
    private final EngineParts parts;
    private final String nonce;

    private final AtomicReference<FaceFrame> waiting = new AtomicReference<FaceFrame>();
    private final AtomicBoolean draining = new AtomicBoolean();

    private final Deque<AnalysedFrame> recent = new ArrayDeque<AnalysedFrame>(RECENT_FRAMES);
    private final List<FaceObservation> passiveFrames = new ArrayList<FaceObservation>();
    private final List<ChallengeOutcome> outcomes = new ArrayList<ChallengeOutcome>();
    /** SHA-256 of each analysed frame, for the evidence digest. */
    private final List<byte[]> frameHashes = new ArrayList<byte[]>();
    private SessionState state = SessionState.WAITING_FOR_FACE;
    private boolean finished;
    private long firstMs = -1;
    private long lastMs;
    /** The guidance shown while waiting for a usable face, so it is sent only when it changes. */
    private String guidanceKey;
    private int frames;
    private double passiveScore = Double.NaN;
    private Baseline baseline;
    private boolean activeUsed;
    private List<Challenge> sequence;
    private int challengeIndex;
    private Challenge challenge;
    private ChallengeVerifier verifier;
    private long challengeStartMs;
    /** The last challenge is done on a frame the detector did not see; the pass waits for the next detection. */
    private boolean confirming;

    HybridSession(EngineParts parts, LivenessContext context, LivenessPolicy policy, LivenessCallback callback) {
        this.parts = parts;
        this.context = context;
        this.policy = policy;
        this.callback = callback;
        this.analyzer = parts.analyzer.apply(policy.detectorMinScore());
        this.assessor = new PassiveAssessor(parts.cues.get(), policy);
        this.nonce = CaptureFreshness.newNonce(parts.random);
        callback.onStateChanged(state, new UserPrompt("liveness.status.position", PromptLevel.INFO));
    }

    @Override
    public void onFrame(FaceFrame frame) {
        waiting.set(frame);
        if (draining.compareAndSet(false, true)) {
            parts.executor.execute(this::drain);
        }
    }

    /** Analyses waiting frames until none is left; only one drain runs at a time. */
    private void drain() {
        while (true) {
            FaceFrame frame = waiting.getAndSet(null);
            if (frame == null) {
                draining.set(false);
                // A frame may have arrived after the slot was found empty and before the flag was cleared
                if (waiting.get() == null || !draining.compareAndSet(false, true)) {
                    return;
                }
                continue;
            }
            process(frame);
        }
    }

    private synchronized void process(FaceFrame frame) {
        if (finished) {
            return;
        }
        long now = frame.timestampMs();
        if (firstMs < 0) {
            firstMs = now;
        }
        lastMs = now;
        if (now - firstMs >= policy.sessionTimeoutMs()) {
            // No capture follows a session that ran out of time, so its frames are dropped now (API.md §3.4)
            recent.clear();
            finish(SessionState.FAILED, Decision.INCONCLUSIVE, LivenessErrorCode.CHALLENGE_TIMEOUT, now);
            return;
        }
        frames++;
        FaceObservation observation;
        double fused;
        boolean detected;
        try {
            observation = analyzer.analyze(frame);
            // PAD runs only where the face detector ran: its models were calibrated on detector boxes
            detected = !(analyzer instanceof DetectingAnalyzer) || ((DetectingAnalyzer) analyzer).detected();
            remember(frame, observation);
            frameHashes.add(sha256().digest(frame.rgb()));
            fused = assessor.add(frame, observation, detected);
        } catch (RuntimeException e) {
            // Runtime problems arrive as a result, never as an exception on the frame thread (API.md §3.8)
            finish(SessionState.ERROR, Decision.INCONCLUSIVE, LivenessErrorCode.ENGINE_ERROR, now);
            return;
        }
        if (assessor.veto() != null) {
            // A hard veto (a frozen stream) ends the attempt in any state
            finish(SessionState.FAILED, Decision.ATTACK_SUSPECTED, StaticStreamDetector.NAME.equals(assessor.veto())
                    ? LivenessErrorCode.STATIC_STREAM : LivenessErrorCode.PAD_FAILED, now);
            return;
        }
        switch (state) {
            case WAITING_FOR_FACE:
            case PASSIVE_CHECK:
                passive(frame, observation, fused, detected, now);
                break;
            case ACTIVE_CHALLENGE:
            case NEUTRAL_HOLD:
                double score = assessor.score();
                if (!Double.isNaN(score) && score < policy.passiveThreshold() - VETO_MARGIN) {
                    // Passive scoring goes on during the challenges; a replay that does the gestures still fails
                    finish(SessionState.FAILED, Decision.ATTACK_SUSPECTED, LivenessErrorCode.PAD_FAILED, now);
                    return;
                }
                LivenessErrorCode broken = guard.check(observation, now);
                if (broken != null) {
                    finish(SessionState.FAILED, Decision.INCONCLUSIVE, broken, now);
                    return;
                }
                challengeStep(observation, detected, now);
                break;
            default:
                break;
        }
    }

    /**
     * Gathers the passive window from frames the quality gate passes and the detector ran on, then decides by mode.
     */
    private void passive(FaceFrame frame, FaceObservation observation, double fused, boolean detected, long now) {
        if (Double.isNaN(fused)) {
            // The quality gate turned the frame away: show its guidance; the frames gathered so far are kept
            QualityGate.Verdict verdict = QualityGate.check(observation, frame.width(), frame.height(), policy);
            if (verdict == null && !detected) {
                // A good frame between detections: nothing to score, wait for the next detection
                return;
            }
            String key = verdict == null ? "liveness.guidance.face_not_found" : verdict.messageKey();
            if (state != SessionState.WAITING_FOR_FACE || !key.equals(guidanceKey)) {
                guidanceKey = key;
                enter(SessionState.WAITING_FOR_FACE, new UserPrompt(key, PromptLevel.GUIDANCE));
            }
            return;
        }
        guidanceKey = null;
        if (state == SessionState.WAITING_FOR_FACE) {
            enter(SessionState.PASSIVE_CHECK, new UserPrompt("liveness.status.checking", PromptLevel.INFO));
        }
        passiveFrames.add(observation);
        callback.onProgress(Math.min(1f, (float) passiveFrames.size() / policy.passiveMinFrames()));
        double score = assessor.score();
        if (Double.isNaN(score)) {
            return;
        }
        passiveScore = score;
        baseline = Baseline.of(passiveFrames);
        boolean passed = score >= policy.passiveThreshold();
        boolean mayEscalate = policy.mode() == LivenessMode.HYBRID && policy.activeEnabled();
        if (!passed && policy.mode() != LivenessMode.ACTIVE && !mayEscalate) {
            // Terminal: PASSIVE mode, or HYBRID with active challenges switched off (API.md §6)
            finish(SessionState.FAILED, Decision.INCONCLUSIVE, LivenessErrorCode.BELOW_THRESHOLD, now);
            return;
        }
        if (policy.mode() == LivenessMode.ACTIVE || (!passed && policy.mode() == LivenessMode.HYBRID)) {
            activeUsed = true;
            sequence = parts.generator.generate(policy, context.source(), parts.random);
        } else {
            sequence = Collections.singletonList(
                    new Challenge(ChallengeType.NEUTRAL_HOLD, policy.challengeTimeoutMs()));
        }
        startChallenge(0, now);
    }

    private void startChallenge(int index, long now) {
        challengeIndex = index;
        challenge = sequence.get(index);
        verifier = ChallengeVerifiers.create(challenge.type(), baseline);
        challengeStartMs = now;
        SessionState next = challenge.type() == ChallengeType.NEUTRAL_HOLD ? SessionState.NEUTRAL_HOLD
                : SessionState.ACTIVE_CHALLENGE;
        enter(next, new UserPrompt(challenge.promptKey(), PromptLevel.INFO));
        callback.onChallenge(challenge, index, sequence.size());
    }

    private void challengeStep(FaceObservation observation, boolean detected, long now) {
        if (confirming) {
            // Between detections the face count is the last detection's, so the guard has just checked a fresh one
            if (detected) {
                finish(SessionState.PASSED, Decision.LIVE, null, now);
            }
            return;
        }
        // The step's countdown: how much of the challenge's time is used
        callback.onProgress(Math.min(1f, (float) (now - challengeStartMs) / challenge.timeoutMs()));
        ChallengeProgress progress = verifier.update(challenge, observation, now);
        if (progress == ChallengeProgress.TIMED_OUT) {
            outcomes.add(new ChallengeOutcome(challenge.type(), progress, now - challengeStartMs));
            finish(SessionState.FAILED, Decision.INCONCLUSIVE, LivenessErrorCode.CHALLENGE_TIMEOUT, now);
        } else if (progress == ChallengeProgress.PASSED) {
            outcomes.add(new ChallengeOutcome(challenge.type(), progress, now - challengeStartMs));
            if (challengeIndex + 1 < sequence.size()) {
                startChallenge(challengeIndex + 1, now);
            } else if (detected) {
                finish(SessionState.PASSED, Decision.LIVE, null, now);
            } else {
                // A pass is decided only where the detector ran, so a second face cannot slip in between detections
                confirming = true;
            }
        }
    }

    private void enter(SessionState next, UserPrompt prompt) {
        state = next;
        callback.onStateChanged(next, prompt);
    }

    private void finish(SessionState finalState, Decision decision, LivenessErrorCode error, long now) {
        finished = true;
        state = finalState;
        if (finalState == SessionState.CANCELLED || finalState == SessionState.ERROR) {
            // No capture follows, so the frames are not kept for a binder (API.md §3.4)
            recent.clear();
        }
        long duration = firstMs < 0 ? 0 : now - firstMs;
        LivenessResult result = LivenessResult.builder(finalState, decision)
                .passiveScore(Double.isNaN(passiveScore) ? 0.0 : passiveScore)
                .activeUsed(activeUsed)
                .challenges(outcomes)
                .frames(frames, duration > 0 ? frames * 1000.0 / duration : 0.0)
                .durationMs(duration)
                .errorCode(error)
                .diagnostics(diagnostics())
                .evidenceDigest(finalState == SessionState.PASSED || finalState == SessionState.FAILED ? digest()
                        : null)
                .nonce(nonce)
                .versions(parts.info.engineVersion(), parts.info.modelVersions())
                .build();
        callback.onResult(result);
    }

    /**
     * For audit and the diagnostics panel, never for the user: {@code pad.<cue>} is each PAD model's median over the
     * passive window, to 3 decimals, and {@code pad.veto} the cue that vetoed, if one did.
     */
    private Map<String, String> diagnostics() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Double> cue : assessor.cues().entrySet()) {
            out.put("pad." + cue.getKey(), String.format(Locale.ROOT, "%.3f", cue.getValue()));
        }
        if (assessor.veto() != null) {
            out.put("pad.veto", assessor.veto());
        }
        return out;
    }

    /**
     * The evidence digest (API.md §3.6, TC-ENG-17): SHA-256 over the nonce, the challenge transcript
     * ({@code TYPE:OUTCOME:durationMs} joined by {@code ;}) and the SHA-256 of each analysed frame's pixels, in order.
     * The capture binder appends the signed image's hash later.
     */
    private String digest() {
        StringBuilder transcript = new StringBuilder();
        for (ChallengeOutcome o : outcomes) {
            if (transcript.length() > 0) {
                transcript.append(';');
            }
            transcript.append(o.type().name()).append(':').append(o.outcome().name()).append(':').append(o.durationMs());
        }
        MessageDigest digest = sha256();
        digest.update(nonce.getBytes(StandardCharsets.UTF_8));
        digest.update(transcript.toString().getBytes(StandardCharsets.UTF_8));
        for (byte[] hash : frameHashes) {
            digest.update(hash);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }

    private void remember(FaceFrame frame, FaceObservation observation) {
        if (recent.size() == RECENT_FRAMES) {
            recent.removeFirst();
        }
        recent.addLast(new AnalysedFrame(frame, observation));
    }

    @Override
    public synchronized void cancel() {
        if (!finished) {
            finish(SessionState.CANCELLED, Decision.INCONCLUSIVE, null, firstMs < 0 ? 0 : lastMs);
        }
    }

    @Override
    public synchronized SessionState state() {
        return state;
    }

    @Override
    public synchronized List<AnalysedFrame> recentFrames() {
        return Collections.unmodifiableList(new ArrayList<AnalysedFrame>(recent));
    }

    @Override
    public synchronized void clear() {
        recent.clear();
    }
}
