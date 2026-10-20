package io.mosip.registration.liveness.api;

/** The liveness verdict of a finished session. */
public enum Decision {
    /** A live person was verified. */
    LIVE,
    /** A presentation attack is suspected. */
    ATTACK_SUSPECTED,
    /** Neither verdict could be reached (timeout, failed challenge, device or engine problem, cancel). */
    INCONCLUSIVE
}
