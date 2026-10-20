package io.mosip.registration.liveness.api;

/** Vendor-neutral face frame producer: an SBI stream, a webcam, CameraX or a video file. */
public interface FrameSource extends AutoCloseable {

    /** @return a stable id, for example {@code sbi095:3}, {@code webcam:0}, {@code camerax:front} */
    String id();

    /** @return static facts plus the live measured frame rate; never null */
    SourceCapabilities capabilities();

    /**
     * Starts delivering frames.
     *
     * @param listener receives frames and source events
     * @throws LivenessException with LIV-001, LIV-002 or LIV-003 if the source cannot start
     */
    void start(FrameListener listener) throws LivenessException;

    /** Stops delivery. Safe to call twice. */
    void stop();

    /** Same as {@link #stop()}. */
    @Override
    default void close() {
        stop();
    }
}
