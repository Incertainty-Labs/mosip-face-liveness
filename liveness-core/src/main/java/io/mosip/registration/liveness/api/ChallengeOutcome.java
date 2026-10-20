package io.mosip.registration.liveness.api;

/** How one challenge of a session ended. Immutable. */
public final class ChallengeOutcome {

    private final ChallengeType type;
    private final ChallengeProgress outcome;
    private final long durationMs;

    /**
     * @param type       the action that was requested
     * @param outcome    how it ended; never {@link ChallengeProgress#IN_PROGRESS}
     * @param durationMs time from prompt to outcome, in milliseconds
     */
    public ChallengeOutcome(ChallengeType type, ChallengeProgress outcome, long durationMs) {
        if (type == null || outcome == null || outcome == ChallengeProgress.IN_PROGRESS) {
            throw new IllegalArgumentException("type and a final outcome are required");
        }
        this.type = type;
        this.outcome = outcome;
        this.durationMs = durationMs;
    }

    /** @return the action that was requested */
    public ChallengeType type() {
        return type;
    }

    /** @return how it ended */
    public ChallengeProgress outcome() {
        return outcome;
    }

    /** @return time from prompt to outcome, in milliseconds */
    public long durationMs() {
        return durationMs;
    }
}
