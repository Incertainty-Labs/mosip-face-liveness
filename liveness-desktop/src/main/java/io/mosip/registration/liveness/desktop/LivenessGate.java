package io.mosip.registration.liveness.desktop;

import io.mosip.registration.liveness.api.Challenge;
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

/**
 * The liveness check the Registration Client runs before it asks the device for the face (TECHNICAL_DESIGN §9):
 * the client's stream feeds a session, the overlay follows it, and only a pass lets the client capture. Attempts are
 * counted for one face field with the clients' {@link AttemptTracker}; with none left, Scan stays off and the
 * overlay shows S12 with the actions {@code on.max.retries} allows. One instance per field and registration.
 */
public final class LivenessGate {

    /** What the client is told; called from the engine's and the source's threads. */
    public interface Listener {
        /** @param view what the overlay shows now */
        void view(OverlayView view);

        /** @param frame a frame the check saw, for the overlay's preview */
        void frame(FaceFrame frame);

        /** @param result the pass: the client now asks the device for the face */
        void passed(LivenessResult result);

        /**
         * @param result       the attempt's end without a pass
         * @param retryAllowed whether Scan may run again for this field
         */
        void ended(LivenessResult result, boolean retryAllowed);
    }

    private final LivenessEngine engine;
    private final LivenessPolicy policy;
    private final Workflow workflow;
    private final Listener listener;
    private final OverlayModel overlay;
    private final AttemptTracker tracker;
    private volatile LivenessSession session;

    /**
     * @param engine           the liveness engine
     * @param policy           the workflow's policy
     * @param workflow         {@code RESIDENT_REGISTRATION}, or {@code RESIDENT_EXCEPTION_PHOTO} (passive only)
     * @param clientRetryLimit the client's own face retry limit; 0 or less when it has none
     * @param listener         what the client is told
     */
    public LivenessGate(LivenessEngine engine, LivenessPolicy policy, Workflow workflow, int clientRetryLimit,
            Listener listener) {
        this.engine = engine;
        this.policy = policy;
        this.workflow = workflow;
        this.listener = listener;
        this.overlay = new OverlayModel(listener::view);
        this.tracker = new AttemptTracker(policy, clientRetryLimit);
    }

    /** @return whether an attempt is left for this field; when not, Scan stays off (S12) */
    public boolean attemptLeft() {
        return tracker.current() <= tracker.limit();
    }

    /**
     * Starts a check on the client's stream, unless no attempt is left (LIV-011), in which case the stream is not
     * started.
     *
     * @param frames the client's stream; stopped when the check ends
     */
    public void start(FrameSource frames) {
        if (!attemptLeft()) {
            overlay.failed(LivenessErrorCode.MAX_RETRIES_EXCEEDED, tracker.limit(), tracker.limit(), false,
                    policy.onMaxRetries());
            return;
        }
        overlay.searching();
        Callback callback = new Callback(frames);
        LivenessSession session = engine.startSession(LivenessContext.builder().workflow(workflow)
                .source(frames.capabilities()).build(), policy, callback);
        this.session = session;
        try {
            frames.start(new FrameListener() {
                @Override
                public void onFrame(FaceFrame frame) {
                    listener.frame(frame);
                    session.onFrame(frame);
                }

                @Override
                public void onError(LivenessErrorCode code, String detail) {
                    // An unreadable frame is skipped and the stream goes on; anything else ends the check
                    if (code != LivenessErrorCode.INVALID_FRAME_DATA) {
                        callback.deviceError = code;
                        session.cancel();
                    }
                }

                @Override
                public void onEnd() {
                }
            });
        } catch (LivenessException e) {
            callback.deviceError = e.errorCode();
            session.cancel();
        }
    }

    /** Ends the check in progress, if any, for example when the operator closes the liveness window. */
    public void cancel() {
        LivenessSession s = session;
        if (s != null) {
            s.cancel();
        }
    }

    /** The engine's events, on its worker thread. */
    private final class Callback implements LivenessCallback {
        private final FrameSource frames;
        /** Why the source ended the check, if it did: a device error, which uses no attempt. */
        volatile LivenessErrorCode deviceError;

        Callback(FrameSource frames) {
            this.frames = frames;
        }

        @Override
        public void onStateChanged(SessionState state, UserPrompt prompt) {
            overlay.state(state, prompt);
        }

        @Override
        public void onChallenge(Challenge challenge, int index, int total) {
            overlay.challenge(challenge, index, total);
        }

        @Override
        public void onProgress(float fraction) {
            overlay.progress(fraction);
        }

        @Override
        public void onResult(LivenessResult result) {
            frames.stop();
            switch (result.finalState()) {
                case PASSED:
                    overlay.capturing();
                    listener.passed(result);
                    return;
                case CANCELLED:
                    LivenessErrorCode device = deviceError;
                    if (device != null) {
                        overlay.failed(device, tracker.current(), tracker.limit(), true, policy.onMaxRetries());
                    } else {
                        overlay.cancelled();
                    }
                    listener.ended(result, true);
                    return;
                default:
                    int attempt = tracker.current();
                    boolean retryAllowed = tracker.record(result);
                    overlay.failed(result.errorCode(), attempt, tracker.limit(), retryAllowed, policy.onMaxRetries());
                    listener.ended(result, retryAllowed);
                    return;
            }
        }
    }
}
