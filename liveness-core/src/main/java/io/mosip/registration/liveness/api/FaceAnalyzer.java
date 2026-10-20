package io.mosip.registration.liveness.api;

/** Finds the face and measures pose, expression and quality in one frame. */
public interface FaceAnalyzer {

    /**
     * Analyses one frame. A pure function of the frame; reports {@code faceCount = 0} instead of throwing.
     *
     * @param frame the frame
     * @return what was seen
     */
    FaceObservation analyze(FaceFrame frame);
}
