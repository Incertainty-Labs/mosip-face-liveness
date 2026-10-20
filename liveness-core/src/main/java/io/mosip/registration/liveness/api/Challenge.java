package io.mosip.registration.liveness.api;

/** One requested facial action with its time limit. Immutable. */
public final class Challenge {

    private final ChallengeType type;
    private final long timeoutMs;

    /**
     * @param type      the action requested
     * @param timeoutMs time allowed to complete it, in milliseconds; greater than zero
     */
    public Challenge(ChallengeType type, long timeoutMs) {
        if (type == null || timeoutMs <= 0) {
            throw new IllegalArgumentException("type is required and timeoutMs must be positive");
        }
        this.type = type;
        this.timeoutMs = timeoutMs;
    }

    /** @return the action requested */
    public ChallengeType type() {
        return type;
    }

    /** @return time allowed, in milliseconds */
    public long timeoutMs() {
        return timeoutMs;
    }

    /** @return the UI message key for the prompt, for example {@code liveness.prompt.turn_left} */
    public String promptKey() {
        return type.promptKey();
    }
}
