package io.mosip.registration.liveness.desktop.overlay;

import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.ErrorCategory;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.MaxRetriesAction;
import io.mosip.registration.liveness.api.PromptLevel;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.UserPrompt;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * Turns a liveness session's events into what the overlay shows (UI_UX_SPEC §4), without any UI toolkit, so the same
 * rules drive the Workbench and the Desktop client. The engine's callbacks arrive on its worker thread; the listener
 * gets each new view on that thread and must hand it to the UI thread itself. Thread-safe.
 */
public final class OverlayModel {

    /** The guidance shown after a liveness failure (S10), in the PS04 wording. */
    public static final List<String> GUIDANCE_LIST = Collections.unmodifiableList(Arrays.asList(
            "liveness.guidance.look_at_camera", "liveness.guidance.improve_lighting",
            "liveness.guidance.face_not_found", "liveness.guidance.follow_prompt"));

    private final Consumer<OverlayView> listener;
    private OverlayView.Builder b = new OverlayView.Builder();

    /** @param listener receives every new view */
    public OverlayModel(Consumer<OverlayView> listener) {
        this.listener = listener;
    }

    /** S1: looking for the face camera, before the session starts. */
    public synchronized void searching() {
        b = new OverlayView.Builder();
        b.state = UiState.DEVICE_SEARCH;
        b.statusKey = "liveness.status.device_search";
        b.buttons = EnumSet.of(Action.CANCEL);
        emit();
    }

    /**
     * The engine's {@code onStateChanged}; final states are left to {@link #capturing}, {@link #failed} and
     * {@link #cancelled}, which know the attempt count.
     *
     * @param state  the session state
     * @param prompt what the engine asks for
     */
    public synchronized void state(SessionState state, UserPrompt prompt) {
        switch (state) {
            case WAITING_FOR_FACE:
                boolean guidance = prompt != null && prompt.level() == PromptLevel.GUIDANCE;
                b.state = guidance ? UiState.GUIDANCE : UiState.POSITION_FACE;
                b.level = guidance ? Level.GUIDANCE : Level.INFO;
                b.statusKey = "liveness.status.position";
                b.promptKey = guidance ? prompt.key() : null;
                b.ring = Float.NaN;
                break;
            case PASSIVE_CHECK:
                b.state = UiState.PASSIVE_CHECK;
                b.level = Level.INFO;
                b.statusKey = "liveness.status.checking";
                b.promptKey = "liveness.status.no_action";
                b.ring = 0f;
                break;
            case ACTIVE_CHALLENGE:
            case NEUTRAL_HOLD:
                b.state = state == SessionState.NEUTRAL_HOLD ? UiState.NEUTRAL_HOLD : UiState.CHALLENGE;
                b.level = Level.INFO;
                b.promptKey = prompt == null ? b.promptKey : prompt.key();
                b.ring = Float.NaN;
                break;
            default:
                return;
        }
        b.buttons = EnumSet.of(Action.CANCEL);
        emit();
    }

    /**
     * The engine's {@code onChallenge}: a new step starts with its whole time; any step after the first means the last
     * one was done (S6). The step dots appear only when there is more than the neutral hold.
     *
     * @param challenge the step
     * @param index     0-based
     * @param total     steps in the session
     */
    public synchronized void challenge(Challenge challenge, int index, int total) {
        b.state = challenge.type() == ChallengeType.NEUTRAL_HOLD ? UiState.NEUTRAL_HOLD : UiState.CHALLENGE;
        b.promptKey = challenge.promptKey();
        b.cue = Cue.of(challenge.type());
        b.timeLeft = 1f;
        if (total > 1) {
            b.stepTotal = total;
            b.statusKey = "liveness.progress.step";
            b.statusArgs = new Object[] {index + 1, total};
        }
        if (index > 0) {
            b.toastKey = "liveness.feedback.detected";
            b.toastSequence++;
        }
        emit();
    }

