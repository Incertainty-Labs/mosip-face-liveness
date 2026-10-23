package io.mosip.registration.liveness.engine.onnx;

/**
 * MediaPipe Face Mesh V2's 478 landmarks (468 face points and 10 iris points) in frame pixels. x and y are image
 * coordinates; z is relative depth on the same scale as x (smaller is closer to the camera). Immutable.
 */
public final class FaceMesh {

    /** Number of landmarks. */
    public static final int POINTS = 478;

    private final float[] xyz;
    private final float presence;

    FaceMesh(float[] xyz, float presence) {
        if (xyz.length != POINTS * 3) {
            throw new IllegalArgumentException("Expected " + POINTS * 3 + " values, got " + xyz.length);
        }
        this.xyz = xyz.clone();
        this.presence = presence;
    }

    /** @return number of landmarks (478) */
    public int size() {
        return POINTS;
    }

    /** @param i landmark index @return its x in frame pixels */
    public float x(int i) {
        return xyz[i * 3];
    }

    /** @param i landmark index @return its y in frame pixels */
    public float y(int i) {
        return xyz[i * 3 + 1];
    }

    /** @param i landmark index @return its relative depth, same scale as x */
    public float z(int i) {
        return xyz[i * 3 + 2];
    }

    /** @return the model's confidence that the crop holds a face, 0..1 */
    public float presence() {
        return presence;
    }
}
