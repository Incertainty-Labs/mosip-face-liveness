package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.ChallengeGenerator;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.SourceCapabilities;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * The random challenge sequence of a session (TECHNICAL_DESIGN §7.1): {@code challenge.min.count} to
 * {@code challenge.max.count} challenges drawn from {@code challenge.pool}, never the same type twice in a row, then
 * NEUTRAL_HOLD; each with {@code challenge.timeout.ms}. BLINK needs {@code blink.min.fps}, judged on the measured
 * frame rate or, when that is unknown (0), the nominal one; CLOSE_EYES and BLINK need eye measurements. Opposite
 * challenges may follow each other, because each challenge first waits for the face to return near its baseline.
 * Thread-safe.
 */
public final class RandomChallengeGenerator implements ChallengeGenerator {

    private final boolean eyesSupported;

    /** @param eyesSupported whether the face analyzer measures eye openness (CLOSE_EYES and BLINK need it) */
    public RandomChallengeGenerator(boolean eyesSupported) {
        this.eyesSupported = eyesSupported;
    }

    @Override
    public List<Challenge> generate(LivenessPolicy policy, SourceCapabilities source, SecureRandom random) {
        List<ChallengeType> pool = new ArrayList<ChallengeType>(policy.challengePool());
        // An unknown measured rate (0) falls back to the device's nominal one; with neither, a blink could fall
        // between frames, so BLINK is left out (CLOSE_EYES still tests the eyes)
        double fps = source.measuredFps() > 0 ? source.measuredFps() : source.nominalFps();
        if (!(fps >= policy.blinkMinFps())) {
            pool.remove(ChallengeType.BLINK);
        }
        if (!eyesSupported) {
            pool.remove(ChallengeType.CLOSE_EYES);
            pool.remove(ChallengeType.BLINK);
        }
        int count = policy.challengeMinCount()
                + random.nextInt(policy.challengeMaxCount() - policy.challengeMinCount() + 1);
        List<Challenge> sequence = new ArrayList<Challenge>();
        ChallengeType last = null;
        for (int i = 0; i < count; i++) {
            // Never the same type twice in a row, so one recorded gesture cannot answer two prompts
            List<ChallengeType> candidates = new ArrayList<ChallengeType>(pool);
            candidates.remove(last);
            if (candidates.isEmpty()) {
                candidates = pool;
            }
            last = candidates.get(random.nextInt(candidates.size()));
            sequence.add(new Challenge(last, policy.challengeTimeoutMs()));
        }
        sequence.add(new Challenge(ChallengeType.NEUTRAL_HOLD, policy.challengeTimeoutMs()));
        return sequence;
    }
}
