package io.mosip.registration.liveness.api;

import java.util.List;

/**
 * Ties a liveness session to the device-signed capture taken after it passed, closing the gap between unsigned
 * stream frames and the signed image (TECHNICAL_DESIGN §8).
 */
public interface CaptureBinder {

    /**
     * @param result       the session result, whose evidence digest is extended
     * @param finalCapture the signed capture, already decoded by the client
     * @param recentFrames the session's {@link LivenessSession#recentFrames()}
     * @return whether the capture is bound, with the final evidence digest
     */
    BindingResult verify(LivenessResult result, FaceFrame finalCapture, List<AnalysedFrame> recentFrames);
}
