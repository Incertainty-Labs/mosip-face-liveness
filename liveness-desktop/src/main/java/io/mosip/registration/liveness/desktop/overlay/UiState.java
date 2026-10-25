package io.mosip.registration.liveness.desktop.overlay;

/** The overlay's states, as numbered in UI_UX_SPEC §4. Feedback (S6) and offline (S15) are details of a view. */
public enum UiState {
    /** Before a session, or after a cancel. */
    IDLE,
    /** S1: looking for the face camera. */
    DEVICE_SEARCH,
    /** S2: waiting for a face in the oval. */
    POSITION_FACE,
    /** S3: a fixable problem with the face (LIV-004, 005, 006). */
    GUIDANCE,
    /** S4: the passive check. */
    PASSIVE_CHECK,
    /** S5: an active challenge. */
    CHALLENGE,
    /** S7: the neutral hold that ends every session. */
    NEUTRAL_HOLD,
    /** S8: liveness passed; the device capture is running. */
    CAPTURING,
    /** S9: the capture is bound to the session. */
    SUCCESS,
    /** S10: liveness failed (LIV-005 during challenges, 007, 009, 010). */
    LIVENESS_FAILURE,
    /** S11: a presentation attack is suspected (LIV-008, 014, 015). */
    PAD_FAILURE,
    /** S12: no attempt is left (LIV-011, or the last attempt failed). */
    MAX_RETRIES,
    /** S13: the device or its stream failed (LIV-001, 002, 003, 012). */
    DEVICE_ERROR,
    /** S14: the engine cannot run (LIV-013). */
    ENGINE_ERROR
}
