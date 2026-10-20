package io.mosip.registration.liveness.api;

/** What happens when a workflow reaches its maximum number of liveness attempts. */
public enum MaxRetriesAction {
    /** Capture or authentication stays blocked; the user is told to contact a supervisor. */
    BLOCK,
    /** A supervisor face authentication, with its own liveness check, unlocks one more attempt. */
    SUPERVISOR_OVERRIDE,
    /** The operator may mark the face as a biometric exception under the existing MOSIP rules. */
    EXCEPTION_FLOW
}
