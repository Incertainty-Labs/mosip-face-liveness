package io.mosip.registration.liveness.api;

/** One passive presentation-attack cue. Several detectors are fused by the engine. */
public interface PadDetector {

    /** @return the cue name, for example {@code minifasnet_v2}, {@code static_stream} */
    String name();

    /** @return the model version from {@code models.json}, or a rule version such as {@code rule-1.0} */
    String modelVersion();

    /**
     * Scores one frame.
     *
     * @param frame       the frame
     * @param observation the analyzer's output for that frame
     * @return the cue's verdict
     */
    PadScore score(FaceFrame frame, FaceObservation observation);
}
