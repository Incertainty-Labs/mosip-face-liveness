package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.PadDetector;
import io.mosip.registration.liveness.api.PadScore;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hard veto for a frozen stream (LIV-015): a still image or a paused video injected as the camera. A live camera
 * always has sensor noise; on 64x48 grey, consecutive webcam frames differed by 0.53 to 2.8 grey levels on average,
 * a re-sent image by 0. The stream is frozen when the whole frame stays within {@value #STILL_DIFF} of the previous
 * one for {@value #FROZEN_MS} ms. Stateful: one instance per session, not thread-safe.
 */
public final class StaticStreamDetector implements PadDetector {

    static final String NAME = "static_stream";
    /** Mean absolute grey-level difference below which two frames count as the same image. */
    static final double STILL_DIFF = 0.15;
    /** How long the stream must stay still. */
    static final long FROZEN_MS = 1000;
    /** How many identical frames, at least, so a slow stream is not judged on two or three. */
    static final int FROZEN_FRAMES = 5;
    private static final int GRID_W = 64;
    private static final int GRID_H = 48;

    private float[] previous;
    private long previousTimestampMs;
    private long previousSequence;
    /** The verdict on the previous frame, returned again when that frame is delivered again. */
    private PadScore last;
    private long stillSinceMs = -1;
    /** Frames in the current still run, its first frame included. */
    private int stillFrames;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String modelVersion() {
        return "rule-1.0";
    }

    @Override
    public PadScore score(FaceFrame frame, FaceObservation observation) {
        if (observation.faceCount() == 0) {
            // A frozen picture of a face always has the face; no face is guidance (LIV-004), so start again
            previous = null;
            stillSinceMs = -1;
            return new PadScore(1.0, false, Collections.<String, Double>emptyMap());
        }
        if (previous != null && frame.sequence() == previousSequence) {
            return last;
        }
        float[] grey = grey(frame);
        double diff = previous == null ? Double.NaN : meanAbsDiff(grey, previous);
        if (diff < STILL_DIFF) {
            if (stillSinceMs < 0) {
                stillSinceMs = previousTimestampMs;
                stillFrames = 1;
            }
            stillFrames++;
        } else {
            stillSinceMs = -1;
            stillFrames = 0;
        }
        previous = grey;
        previousTimestampMs = frame.timestampMs();
        previousSequence = frame.sequence();
        boolean frozen = stillSinceMs >= 0 && frame.timestampMs() - stillSinceMs >= FROZEN_MS
                && stillFrames >= FROZEN_FRAMES;
        Map<String, Double> cues = new LinkedHashMap<String, Double>();
        cues.put(NAME + ".diff", diff);
        last = new PadScore(frozen ? 0.0 : 1.0, frozen, cues);
        return last;
    }

    private static float[] grey(FaceFrame frame) {
        byte[] rgb = frame.rgb();
        float[] luma = new float[frame.width() * frame.height()];
        for (int i = 0, p = 0; i < luma.length; i++, p += 3) {
            luma[i] = 0.299f * (rgb[p] & 0xFF) + 0.587f * (rgb[p + 1] & 0xFF) + 0.114f * (rgb[p + 2] & 0xFF);
        }
        return FrameQuality.areaResize(luma, frame.width(), frame.height(), GRID_W, GRID_H);
    }

    private static double meanAbsDiff(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.abs(a[i] - b[i]);
        }
        return sum / a.length;
    }
}
