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

    /**
     * After each analysed frame, for a diagnostics panel (v0.2.2); never for the user, and nothing to implement
     * otherwise. Called on the engine's thread before the session acts on the frame; an exception thrown here is
     * ignored, so diagnostics can never change a result.
     *
     * @param observation what the face analyzer saw on the frame (no pixels)
     * @param scores      each PAD model's live probability on this frame, by name; empty on frames the PAD models did
     *                    not run on (no detector box, or the quality gate turned the frame away)
     */
    default void onFrameAnalysed(FaceObservation observation, java.util.Map<String, Double> scores) {
    }
}
