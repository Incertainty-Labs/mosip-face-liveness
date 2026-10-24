package io.mosip.registration.liveness.api;

/**
 * One decoded frame from any {@link FrameSource}: packed 8-bit RGB, row-major, no alpha. Decoding (JPEG, YUV,
 * JPEG 2000) happens in the source adapter, never in the engine.
 *
 * <p>The frame is upright and not mirrored, as the camera sees the person. Every model assumes an upright face, and
 * head pose depends on it: in a mirrored frame yaw changes sign, so "turn left" would be checked as "turn right". A
 * client may mirror its on-screen preview, but never the frames it hands to the engine.
 *
 * <p>Immutable by contract. The pixel buffer is not copied, to keep per-frame cost low on Android; producers hand
 * over a fresh buffer and nobody may modify {@link #rgb()} afterwards.
 */
public final class FaceFrame {

    private final int width;
    private final int height;
    private final byte[] rgb;
    private final long timestampMs;
    private final long sequence;
    private final String sourceId;

    /**
     * Creates a frame.
     *
     * @param width       width in pixels, greater than zero
     * @param height      height in pixels, greater than zero
     * @param rgb         packed R,G,B bytes, row-major; length must be {@code width * height * 3}; not copied
     * @param timestampMs capture time on the source's monotonic clock, in milliseconds
     * @param sequence    strictly increasing number per source
     * @param sourceId    the producing {@link FrameSource#id()}
     * @throws IllegalArgumentException if a size is not positive, the buffer is missing or the wrong length, or the
     *                                  source id is missing
     */
    public FaceFrame(int width, int height, byte[] rgb, long timestampMs, long sequence, String sourceId) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Frame size must be positive: " + width + "x" + height);
        }
        if (rgb == null || rgb.length != (long) width * height * 3) {
            throw new IllegalArgumentException("RGB buffer must hold " + width + "x" + height + "x3 bytes");
        }
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId must not be null");
        }
        this.width = width;
        this.height = height;
        this.rgb = rgb;
        this.timestampMs = timestampMs;
        this.sequence = sequence;
        this.sourceId = sourceId;
    }

    /** @return width in pixels */
    public int width() {
        return width;
    }

    /** @return height in pixels */
    public int height() {
        return height;
    }

    /** @return the packed RGB pixels; read-only by contract */
    public byte[] rgb() {
        return rgb;
    }

    /** @return capture time on the source's monotonic clock, in milliseconds */
    public long timestampMs() {
        return timestampMs;
    }

    /** @return the frame number, strictly increasing per source */
    public long sequence() {
        return sequence;
    }

    /** @return the id of the source that produced this frame */
    public String sourceId() {
        return sourceId;
    }
}
