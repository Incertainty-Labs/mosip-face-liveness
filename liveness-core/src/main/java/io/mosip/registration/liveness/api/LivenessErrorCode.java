package io.mosip.registration.liveness.api;

/**
 * Every liveness outcome other than success, as listed in API.md §6. Clients wrap these one-to-one in their own
 * error types ({@code REG-LIV-0xx}).
 */
public enum LivenessErrorCode {
    /** No face capture device found. */
    DEVICE_UNAVAILABLE("LIV-001", ErrorCategory.DEVICE, "liveness.error.device_unavailable", AttemptRule.NEVER),
    /** The device stream could not start or dropped. */
    DEVICE_CONNECTION_FAILED("LIV-002", ErrorCategory.DEVICE, "liveness.error.device_connection", AttemptRule.NEVER),
    /** The camera is missing or permission was denied. */
    CAMERA_UNAVAILABLE("LIV-003", ErrorCategory.DEVICE, "liveness.error.camera_unavailable", AttemptRule.NEVER),
    /** No face in the frame. */
    FACE_NOT_DETECTED("LIV-004", ErrorCategory.GUIDANCE, "liveness.guidance.face_not_found", AttemptRule.NEVER),
    /** More than one face in the frame; counts as an attempt only during an active challenge. */
    MULTIPLE_FACES("LIV-005", ErrorCategory.GUIDANCE, "liveness.guidance.one_face_only",
            AttemptRule.DURING_CHALLENGE),
    /** Face too small, off-centre, blurred, too dark or too bright. */
    POOR_FACE_QUALITY("LIV-006", ErrorCategory.GUIDANCE, "liveness.guidance.improve_lighting", AttemptRule.NEVER),
    /**
     * Passive score below the threshold. Internal when it escalates to challenges; a failure (with this message key)
     * only when terminal, that is in mode PASSIVE or with active challenges disabled.
     */
    BELOW_THRESHOLD("LIV-007", ErrorCategory.LIVENESS, "liveness.fail.try_again", AttemptRule.WHEN_TERMINAL),
    /** Presentation attack suspected by the passive cues. */
    PAD_FAILED("LIV-008", ErrorCategory.ATTACK, "liveness.fail.generic", AttemptRule.ALWAYS),
    /** The user did not perform the requested action. */
    CHALLENGE_FAILED("LIV-009", ErrorCategory.LIVENESS, "liveness.fail.try_again", AttemptRule.ALWAYS),
    /** The requested action was not completed in time. */
    CHALLENGE_TIMEOUT("LIV-010", ErrorCategory.LIVENESS, "liveness.fail.timeout", AttemptRule.ALWAYS),
    /** The workflow has used all its attempts. */
    MAX_RETRIES_EXCEEDED("LIV-011", ErrorCategory.POLICY, "liveness.fail.max_retries", AttemptRule.NEVER),
    /** Frames could not be decoded. */
    INVALID_FRAME_DATA("LIV-012", ErrorCategory.DEVICE, "liveness.error.invalid_frame", AttemptRule.NEVER),
    /** The engine failed, for example a model hash mismatch; the face is never accepted. */
    ENGINE_ERROR("LIV-013", ErrorCategory.SYSTEM, "liveness.error.engine", AttemptRule.NEVER),
    /** The signed device capture does not match the analysed frames. */
    CAPTURE_BINDING_FAILED("LIV-014", ErrorCategory.ATTACK, "liveness.fail.generic", AttemptRule.ALWAYS),
    /** The stream repeats the same frame, as a still image injected as video would. */
    STATIC_STREAM("LIV-015", ErrorCategory.ATTACK, "liveness.fail.generic", AttemptRule.ALWAYS);

    /** When an error uses up one of the workflow's attempts. */
    private enum AttemptRule {
        NEVER, ALWAYS, DURING_CHALLENGE, WHEN_TERMINAL
    }

    private final String code;
    private final ErrorCategory category;
    private final String messageKey;
    private final AttemptRule attemptRule;

    LivenessErrorCode(String code, ErrorCategory category, String messageKey, AttemptRule attemptRule) {
        this.code = code;
        this.category = category;
        this.messageKey = messageKey;
        this.attemptRule = attemptRule;
    }

    /** @return the stable code, for example {@code LIV-008} */
    public String code() {
        return code;
    }

    /** @return the broad kind of error */
    public ErrorCategory category() {
        return category;
    }

    /** @return the UI message key (API.md §7); never shown together with scores or cue names */
    public String messageKey() {
        return messageKey;
    }

    /**
     * Whether this error uses up one of the workflow's attempts.
     *
     * @param duringChallenge whether the error was raised during an active challenge
     * @param terminal        whether the error ended the attempt instead of escalating (relevant for LIV-007)
     * @return {@code true} if the attempt counter must be incremented
     */
    public boolean countsAsAttempt(boolean duringChallenge, boolean terminal) {
        switch (attemptRule) {
            case ALWAYS:
                return true;
            case DURING_CHALLENGE:
                return duringChallenge;
            case WHEN_TERMINAL:
                return terminal;
            default:
                return false;
        }
    }

    /**
     * Looks up an error by its code.
     *
     * @param code a code such as {@code LIV-010}
     * @return the matching error
     * @throws IllegalArgumentException if no error has that code
     */
    public static LivenessErrorCode fromCode(String code) {
        for (LivenessErrorCode error : values()) {
            if (error.code.equals(code)) {
                return error;
            }
        }
        throw new IllegalArgumentException("Unknown liveness error code: " + code);
    }
}
