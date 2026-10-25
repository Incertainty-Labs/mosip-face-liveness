package io.mosip.registration.liveness.binding;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.Rect;
import java.util.Arrays;

/**
 * A 64-bit perceptual hash (pHash) of a face crop: grey, shrunk to 32x32 by area averaging, 2-D DCT, then one bit per
 * coefficient of the lowest 8x8 frequencies, set when it is above their median (the DC term left out of the median).
 * Similar images give hashes a few bits apart.
 */
final class PerceptualHash {

    private static final int N = 32;
    private static final int K = 8;

    private PerceptualHash() {
    }

    /**
     * @param frame the frame
     * @param box   the face box, clipped to the frame
     * @return the hash; 0 for an empty box
     */
    static long of(FaceFrame frame, Rect box) {
        int x0 = Math.max(0, box.x());
        int y0 = Math.max(0, box.y());
        int x1 = Math.min(frame.width(), box.x() + box.width());
        int y1 = Math.min(frame.height(), box.y() + box.height());
        if (x1 - x0 < 2 || y1 - y0 < 2) {
            return 0;
        }
        double[] grey = shrink(frame, x0, y0, x1, y1);
        double[] dct = dct(grey);
        double[] low = new double[K * K];
        for (int v = 0; v < K; v++) {
            for (int u = 0; u < K; u++) {
                low[v * K + u] = dct[v * N + u];
            }
        }
        double[] ac = Arrays.copyOfRange(low, 1, low.length);
        Arrays.sort(ac);
        double median = (ac[ac.length / 2 - 1] + ac[ac.length / 2]) / 2;
        long hash = 0;
        for (int i = 0; i < low.length; i++) {
            if (low[i] > median) {
                hash |= 1L << i;
            }
        }
        return hash;
    }

    /** Area-averages the crop's luma to 32x32. */
    private static double[] shrink(FaceFrame frame, int x0, int y0, int x1, int y1) {
        byte[] rgb = frame.rgb();
        int w = frame.width();
        double[] sum = new double[N * N];
        int[] count = new int[N * N];
        for (int y = y0; y < y1; y++) {
            int cy = (y - y0) * N / (y1 - y0);
            for (int x = x0; x < x1; x++) {
                int cx = (x - x0) * N / (x1 - x0);
                int i = (y * w + x) * 3;
                sum[cy * N + cx] += 0.299 * (rgb[i] & 0xFF) + 0.587 * (rgb[i + 1] & 0xFF) + 0.114 * (rgb[i + 2] & 0xFF);
                count[cy * N + cx]++;
            }
        }
        for (int i = 0; i < sum.length; i++) {
            sum[i] = count[i] == 0 ? 0 : sum[i] / count[i];
        }
        return sum;
    }

    /** Separable 2-D DCT-II of an N x N block. */
    private static double[] dct(double[] in) {
        double[] cos = new double[N * N];
        for (int k = 0; k < N; k++) {
            for (int n = 0; n < N; n++) {
                cos[k * N + n] = Math.cos(Math.PI / N * (n + 0.5) * k);
            }
        }
        double[] rows = new double[N * N];
        for (int y = 0; y < N; y++) {
            for (int k = 0; k < N; k++) {
                double s = 0;
                for (int n = 0; n < N; n++) {
                    s += in[y * N + n] * cos[k * N + n];
                }
                rows[y * N + k] = s;
            }
        }
        double[] out = new double[N * N];
        for (int x = 0; x < N; x++) {
            for (int k = 0; k < N; k++) {
                double s = 0;
                for (int n = 0; n < N; n++) {
                    s += rows[n * N + x] * cos[k * N + n];
                }
                out[k * N + x] = s;
            }
        }
        return out;
    }
}
