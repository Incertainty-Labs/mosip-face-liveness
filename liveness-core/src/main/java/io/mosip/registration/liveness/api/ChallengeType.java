package io.mosip.registration.liveness.api;

import java.util.Locale;

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
    NEUTRAL_HOLD;

    /** @return the UI message key for this action's prompt, for example {@code liveness.prompt.turn_left} */
    public String promptKey() {
        return this == NEUTRAL_HOLD ? "liveness.prompt.neutral" : "liveness.prompt." + name().toLowerCase(Locale.ROOT);
    }
}
