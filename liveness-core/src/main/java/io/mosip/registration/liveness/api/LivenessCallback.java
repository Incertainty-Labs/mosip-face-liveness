package io.mosip.registration.liveness.api;

/**
 * Receives what a session reports. Called on the engine's thread (the stub engine calls on the frame thread), so
 * implementations must hop to their UI thread ({@code Platform.runLater} or {@code runOnUiThread}).
 */
public interface LivenessCallback {

    /**
     * The session moved to a new state or has a new prompt to show.
     *
     * @param state  the current state
     * @param prompt what the UI should show now
     */
    void onStateChanged(SessionState state, UserPrompt prompt);

    /**
     * A new random challenge starts. Not called for the final neutral hold.
     *
     * @param challenge the action requested
     * @param index     1-based position among the random challenges
     * @param total     number of random challenges in this session
     */
    void onChallenge(Challenge challenge, int index, int total);

    /**
     * Progress within the current step, for the progress ring.
     *
     * @param fraction 0..1
     */
    void onProgress(float fraction);

    /**
     * The session finished. Called exactly once per session.
     *
     * @param result the outcome
     */
    void onResult(LivenessResult result);
}
