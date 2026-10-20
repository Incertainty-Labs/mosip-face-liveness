package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.LivenessCallback;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.UserPrompt;
import java.util.ArrayList;
import java.util.List;

/** Test callback that records what a session reports, in order. */
final class RecordingCallback implements LivenessCallback {

    private final List<SessionState> states = new ArrayList<SessionState>();
    private final List<UserPrompt> prompts = new ArrayList<UserPrompt>();
    private final List<String> challengeSteps = new ArrayList<String>();
    private final List<ChallengeType> challengeTypes = new ArrayList<ChallengeType>();
    private final List<LivenessResult> results = new ArrayList<LivenessResult>();

    @Override
    public void onStateChanged(SessionState state, UserPrompt prompt) {
        states.add(state);
        prompts.add(prompt);
    }

    @Override
    public void onChallenge(Challenge challenge, int index, int total) {
        challengeSteps.add(index + "/" + total);
        challengeTypes.add(challenge.type());
    }

    @Override
    public void onProgress(float fraction) {
        // progress is not asserted by these tests
    }

    @Override
    public void onResult(LivenessResult result) {
        results.add(result);
    }

    /** @return reported states with consecutive repeats removed */
    List<SessionState> distinctStates() {
        List<SessionState> distinct = new ArrayList<SessionState>();
        for (SessionState s : states) {
            if (distinct.isEmpty() || distinct.get(distinct.size() - 1) != s) {
                distinct.add(s);
            }
        }
        return distinct;
    }

    List<UserPrompt> prompts() {
        return prompts;
    }

    List<String> challengeSteps() {
        return challengeSteps;
    }

    List<ChallengeType> challengeTypes() {
        return challengeTypes;
    }

    int resultCount() {
        return results.size();
    }

    LivenessResult result() {
        return results.isEmpty() ? null : results.get(0);
    }
}
