package io.mosip.registration.liveness.api;

import java.util.List;

/** One liveness attempt. Retries create a new session. */
public interface LivenessSession {

    /**
     * Hands the session the next frame. Returns quickly; if the engine is busy, the frame replaces any waiting one.
     * Frames after the result are ignored.
     *
     * @param frame the next frame from the source
     */
    void onFrame(FaceFrame frame);

    /** Cancels the session. Thread-safe and idempotent; reports a CANCELLED result once if none was reported yet. */
    void cancel();

    /** @return the current state */
    SessionState state();

    /** @return the last analysed frames (at most 5) with their observations, for the capture binder */
    List<AnalysedFrame> recentFrames();

    /** Drops the buffered frames. Call after binding and evidence are written. */
    void clear();
}
