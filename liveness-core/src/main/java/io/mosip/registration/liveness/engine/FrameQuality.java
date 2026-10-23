package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.FaceFrame;

/**
 * Image quality inside the face box: brightness (mean luma, 0..1) and sharpness (variance of the 3x3 Laplacian on the
 * face scaled to {@value #WORK_WIDTH} px wide, so it barely depends on distance, mapped to 0..1 as v / (v + 50)). Both
 * are measurements only; the pass marks come from the policy. The box is clipped to the frame. Immutable.
 */
public final class FrameQuality {

    /** Face width the sharpness is measured at. */
    static final int WORK_WIDTH = 128;
    /** Laplacian variance that maps to 0.5. Sharp faces measured 90 to 600, faces blurred by sigma 1.5 to 3, 6 to 18. */
    private static final double SHARPNESS_HALF = 50.0;

    private final float sharpness;
    private final float brightness;

    private FrameQuality(float sharpness, float brightness) {
        this.sharpness = sharpness;
        this.brightness = brightness;
    }

    /**
     * @param frame the frame
     * @param x     face box left
     * @param y     face box top
     * @param w     face box width
     * @param h     face box height
     * @return the quality inside the box; 0 sharpness and brightness when the box misses the frame
     */
    public static FrameQuality of(FaceFrame frame, float x, float y, float w, float h) {
        int x0 = Math.max(0, Math.round(x));
        int y0 = Math.max(0, Math.round(y));
        int x1 = Math.min(frame.width(), Math.round(x + w));
        int y1 = Math.min(frame.height(), Math.round(y + h));
        if (x1 - x0 < 3 || y1 - y0 < 3) {
            return new FrameQuality(0f, 0f);
        }
        float[] luma = luma(frame, x0, y0, x1, y1);
        int cw = x1 - x0;
        int ch = y1 - y0;
        double sum = 0;
        for (float v : luma) {
            sum += v;
        }
        float brightness = (float) (sum / luma.length / 255.0);

        int ww = WORK_WIDTH;
        int wh = Math.max(3, Math.round((float) ch * WORK_WIDTH / cw));
        float[] small = areaResize(luma, cw, ch, ww, wh);
        double variance = laplacianVariance(small, ww, wh);
        return new FrameQuality((float) (variance / (variance + SHARPNESS_HALF)), brightness);
    }

    /** @return sharpness 0..1 (normalised Laplacian variance) */
    public float sharpness() {
        return sharpness;
    }

    /** @return brightness 0..1 (mean luma) */
    public float brightness() {
        return brightness;
    }

    private static float[] luma(FaceFrame frame, int x0, int y0, int x1, int y1) {
        byte[] rgb = frame.rgb();
        int w = frame.width();
        float[] out = new float[(x1 - x0) * (y1 - y0)];
        int k = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int i = (y * w + x) * 3;
                out[k++] = 0.299f * (rgb[i] & 0xFF) + 0.587f * (rgb[i + 1] & 0xFF) + 0.114f * (rgb[i + 2] & 0xFF);
            }
        }
        return out;
    }

    /** Area averaging: each output pixel is the mean of the source pixels its footprint covers, weighted by overlap. */
    static float[] areaResize(float[] src, int sw, int sh, int dw, int dh) {
        float[] out = new float[dw * dh];
        double sx = (double) sw / dw;
        double sy = (double) sh / dh;
        for (int oy = 0; oy < dh; oy++) {
            double fy0 = oy * sy;
            double fy1 = fy0 + sy;
            for (int ox = 0; ox < dw; ox++) {
                double fx0 = ox * sx;
                double fx1 = fx0 + sx;
                double acc = 0;
                double area = 0;
                for (int y = (int) fy0; y < Math.min(sh, (int) Math.ceil(fy1)); y++) {
                    double wy = Math.min(fy1, y + 1) - Math.max(fy0, y);
                    for (int x = (int) fx0; x < Math.min(sw, (int) Math.ceil(fx1)); x++) {
                        double wgt = wy * (Math.min(fx1, x + 1) - Math.max(fx0, x));
                        acc += wgt * src[y * sw + x];
                        area += wgt;
                    }
                }
                out[oy * dw + ox] = (float) (acc / area);
            }
        }
        return out;
    }

    /** Variance of the 4-neighbour Laplacian over the interior pixels. */
    private static double laplacianVariance(float[] g, int w, int h) {
        double sum = 0;
        double sumSq = 0;
        int n = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                double lap = g[i - 1] + g[i + 1] + g[i - w] + g[i + w] - 4 * g[i];
                sum += lap;
                sumSq += lap * lap;
                n++;
            }
        }
        double mean = sum / n;
        return sumSq / n - mean * mean;
    }
}
