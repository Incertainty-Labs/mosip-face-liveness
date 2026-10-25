package io.mosip.registration.liveness.desktop.overlay;

/** The state group that picks the overlay's colour and icon (UI_UX_SPEC §3.2). */
public enum Level {
    /** Working or instructing: the primary colour. */
    INFO,
    /** A fixable problem: amber. */
    GUIDANCE,
    /** Done: green. */
    SUCCESS,
    /** Liveness or PAD failed: red. */
    FAILURE,
    /** The device or the engine failed: red, with a device icon. */
    DEVICE_ERROR
}
