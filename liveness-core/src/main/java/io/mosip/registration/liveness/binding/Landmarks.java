package io.mosip.registration.liveness.binding;

/**
 * Five comparable points from a {@code FaceObservation}'s landmarks, whichever form the analyzer gives: Face Mesh's
 * 478 points (MediaPipe indices) or YuNet's 5 (right eye, left eye, nose, right and left mouth corner).
 */
final class Landmarks {

    /** Face Mesh indices: right eye outer corner, left eye outer corner, nose tip, right and left mouth corner. */
    private static final int[] MESH = {33, 263, 1, 61, 291};
    private static final int MESH_POINTS = 478;

    private Landmarks() {
    }

    /**
     * @param a    landmarks as x,y pairs in frame pixels
     * @param b    the other face's landmarks
     * @param span the mean distance, relative to {@code a}'s eye distance, at which the agreement reaches 0
     * @return agreement 0..1; 0 when either has no usable landmarks
     */
    static double agreement(float[] a, float[] b, double span) {
        double[] pa = five(a);
        double[] pb = five(b);
        if (pa == null || pb == null) {
            return 0;
        }
        double eyes = Math.hypot(pa[2] - pa[0], pa[3] - pa[1]);
        if (eyes <= 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < 5; i++) {
            sum += Math.hypot(pa[2 * i] - pb[2 * i], pa[2 * i + 1] - pb[2 * i + 1]);
        }
        return Math.max(0, 1 - sum / 5 / eyes / span);
    }

    private static double[] five(float[] xy) {
        double[] out = new double[10];
        if (xy.length >= 2 * MESH_POINTS) {
            for (int i = 0; i < 5; i++) {
                out[2 * i] = xy[2 * MESH[i]];
                out[2 * i + 1] = xy[2 * MESH[i] + 1];
            }
            return out;
        }
        if (xy.length == 10) {
            for (int i = 0; i < 10; i++) {
                out[i] = xy[i];
            }
            return out;
        }
        return null;
    }
}
