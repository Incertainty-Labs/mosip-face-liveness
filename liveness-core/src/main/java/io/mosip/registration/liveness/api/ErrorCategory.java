package io.mosip.registration.liveness.api;

/** Broad kind of a {@link LivenessErrorCode}, used to pick the UI treatment and recovery path. */
public enum ErrorCategory {
    /** The capture device, camera or frame data failed; the user fixes the setup and retries. */
    DEVICE,
    /** The user can fix it live (position, lighting, one person only); shown as guidance. */
    GUIDANCE,
    /** Liveness could not be established (low score, failed or timed-out challenge). */
    LIVENESS,
    /** A presentation attack is suspected; the UI shows only a generic message. */
    ATTACK,
    /** A policy limit was reached (maximum attempts). */
    POLICY,
    /** The liveness engine itself failed; fail closed. */
    SYSTEM
}
