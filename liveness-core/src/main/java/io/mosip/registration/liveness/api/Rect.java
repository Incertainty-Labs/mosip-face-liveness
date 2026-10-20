package io.mosip.registration.liveness.api;

/** An axis-aligned box in frame pixels. Immutable. */
public final class Rect {

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    /**
     * @param x      left edge
     * @param y      top edge
     * @param width  width, zero or more
     * @param height height, zero or more
     */
    public Rect(int x, int y, int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("width and height must not be negative");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** @return left edge */
    public int x() {
        return x;
    }

    /** @return top edge */
    public int y() {
        return y;
    }

    /** @return width */
    public int width() {
        return width;
    }

    /** @return height */
    public int height() {
        return height;
    }
}
