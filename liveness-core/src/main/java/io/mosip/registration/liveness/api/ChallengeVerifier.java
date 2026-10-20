package io.mosip.registration.liveness.api;

/** Decides from successive observations whether one kind of challenge was performed. */
public interface ChallengeVerifier {

    /** @return the challenge type this verifier handles */
    ChallengeType type();

    /**
     * Feeds the next observation, in time order.
     *
     * @param challenge   the challenge being verified
     * @param observation the latest observation
     * @param timestampMs the frame time
     * @return IN_PROGRESS, PASSED, FAILED or TIMED_OUT
     */
    ChallengeProgress update(Challenge challenge, FaceObservation observation, long timestampMs);

    /** Forgets all state so the verifier can start a new challenge. */
    void reset();
}
