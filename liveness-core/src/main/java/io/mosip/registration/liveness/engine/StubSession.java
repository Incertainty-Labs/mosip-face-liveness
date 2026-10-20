package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.AnalysedFrame;
import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.ChallengeOutcome;
import io.mosip.registration.liveness.api.ChallengeProgress;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.Decision;
import io.mosip.registration.liveness.api.ErrorCategory;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessCallback;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.LivenessSession;
import io.mosip.registration.liveness.api.PromptLevel;
import io.mosip.registration.liveness.api.Rect;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.UserPrompt;
import io.mosip.registration.liveness.binding.CaptureFreshness;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * One scripted session. The timeline is the frames' own timestamps, measured from the first frame, so the stub
 * behaves the same at any frame rate and needs no timer thread.
 */
final class StubSession implements LivenessSession {

    /** Length of the scripted passive check. */
    static final long PASSIVE_MS = 1000L;
    /** Time the stub takes to "pass" or "fail" each challenge. */
    static final long CHALLENGE_MS = 1500L;
    /** Length of the final neutral hold. */
    static final long NEUTRAL_HOLD_MS = 1000L;
    /** Scripted passive score when the scenario escalates to challenges (below the default threshold). */
    static final double LOW_PASSIVE_SCORE = 0.60;
    /** Scripted passive score when the passive check passes on its own. */
    static final double HIGH_PASSIVE_SCORE = 0.95;
    /** Scripted passive score for a presentation attack. */
    static final double ATTACK_PASSIVE_SCORE = 0.20;
    /** Frames kept for the capture binder (API.md §3.5). */
    static final int RECENT_FRAMES = 5;

    private final StubScenario scenario;
    private final LivenessCallback callback;
    private final List<Step> schedule = new ArrayList<Step>();
    private final List<ChallengeOutcome> outcomes = new ArrayList<ChallengeOutcome>();
    private final Deque<AnalysedFrame> recent = new ArrayDeque<AnalysedFrame>(RECENT_FRAMES);
    private final int challengeCount;
    private final double passiveScore;
    private final String nonce;

    private SessionState state = SessionState.WAITING_FOR_FACE;
    private long firstFrameMs = -1L;
    private long lastFrameMs = -1L;
    private int frames;
    private int stepIndex = -1;
    private long stepStartMs;
    private boolean finished;

    StubSession(StubScenario scenario, LivenessPolicy policy, LivenessCallback callback, SecureRandom random) {
        this.scenario = scenario;
        this.nonce = CaptureFreshness.newNonce(random);
        this.callback = callback;
        this.challengeCount = scenario.escalates() ? policy.challengeMinCount() : 0;
        this.passiveScore = scenario == StubScenario.PAD ? ATTACK_PASSIVE_SCORE
                : scenario.escalates() ? LOW_PASSIVE_SCORE : HIGH_PASSIVE_SCORE;
        buildSchedule(policy, random);
        callback.onStateChanged(state, new UserPrompt("liveness.status.position", PromptLevel.INFO));
    }

    @Override
    public synchronized void onFrame(FaceFrame frame) {
        if (finished) {
            return;
        }
        frames++;
        remember(frame);
        long now = frame.timestampMs();
        lastFrameMs = now;
        if (firstFrameMs < 0) {
            firstFrameMs = now;
            if (scenario == StubScenario.ENGINE_ERROR) {
                fail(LivenessErrorCode.ENGINE_ERROR, now);
                return;
            }
            enter(0, now);
        }
        while (!finished && now - stepStartMs >= current().durationMs) {
            complete();
        }
        if (!finished) {
            callback.onProgress((float) (now - stepStartMs) / current().durationMs);
        }
    }

