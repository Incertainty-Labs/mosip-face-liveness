package io.mosip.registration.liveness.api;

/**
 * Thrown only by setup calls such as {@code FrameSource.start} or model loading. Problems during a session arrive as
 * a result with an error code, never as an exception on the frame thread.
 */
public class LivenessException extends Exception {

    private static final long serialVersionUID = 1L;

    private final LivenessErrorCode errorCode;

    /**
     * @param errorCode the error; required
     * @param detail    technical detail for logs and audit
     */
    public LivenessException(LivenessErrorCode errorCode, String detail) {
        super(errorCode == null ? null : errorCode.code() + ": " + detail);
        if (errorCode == null) {
            throw new IllegalArgumentException("errorCode is required");
        }
        this.errorCode = errorCode;
    }

    /** @return the error */
    public LivenessErrorCode errorCode() {
        return errorCode;
    }
}
