package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.FaceObservation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The user's own neutral face, measured over the passive window: each measure's median over the frames with exactly
 * one face, skipping values the analyzer could not measure (NaN). Challenges are judged against it (TECHNICAL_DESIGN
 * §7.2), because a webcam above or below the face already reads 10° of pitch, and a neutral smile score is 0.
 * Immutable.
 */
public final class Baseline {

    private final double yawDeg;
    private final double pitchDeg;
    private final double rollDeg;
    private final double smile;
    private final double mouthOpen;
    private final double eyeOpenLeft;
    private final double eyeOpenRight;

    private Baseline(double[] medians) {
        this.yawDeg = medians[0];
        this.pitchDeg = medians[1];
        this.rollDeg = medians[2];
        this.smile = medians[3];
        this.mouthOpen = medians[4];
        this.eyeOpenLeft = medians[5];
        this.eyeOpenRight = medians[6];
    }

    /**
     * @param observations the passive window's observations
     * @return the medians; NaN for a measure no frame with one face had
     */
    public static Baseline of(List<FaceObservation> observations) {
        double[] medians = new double[7];
        for (int m = 0; m < medians.length; m++) {
            List<Double> values = new ArrayList<Double>();
            for (FaceObservation o : observations) {
                double v = o.faceCount() == 1 ? measure(o, m) : Double.NaN;
                if (!Double.isNaN(v)) {
                    values.add(v);
                }
            }
            medians[m] = median(values);
        }
        return new Baseline(medians);
    }

    /** @return neutral yaw, degrees */
    public double yawDeg() {
        return yawDeg;
    }

    /** @return neutral pitch, degrees */
    public double pitchDeg() {
        return pitchDeg;
    }

    /** @return neutral roll, degrees */
    public double rollDeg() {
        return rollDeg;
    }

    /** @return neutral smile, 0..1 */
    public double smile() {
        return smile;
    }

    /** @return neutral mouth openness, 0..1 */
    public double mouthOpen() {
        return mouthOpen;
    }

    /** @return neutral left eye openness, 0..1 */
    public double eyeOpenLeft() {
        return eyeOpenLeft;
    }

    /** @return neutral right eye openness, 0..1 */
    public double eyeOpenRight() {
        return eyeOpenRight;
    }

    private static double measure(FaceObservation o, int m) {
        switch (m) {
            case 0:
                return o.yawDeg();
            case 1:
                return o.pitchDeg();
            case 2:
                return o.rollDeg();
            case 3:
                return o.smile();
            case 4:
                return o.mouthOpen();
            case 5:
                return o.eyeOpenLeft();
            default:
                return o.eyeOpenRight();
        }
    }

    static double median(List<Double> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = new ArrayList<Double>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2;
    }
}
