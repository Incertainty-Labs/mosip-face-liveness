package io.mosip.registration.liveness.api;

/** Receives frames and source events from a {@link FrameSource}. */
public interface FrameListener {

    /** @param frame the next decoded frame */
    void onFrame(FaceFrame frame);

    /**
     * A source problem: LIV-002 (connection) or LIV-012 (unreadable frame).
     *
     * @param code   the error
     * @param detail technical detail for logs
     */
    void onError(LivenessErrorCode code, String detail);

    /** The source ended (stream closed, file finished). */
    void onEnd();
}
