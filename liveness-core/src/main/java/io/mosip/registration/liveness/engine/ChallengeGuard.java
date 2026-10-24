package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.Rect;

/**
 * The checks that run on every frame while the challenges are shown (TECHNICAL_DESIGN §7.2). Stateful: one instance
 * per session, used from the liveness thread only; not thread-safe.
 */
public final class ChallengeGuard {

    /** Face continuity: consecutive face boxes must overlap at least this much. */
    static final double MIN_IOU = 0.3;
    /** Motion plausibility: yaw or pitch may not change by more than this between close frames. */
    static final double MAX_LEAP_DEG = 15;
    /** Frames closer than this count as close (10 fps or faster). */
    static final long LEAP_WINDOW_MS = 100;

    private Rect lastBox;
    private long lastMs;
    private double lastYaw;
    private double lastPitch;

    /**
     * @param observation the frame's analysis
     * @param timestampMs the frame's time
     * @return the error that ends the attempt, or {@code null} while the session may go on
     */
    public LivenessErrorCode check(FaceObservation observation, long timestampMs) {
        if (observation.faceCount() > 1) {
            return LivenessErrorCode.MULTIPLE_FACES;
        }
        if (observation.faceCount() == 0) {
            return null;
        }
        Rect box = observation.box();
        // A face that jumps is not the face that was answering: a swap, or a cut in a replayed video
        boolean jumped = lastBox != null && iou(lastBox, box) < MIN_IOU;
        // A head turns smoothly; a pose that leaps between close frames is a cut in a replayed video
        boolean leapt = lastBox != null && timestampMs - lastMs < LEAP_WINDOW_MS
                && (Math.abs(observation.yawDeg() - lastYaw) > MAX_LEAP_DEG
                        || Math.abs(observation.pitchDeg() - lastPitch) > MAX_LEAP_DEG);
        lastBox = box;
        lastMs = timestampMs;
        lastYaw = observation.yawDeg();
        lastPitch = observation.pitchDeg();
        return jumped || leapt ? LivenessErrorCode.CHALLENGE_FAILED : null;
    }

    private static double iou(Rect a, Rect b) {
        double w = Math.min(a.x() + a.width(), b.x() + b.width()) - Math.max(a.x(), b.x());
        double h = Math.min(a.y() + a.height(), b.y() + b.height()) - Math.max(a.y(), b.y());
        double inter = w > 0 && h > 0 ? w * h : 0;
        return inter / ((double) a.width() * a.height() + (double) b.width() * b.height() - inter);
    }
}
