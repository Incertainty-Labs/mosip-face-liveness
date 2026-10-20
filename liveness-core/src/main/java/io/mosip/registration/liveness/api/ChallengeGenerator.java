package io.mosip.registration.liveness.api;

import java.security.SecureRandom;
import java.util.List;

/** Chooses the random, unpredictable challenge sequence for a session. */
public interface ChallengeGenerator {

    /**
     * @param policy the workflow policy (pool, counts, timeouts, minimum fps for BLINK)
     * @param source the frame source, for its measured frame rate
     * @param random the random source
     * @return {@code min..max} challenges from the pool followed by NEUTRAL_HOLD; never empty
     */
    List<Challenge> generate(LivenessPolicy policy, SourceCapabilities source, SecureRandom random);
}
