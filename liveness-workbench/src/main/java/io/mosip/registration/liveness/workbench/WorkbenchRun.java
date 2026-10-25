package io.mosip.registration.liveness.workbench;

import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.Decision;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FrameListener;
import io.mosip.registration.liveness.api.FrameSource;
import io.mosip.registration.liveness.api.LivenessCallback;
import io.mosip.registration.liveness.api.LivenessContext;
import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessException;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.LivenessSession;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.UserPrompt;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.desktop.overlay.OverlayModel;
import io.mosip.registration.liveness.desktop.overlay.OverlayView;
import io.mosip.registration.liveness.engine.AttemptTracker;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * One Workbench check at a time, as the Desktop client would run it: the source's frames go to a liveness session
 * and the overlay; a pass is followed by the device capture and the {@link CaptureCheck}; attempts are counted per
 * workflow with the clients' {@link AttemptTracker}; everything that happens is written to the audit pane.
 */
public final class WorkbenchRun {

    /** What the Workbench shows. Called from the engine's, the source's and the capture's threads. */
    public interface Ui {
        void overlay(OverlayView view);

        void frame(FaceFrame frame);

        void audit(String line);

        void result(ResultCard card);
    }

    /** Takes the signed capture from the device, with the session nonce as its transactionId. */
    public interface Capturer {
        /**
         * @param nonce the session nonce
         * @return the signed capture, or null when the source has no device (a recorded clip)
         * @throws IOException if the device fails
         */
        SignedCapture capture(String nonce) throws IOException;
    }

    private final LivenessEngine engine;
    private final CaptureCheck check;
    private final Capturer capturer;
    private final Ui ui;
    private final LongSupplier wallClock;
    private final Executor captureExecutor;
    private final OverlayModel overlay;
    private final Map<Workflow, AttemptTracker> trackers = new EnumMap<Workflow, AttemptTracker>(Workflow.class);
    private volatile LivenessSession session;
    private volatile FrameSource source;

    /**
     * @param engine          the liveness engine
     * @param check           the after-pass capture check
     * @param capturer        the device capture
     * @param ui              what shows the results
     * @param wallClock       the clock the device signs with, for the pass time
     * @param captureExecutor where the capture runs, off the engine's thread
     */
    public WorkbenchRun(LivenessEngine engine, CaptureCheck check, Capturer capturer, Ui ui, LongSupplier wallClock,
            Executor captureExecutor) {
        this.engine = engine;
        this.check = check;
        this.capturer = capturer;
        this.ui = ui;
        this.wallClock = wallClock;
        this.captureExecutor = captureExecutor;
        this.overlay = new OverlayModel(ui::overlay);
    }

    /**
     * Starts a check, unless the workflow's attempts are used up (LIV-011).
     *
     * @param workflow       the workflow to check
     * @param policy         its policy
     * @param modeOverridden whether the Workbench's mode override set its mode, for the audit
     * @param frames         the frame source; it is started here and stopped when the attempt ends
     */
    public void start(Workflow workflow, LivenessPolicy policy, boolean modeOverridden, FrameSource frames) {
        AttemptTracker tracker = tracker(workflow, policy);
        ui.audit("Start " + workflow + ": mode " + policy.mode() + (modeOverridden ? " (mode overridden in the"
                + " Workbench)" : "") + ", attempt " + tracker.current() + " of " + tracker.limit() + ", source "
                + frames.id());
        for (String warning : policy.warnings()) {
            ui.audit("Config warning: " + warning);
        }
        if (tracker.current() > tracker.limit()) {
            ui.audit("No attempt left (LIV-011); Reset starts the count again");
            overlay.failed(LivenessErrorCode.MAX_RETRIES_EXCEEDED, tracker.limit(), tracker.limit(), false,
                    policy.onMaxRetries());
            return;
        }
        overlay.searching();
        source = frames;
        LivenessSession started = engine.startSession(LivenessContext.builder().workflow(workflow)
                .source(frames.capabilities()).build(), policy, new Callback(workflow, policy, tracker));
        session = started;
        try {
            frames.start(new FrameListener() {
                @Override
                public void onFrame(FaceFrame frame) {
                    ui.frame(frame);
                    started.onFrame(frame);
                }

                @Override
                public void onError(LivenessErrorCode code, String detail) {
                    ui.audit("Source: " + code.code() + " " + detail);
                    started.cancel();
                    overlay.failed(code, tracker.current(), tracker.limit(), true, policy.onMaxRetries());
                }

                @Override
                public void onEnd() {
                    ui.audit("Source: end of stream");
                }
            });
        } catch (LivenessException e) {
            ui.audit("Source: " + e.errorCode().code() + " " + e.getMessage());
            started.cancel();
            overlay.failed(e.errorCode(), tracker.current(), tracker.limit(), true, policy.onMaxRetries());
        }
    }