    @Override
    public synchronized void cancel() {
        if (finished) {
            return;
        }
        finished = true;
        state = SessionState.CANCELLED;
        callback.onResult(result(SessionState.CANCELLED, Decision.INCONCLUSIVE, null,
                firstFrameMs < 0 ? 0L : lastFrameMs));
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

    /** Keeps the frame with a stub observation (one face filling the frame); the stub analyses no pixels. */
    private void remember(FaceFrame frame) {
        if (recent.size() == RECENT_FRAMES) {
            recent.removeFirst();
        }
        FaceObservation observation = FaceObservation.builder()
                .faces(1, new Rect(0, 0, frame.width(), frame.height())).build();
        recent.addLast(new AnalysedFrame(frame, observation));
    }

    private void buildSchedule(LivenessPolicy policy, SecureRandom random) {
        if (scenario == StubScenario.NO_FACE) {
            schedule.add(new Step(SessionState.WAITING_FOR_FACE,
                    new UserPrompt("liveness.guidance.face_not_found", PromptLevel.GUIDANCE), null,
                    policy.sessionTimeoutMs(), LivenessErrorCode.FACE_NOT_DETECTED, null));
            return;
        }
        schedule.add(new Step(SessionState.PASSIVE_CHECK, new UserPrompt("liveness.status.checking", PromptLevel.INFO),
                null, PASSIVE_MS, scenario == StubScenario.PAD ? LivenessErrorCode.PAD_FAILED : null, null));
        ChallengeType previous = null;
        for (int i = 0; i < challengeCount; i++) {
            ChallengeType type = pick(policy.challengePool(), previous, random);
            Challenge challenge = new Challenge(type, policy.challengeTimeoutMs());
            if (i == 0 && scenario == StubScenario.FAIL_CHALLENGE) {
                schedule.add(Step.challenge(challenge, CHALLENGE_MS, LivenessErrorCode.CHALLENGE_FAILED,
                        ChallengeProgress.FAILED));
            } else if (i == 0 && scenario == StubScenario.TIMEOUT) {
                schedule.add(Step.challenge(challenge, challenge.timeoutMs(), LivenessErrorCode.CHALLENGE_TIMEOUT,
                        ChallengeProgress.TIMED_OUT));
            } else {
                schedule.add(Step.challenge(challenge, CHALLENGE_MS, null, ChallengeProgress.PASSED));
            }
            previous = type;
        }
        schedule.add(new Step(SessionState.NEUTRAL_HOLD, new UserPrompt("liveness.prompt.neutral", PromptLevel.INFO),
                new Challenge(ChallengeType.NEUTRAL_HOLD, NEUTRAL_HOLD_MS), NEUTRAL_HOLD_MS, null,
                ChallengeProgress.PASSED));
    }

    private Step current() {
        return schedule.get(stepIndex);
    }

    private void enter(int index, long now) {
        stepIndex = index;
        stepStartMs = now;
        Step step = current();
        state = step.state;
        if (step.state == SessionState.ACTIVE_CHALLENGE) {
            callback.onStateChanged(state, new UserPrompt(step.challenge.promptKey(), PromptLevel.INFO));
            callback.onChallenge(step.challenge, index, challengeCount);
        } else {
            callback.onStateChanged(state, step.prompt);
        }
    }

    private void complete() {
        Step step = current();
        long stepEnd = stepStartMs + step.durationMs;
        if (step.challenge != null) {
            outcomes.add(new ChallengeOutcome(step.challenge.type(), step.outcome, step.durationMs));
        }
        if (step.failure != null) {
            fail(step.failure, stepEnd);
        } else if (stepIndex + 1 < schedule.size()) {
            enter(stepIndex + 1, stepEnd);
        } else {
            pass(stepEnd);
        }
    }

    private void pass(long endMs) {
        finished = true;
        state = SessionState.PASSED;
        callback.onStateChanged(state, new UserPrompt("liveness.feedback.success", PromptLevel.SUCCESS));
        callback.onResult(result(SessionState.PASSED, Decision.LIVE, null, endMs));
    }

    private void fail(LivenessErrorCode error, long endMs) {
        finished = true;
        boolean systemFault = error.category() == ErrorCategory.DEVICE || error.category() == ErrorCategory.SYSTEM;
        state = systemFault ? SessionState.ERROR : SessionState.FAILED;
        Decision decision = error.category() == ErrorCategory.ATTACK ? Decision.ATTACK_SUSPECTED
                : Decision.INCONCLUSIVE;
        callback.onStateChanged(state, new UserPrompt(error.messageKey(), PromptLevel.ERROR));
        callback.onResult(result(state, decision, error, endMs));
    }

    private LivenessResult result(SessionState finalState, Decision decision, LivenessErrorCode error, long endMs) {
        long duration = firstFrameMs < 0 ? 0L : endMs - firstFrameMs;
        return LivenessResult.builder(finalState, decision)
                .passiveScore(passiveScore)
                .activeUsed(challengeCount > 0)
                .challenges(outcomes)
                .frames(frames, duration > 0 ? frames * 1000.0 / duration : 0.0)
                .durationMs(duration)
                .errorCode(error)
                .nonce(nonce)
                .versions(StubLivenessEngine.VERSION, Collections.<String, String>emptyMap())
                .build();
    }

    /** Picks a random challenge from the pool, never the same type twice in a row. */
    private static ChallengeType pick(List<ChallengeType> pool, ChallengeType previous, SecureRandom random) {
        List<ChallengeType> candidates = new ArrayList<ChallengeType>(pool);
        if (candidates.size() > 1) {
            candidates.remove(previous);
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /** One scripted step of the timeline, optionally ending in a scripted failure. */
    private static final class Step {
        final SessionState state;
        final UserPrompt prompt;
        final Challenge challenge;
        final long durationMs;
        final LivenessErrorCode failure;
        final ChallengeProgress outcome;

        Step(SessionState state, UserPrompt prompt, Challenge challenge, long durationMs,
                LivenessErrorCode failure, ChallengeProgress outcome) {
            this.state = state;
            this.prompt = prompt;
            this.challenge = challenge;
            this.durationMs = durationMs;
            this.failure = failure;
            this.outcome = outcome;
        }

        static Step challenge(Challenge challenge, long durationMs, LivenessErrorCode failure,
                ChallengeProgress outcome) {
            return new Step(SessionState.ACTIVE_CHALLENGE, null, challenge, durationMs, failure, outcome);
        }
    }
}
