package io.mosip.registration.liveness.engine.onnx;

/**
 * Head pose from Face Mesh's 3D landmarks. The face's own axes are X from the right cheek to the left cheek (points
 * 234 to 454), Y from forehead to chin (10 to 152, made perpendicular to X) and Z = X x Y (away from the camera). Their
 * rotation from the camera axes is read as R = Rroll . Rpitch . Ryaw, giving the API's signs: yaw positive when the
 * person turns to their own left, pitch positive when looking up, roll positive when the head tilts so the person's
 * left cheek is lower in the image. Immutable.
 */
public final class HeadPose {

    private static final int RIGHT_CHEEK = 234;
    private static final int LEFT_CHEEK = 454;
    private static final int FOREHEAD = 10;
    private static final int CHIN = 152;

    private final float yawDeg;
    private final float pitchDeg;
    private final float rollDeg;

    private HeadPose(float yawDeg, float pitchDeg, float rollDeg) {
        this.yawDeg = yawDeg;
        this.pitchDeg = pitchDeg;
        this.rollDeg = rollDeg;
    }

    /**
     * @param mesh the face's landmarks
     * @return its head pose
     */
    public static HeadPose of(FaceMesh mesh) {
        double[] x = unit(diff(mesh, LEFT_CHEEK, RIGHT_CHEEK));
        double[] y0 = diff(mesh, CHIN, FOREHEAD);
        double along = dot(y0, x);
        double[] y = unit(new double[] {y0[0] - along * x[0], y0[1] - along * x[1], y0[2] - along * x[2]});
        double[] z = {x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0]};
        double pitch = Math.asin(Math.max(-1, Math.min(1, -y[2])));
        double yaw = Math.atan2(x[2], z[2]);
        double roll = Math.atan2(-y[0], y[1]);
        return new HeadPose((float) Math.toDegrees(yaw), (float) Math.toDegrees(pitch), (float) Math.toDegrees(roll));
    }

    /** @return yaw in degrees; positive when the person turns to their own left */
    public float yawDeg() {
        return yawDeg;
    }

    /** @return pitch in degrees; positive when looking up */
    public float pitchDeg() {
        return pitchDeg;
    }

    /** @return roll in degrees; positive when the person's left cheek is lower in the image */
    public float rollDeg() {
        return rollDeg;
    }

    private static double[] diff(FaceMesh m, int to, int from) {
        return new double[] {m.x(to) - m.x(from), m.y(to) - m.y(from), m.z(to) - m.z(from)};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] unit(double[] v) {
        double n = Math.sqrt(dot(v, v));
        return new double[] {v[0] / n, v[1] / n, v[2] / n};
    }
}
