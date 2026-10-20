package io.mosip.registration.liveness.api;

/** Where a liveness session is. A session is one attempt; it ends in exactly one of the last four states. */
public enum SessionState {
    /** Waiting for exactly one face of usable quality. */
    WAITING_FOR_FACE,
    /** Scoring frames for passive liveness; no user action needed. */
    PASSIVE_CHECK,
    /** The user is performing a requested facial action. */
    ACTIVE_CHALLENGE,
    /** The user looks straight at the camera and holds still before capture. */
    NEUTRAL_HOLD,
    /** Liveness established; the device capture may now be taken. */
    PASSED,
    /** Liveness not established or an attack suspected; counts per the error's attempt rule. */
    FAILED,
    /** A device or engine problem stopped the session. */
    ERROR,
    /** The user or the client cancelled the session. */
    CANCELLED;

    /** @return whether this state ends the session */
    public boolean isFinal() {
        return this == PASSED || this == FAILED || this == ERROR || this == CANCELLED;
    }
}
