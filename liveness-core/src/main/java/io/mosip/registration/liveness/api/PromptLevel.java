package io.mosip.registration.liveness.api;

/** How a {@link UserPrompt} should be styled. */
public enum PromptLevel {
    /** Status or instruction. */
    INFO,
    /** Something the user can fix right now. */
    GUIDANCE,
    /** A step or the whole check succeeded. */
    SUCCESS,
    /** The attempt failed or an error occurred. */
    ERROR
}
