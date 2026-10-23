package io.mosip.registration.liveness.engine.onnx;

/**
 * One face found by {@link YuNetDetector}, in the frame's pixel coordinates. Immutable.
 *
 * <p>The five landmarks are, in order: right eye, left eye, nose tip, right mouth corner, left mouth corner (right
 * and left as seen from the person, so the right eye is on the image's left).
 */
public final class FaceDetection {

    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private final float[] landmarks;
    private final float score;

    FaceDetection(float x, float y, float width, float height, float[] landmarks, float score) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.landmarks = landmarks.clone();
        this.score = score;
    }

    /** @return left edge of the face box */
    public float x() {
        return x;
    }

    /** @return top edge of the face box */
    public float y() {
        return y;
    }

    /** @return face box width */
    public float width() {
        return width;
    }

    /** @return face box height */
    public float height() {
        return height;
    }

    /** @return the five landmarks as x0, y0, ... x4, y4 (a copy) */
    public float[] landmarks() {
        return landmarks.clone();
    }

    /** @return detector confidence 0..1 */
    public float score() {
        return score;
    }
}
