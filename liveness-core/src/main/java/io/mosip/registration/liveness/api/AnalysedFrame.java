package io.mosip.registration.liveness.api;

/** One analysed frame and what the analyzer saw in it. Immutable. Used by the {@code CaptureBinder}. */
public final class AnalysedFrame {

    private final FaceFrame frame;
    private final FaceObservation observation;

    /**
     * @param frame       the frame
     * @param observation the analysis of that frame
     */
    public AnalysedFrame(FaceFrame frame, FaceObservation observation) {
        if (frame == null || observation == null) {
            throw new IllegalArgumentException("frame and observation are required");
        }
        this.frame = frame;
        this.observation = observation;
    }

    /** @return the frame */
    public FaceFrame frame() {
        return frame;
    }

    /** @return the analysis of the frame */
    public FaceObservation observation() {
        return observation;
    }
}
