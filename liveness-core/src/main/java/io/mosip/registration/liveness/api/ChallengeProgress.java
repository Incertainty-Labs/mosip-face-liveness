package io.mosip.registration.liveness.api;

/** Status of one challenge as frames are verified. */
public enum ChallengeProgress {
    /** Still waiting for the action. */
    IN_PROGRESS,
    /** The action was performed. */
    PASSED,
    /** A wrong action, a second face or a face swap was seen. */
    FAILED,
    /** The action was not completed within the challenge timeout. */
    TIMED_OUT
}
