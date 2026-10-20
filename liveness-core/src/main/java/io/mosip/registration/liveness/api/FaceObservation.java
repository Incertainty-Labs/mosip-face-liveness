package io.mosip.registration.liveness.api;

/**
 * What the face analyzer saw in one frame. Immutable. Angles are in degrees; 0..1 values are relative measures.
 * Values the analyzer cannot measure are {@code NaN}.
 */
public final class FaceObservation {

    private final int faceCount;
    private final Rect box;
    private final float[] landmarks;
    private final float yawDeg;
    private final float pitchDeg;
    private final float rollDeg;
    private final float eyeOpenLeft;
    private final float eyeOpenRight;
    private final float smile;
    private final float mouthOpen;
    private final float sharpness;
    private final float brightness;

    private FaceObservation(Builder b) {
        this.faceCount = b.faceCount;
        this.box = b.box;
        this.landmarks = b.landmarks.clone();
        this.yawDeg = b.yawDeg;
        this.pitchDeg = b.pitchDeg;
        this.rollDeg = b.rollDeg;
        this.eyeOpenLeft = b.eyeOpenLeft;
        this.eyeOpenRight = b.eyeOpenRight;
        this.smile = b.smile;
        this.mouthOpen = b.mouthOpen;
        this.sharpness = b.sharpness;
        this.brightness = b.brightness;
    }

    /** @return a builder with no face and every measure unknown */
    public static Builder builder() {
        return new Builder();
    }

    /** @return number of faces found */
    public int faceCount() {
        return faceCount;
    }

    /** @return the largest face, or {@code null} when no face was found */
    public Rect box() {
        return box;
    }

    /** @return a copy of the landmark coordinates as x,y pairs in frame pixels (5 points or dense) */
    public float[] landmarks() {
        return landmarks.clone();
    }

    /** @return head yaw; positive means the user turned to their own left */
    public float yawDeg() {
        return yawDeg;
    }

    /** @return head pitch; positive means looking up */
    public float pitchDeg() {
        return pitchDeg;
    }

    /** @return head roll */
    public float rollDeg() {
        return rollDeg;
    }

    /** @return left eye openness 0..1, or NaN without eye landmarks */
    public float eyeOpenLeft() {
        return eyeOpenLeft;
    }

    /** @return right eye openness 0..1, or NaN without eye landmarks */
    public float eyeOpenRight() {
        return eyeOpenRight;
    }

    /** @return smile 0..1 */
    public float smile() {
        return smile;
    }

    /** @return mouth openness 0..1 */
    public float mouthOpen() {
        return mouthOpen;
    }

    /** @return sharpness 0..1 (normalised Laplacian variance) */
    public float sharpness() {
        return sharpness;
    }

    /** @return mean luma 0..1 */
    public float brightness() {
        return brightness;
    }

    /** @return whether eye openness was measured */
    public boolean supportsEyes() {
        return !Float.isNaN(eyeOpenLeft) && !Float.isNaN(eyeOpenRight);
    }

    /** Mutable builder for {@link FaceObservation}. Not thread-safe. */
    public static final class Builder {
        private int faceCount;
        private Rect box;
        private float[] landmarks = new float[0];
        private float yawDeg = Float.NaN;
        private float pitchDeg = Float.NaN;
        private float rollDeg = Float.NaN;
        private float eyeOpenLeft = Float.NaN;
        private float eyeOpenRight = Float.NaN;
        private float smile = Float.NaN;
        private float mouthOpen = Float.NaN;
        private float sharpness = Float.NaN;
        private float brightness = Float.NaN;

        private Builder() {
        }

        /** @param count faces found @param largest the largest face box, or null @return this builder */
        public Builder faces(int count, Rect largest) {
            this.faceCount = count;
            this.box = largest;
            return this;
        }

        /** @param xy landmark x,y pairs; copied @return this builder */
        public Builder landmarks(float[] xy) {
            this.landmarks = xy == null ? new float[0] : xy.clone();
            return this;
        }

        /** @param yaw degrees @param pitch degrees @param roll degrees @return this builder */
        public Builder pose(float yaw, float pitch, float roll) {
            this.yawDeg = yaw;
            this.pitchDeg = pitch;
            this.rollDeg = roll;
            return this;
        }

        /** @param left 0..1 @param right 0..1 @return this builder */
        public Builder eyesOpen(float left, float right) {
            this.eyeOpenLeft = left;
            this.eyeOpenRight = right;
            return this;
        }

        /** @param smileValue 0..1 @param mouthOpenValue 0..1 @return this builder */
        public Builder expression(float smileValue, float mouthOpenValue) {
            this.smile = smileValue;
            this.mouthOpen = mouthOpenValue;
            return this;
        }

        /** @param sharpnessValue 0..1 @param brightnessValue 0..1 @return this builder */
        public Builder quality(float sharpnessValue, float brightnessValue) {
            this.sharpness = sharpnessValue;
            this.brightness = brightnessValue;
            return this;
        }

        /** @return the immutable observation */
        public FaceObservation build() {
            return new FaceObservation(this);
        }
    }
}
