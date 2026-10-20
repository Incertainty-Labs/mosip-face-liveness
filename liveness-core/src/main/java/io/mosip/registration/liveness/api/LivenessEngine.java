package io.mosip.registration.liveness.api;

/** Entry point used by both clients. Create one per application; it is shared by all sessions. */
public interface LivenessEngine {

    /**
     * Starts one attempt.
     *
     * @param context  who and what the session is for
     * @param policy   the workflow's resolved policy
     * @param callback receives states, prompts, progress and the result
     * @return the new session; never null
     */
    LivenessSession startSession(LivenessContext context, LivenessPolicy policy, LivenessCallback callback);

    /** @return engine and model versions and the execution provider in use */
    EngineInfo info();
}
