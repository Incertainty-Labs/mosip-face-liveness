package io.mosip.registration.liveness.api;

/** A facial action the user may be asked to perform. */
public enum ChallengeType {
    /** Turn the head to the user's own left. */
    TURN_LEFT,
    /** Turn the head to the user's own right. */
    TURN_RIGHT,
    /** Tilt the head up. */
    LOOK_UP,
    /** Tilt the head down. */
    LOOK_DOWN,
    /** Smile. */
    SMILE,
    /** Open the mouth. */
    OPEN_MOUTH,
    /** Close both eyes for a moment. */
    CLOSE_EYES,
    /** Blink; offered only when the measured frame rate is high enough. */
    BLINK,
    /** Look straight at the camera and hold still; always the last step before capture. */
    NEUTRAL_HOLD
}
