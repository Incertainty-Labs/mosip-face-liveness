package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.FaceAnalyzer;

/**
 * A face analyzer that runs its face detector on some frames only and follows the face in between. The engine runs
 * the passive PAD models only on frames where the detector ran, because they were calibrated on detector boxes.
 */
public interface DetectingAnalyzer extends FaceAnalyzer {

    /** @return whether the last {@code analyze} ran the face detector */
    boolean detected();
}
