package io.mosip.registration.liveness.api;

/** How a session decides liveness. */
public enum LivenessMode {
    /** Liveness is not checked. */
    OFF,
    /** Passive check only; a low score fails the attempt. */
    PASSIVE,
    /** Active challenges always run; passive scoring still vetoes. */
    ACTIVE,
    /** Passive check first; challenges start only when the passive score is below the threshold. */
    HYBRID
}