    /** Cancels the check in progress, if any. */
    public void cancel() {
        LivenessSession s = session;
        if (s != null) {
            s.cancel();
        }
    }

    /** Returns the overlay to idle, for example after Close or Next. */
    public void clear() {
        overlay.cancelled();
    }

    /** Starts every workflow's attempt count again, as a new registration would. */
    public synchronized void resetAttempts() {
        for (AttemptTracker t : trackers.values()) {
            t.reset();
        }
        ui.audit("Attempts reset");
    }

    private synchronized AttemptTracker tracker(Workflow workflow, LivenessPolicy policy) {
        AttemptTracker t = trackers.get(workflow);
        if (t == null) {
            t = new AttemptTracker(policy, 0);
            trackers.put(workflow, t);
        }
        return t;
    }

    private void stopSource() {
        FrameSource s = source;
        if (s != null) {
            s.stop();
        }
    }

    /** The engine's events, on its worker thread. */
    private final class Callback implements LivenessCallback {
        private final Workflow workflow;
        private final LivenessPolicy policy;
        private final AttemptTracker tracker;

        Callback(Workflow workflow, LivenessPolicy policy, AttemptTracker tracker) {
            this.workflow = workflow;
            this.policy = policy;
            this.tracker = tracker;
        }

        @Override
        public void onStateChanged(SessionState state, UserPrompt prompt) {
            ui.audit("State " + state + (prompt == null ? "" : " (" + prompt.key() + ")"));
            overlay.state(state, prompt);
        }

        @Override
        public void onChallenge(Challenge challenge, int index, int total) {
            ui.audit("Challenge " + (index + 1) + " of " + total + ": " + challenge.type());
            overlay.challenge(challenge, index, total);
        }

        @Override
        public void onProgress(float fraction) {
            overlay.progress(fraction);
        }

        @Override
        public void onResult(LivenessResult result) {
            ui.audit("Result " + result.finalState() + " " + result.decision()
                    + (result.errorCode() == null ? "" : " " + result.errorCode().code()) + ", passive score "
                    + String.format("%.3f", result.passiveScore()) + ", " + result.framesAnalysed() + " frames");
            switch (result.finalState()) {
                case PASSED:
                    long passedAt = wallClock.getAsLong();
                    overlay.capturing();
                    captureExecutor.execute(() -> capture(result, passedAt));
                    return;
                case CANCELLED:
                    overlay.cancelled();
                    break;
                case ERROR:
                    overlay.failed(LivenessErrorCode.ENGINE_ERROR, tracker.current(), tracker.limit(), true,
                            policy.onMaxRetries());
                    break;
                default:
                    failed(result);
                    break;
            }
            stopSource();
            ui.result(new ResultCard(workflow.name(), result, null));
        }

        private void capture(LivenessResult result, long passedAt) {
            CaptureCheck.Outcome outcome;
            try {
                SignedCapture signed = capturer.capture(result.nonce());
                if (signed == null) {
                    // A recorded clip: liveness is all there is to show
                    ui.audit("Passed; no device capture (the source is a clip)");
                    stopSource();
                    overlay.succeeded();
                    ui.result(new ResultCard(workflow.name(), result, null));
                    return;
                }
                outcome = check.check(result, signed, passedAt, policy, session.recentFrames());
            } catch (IOException e) {
                ui.audit("Capture failed: " + e.getMessage());
                stopSource();
                overlay.failed(LivenessErrorCode.DEVICE_CONNECTION_FAILED, tracker.current(), tracker.limit(), true,
                        policy.onMaxRetries());
                ui.result(new ResultCard(workflow.name(), result, null));
                return;
            }
            stopSource();
            if (outcome.bound()) {
                ui.audit("Capture bound: signature valid, nonce echoed, signed in time"
                        + (outcome.binding() == null ? ", scene check off" : ", scene similarity "
                                + String.format("%.3f", outcome.binding().similarity())));
                overlay.succeeded();
            } else {
                ui.audit("Capture refused (" + outcome.errorCode().code() + "): " + outcome.reason());
                failed(LivenessResult.builder(SessionState.FAILED, Decision.ATTACK_SUSPECTED)
                        .errorCode(outcome.errorCode()).build());
            }
            ui.result(new ResultCard(workflow.name(), result, outcome));
        }

        /** An attempt that ended in failure: counted by the clients' rules, then shown with what is left. */
        private void failed(LivenessResult result) {
            int attempt = tracker.current();
            boolean retryAllowed = tracker.record(result);
            if (!retryAllowed) {
                ui.audit("Attempts used up (" + tracker.limit() + "); on.max.retries " + policy.onMaxRetries());
            }
            overlay.failed(result.errorCode(), attempt, tracker.limit(), retryAllowed, policy.onMaxRetries());
        }
    }
}