    /**
     * The engine's {@code onProgress}: the ring during the passive check, the time bar during a step.
     *
     * @param fraction 0..1
     */
    public synchronized void progress(float fraction) {
        if (b.state == UiState.PASSIVE_CHECK) {
            b.ring = fraction;
        } else if (b.state == UiState.CHALLENGE || b.state == UiState.NEUTRAL_HOLD) {
            b.timeLeft = 1f - fraction;
        } else {
            return;
        }
        emit();
    }

    /** S8: liveness passed and the device capture runs. */
    public synchronized void capturing() {
        b = new OverlayView.Builder();
        b.state = UiState.CAPTURING;
        b.statusKey = "liveness.feedback.hold_still";
        emit();
    }

    /** S9: the capture is bound to the session. */
    public synchronized void succeeded() {
        b = new OverlayView.Builder();
        b.state = UiState.SUCCESS;
        b.level = Level.SUCCESS;
        b.statusKey = "liveness.feedback.success";
        b.buttons = EnumSet.of(Action.NEXT);
        emit();
    }

    /**
     * The attempt ended without a capture: S10 to S14 by the error code, or S12 when no attempt is left.
     *
     * @param code         why
     * @param attempt      the attempt just used, 1-based
     * @param maxAttempts  attempts allowed
     * @param retryAllowed whether another attempt is allowed
     * @param onMax        what is offered when none is ({@code on.max.retries})
     */
    public synchronized void failed(LivenessErrorCode code, int attempt, int maxAttempts, boolean retryAllowed,
            MaxRetriesAction onMax) {
        b = new OverlayView.Builder();
        b.errorCode = code.code();
        b.level = Level.FAILURE;
        ErrorCategory category = code.category();
        boolean counted = category == ErrorCategory.LIVENESS || category == ErrorCategory.ATTACK
                || category == ErrorCategory.GUIDANCE;
        if (code == LivenessErrorCode.MAX_RETRIES_EXCEEDED || (counted && !retryAllowed)) {
            b.state = UiState.MAX_RETRIES;
            b.statusKey = LivenessErrorCode.MAX_RETRIES_EXCEEDED.messageKey();
            b.buttons = onMax == MaxRetriesAction.SUPERVISOR_OVERRIDE ? EnumSet.of(Action.REQUEST_OVERRIDE, Action.CLOSE)
                    : onMax == MaxRetriesAction.EXCEPTION_FLOW ? EnumSet.of(Action.MARK_EXCEPTION, Action.CLOSE)
                            : EnumSet.of(Action.CLOSE);
            showAttempt(attempt, maxAttempts);
        } else if (category == ErrorCategory.DEVICE) {
            b.state = UiState.DEVICE_ERROR;
            b.level = Level.DEVICE_ERROR;
            b.statusKey = code.messageKey();
            b.buttons = EnumSet.of(Action.RESCAN, Action.RETRY, Action.CANCEL);
        } else if (category == ErrorCategory.SYSTEM) {
            b.state = UiState.ENGINE_ERROR;
            b.statusKey = code.messageKey();
            b.buttons = EnumSet.of(Action.CLOSE);
        } else if (category == ErrorCategory.ATTACK) {
            b.state = UiState.PAD_FAILURE;
            b.statusKey = code.messageKey();
            b.buttons = EnumSet.of(Action.RETRY, Action.CANCEL);
            showAttempt(attempt, maxAttempts);
        } else {
            b.state = UiState.LIVENESS_FAILURE;
            b.statusKey = code.messageKey();
            b.guidanceKeys = GUIDANCE_LIST;
            b.buttons = EnumSet.of(Action.RETRY, Action.CANCEL);
            showAttempt(attempt, maxAttempts);
        }
        emit();
    }

    /** The session was cancelled: back to idle. */
    public synchronized void cancelled() {
        b = new OverlayView.Builder();
        emit();
    }

    private void showAttempt(int attempt, int maxAttempts) {
        b.attempt = attempt;
        b.maxAttempts = maxAttempts;
    }

    private void emit() {
        listener.accept(new OverlayView(b));
    }
}
