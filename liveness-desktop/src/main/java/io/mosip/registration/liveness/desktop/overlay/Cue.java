package io.mosip.registration.liveness.desktop.overlay;

import io.mosip.registration.liveness.api.ChallengeType;

/** The icon beside a challenge's prompt (UI_UX_SPEC §3.1, direction cue). */
public enum Cue {
    NONE, LEFT, RIGHT, UP, DOWN, SMILE, MOUTH, EYES;

    /**
     * @param type the challenge
     * @return its cue; turns are the person's own left and right, as the prompts say
     */
    public static Cue of(ChallengeType type) {
        switch (type) {
            case TURN_LEFT:
                return LEFT;
            case TURN_RIGHT:
                return RIGHT;
            case LOOK_UP:
                return UP;
            case LOOK_DOWN:
                return DOWN;
            case SMILE:
                return SMILE;
            case OPEN_MOUTH:
                return MOUTH;
            case CLOSE_EYES:
            case BLINK:
                return EYES;
            default:
                return NONE;
        }
    }
}
