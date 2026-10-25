package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.PadDetector;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The passive PAD decision of one session (TECHNICAL_DESIGN §5.3): each frame's score is the weighted mean of the
 * cues' live probabilities, and the session score is the median over the last {@code passive.min.frames} frames.
 * The weights come from {@code pad.weights}, renormalised over the cues actually loaded. Only frames that pass the
 * {@link QualityGate} are scored; the others are skipped without restarting the window. A cue without a weight (the
 * {@link StaticStreamDetector}) only vetoes: it sees every frame, and its veto stays for the session. One instance per
 * session, used from the liveness thread only; not thread-safe.
 */
public final class PassiveAssessor {

    private final List<PadDetector> weighted = new ArrayList<PadDetector>();
    private final List<Double> weights = new ArrayList<Double>();
    /** Cues without a weight: they only veto, and see every frame. */
    private final List<PadDetector> vetoOnly = new ArrayList<PadDetector>();
    private final LivenessPolicy policy;
    private final int windowSize;
    private final Deque<Double> window = new ArrayDeque<Double>();
    /** Each weighted cue's own live probabilities over the same window, for {@link #cues()}. */
    private final List<Deque<Double>> cueWindows = new ArrayList<Deque<Double>>();
    private String vetoedBy;
    private Map<String, Double> lastScores = Collections.emptyMap();

    /**
     * @param detectors the loaded cues
     * @param policy    the session's policy: {@code pad.weights}, {@code passive.min.frames} and the quality limits
     * @throws IllegalArgumentException if no detector has a weight in {@code pad.weights}
     */
    public PassiveAssessor(List<PadDetector> detectors, LivenessPolicy policy) {
        for (PadDetector detector : detectors) {
            Double weight = policy.padWeights().get(detector.name());
            if (weight != null) {
                weighted.add(detector);
                weights.add(weight);
                cueWindows.add(new ArrayDeque<Double>());
            } else {
                vetoOnly.add(detector);
            }
        }
        double sum = 0;
        for (double w : weights) {
            sum += w;
        }
        if (!(sum > 0)) {
            throw new IllegalArgumentException("No loaded passive cue has a weight in pad.weights "
                    + policy.padWeights());
        }
        for (int i = 0; i < weights.size(); i++) {
            weights.set(i, weights.get(i) / sum);
        }
        this.policy = policy;
        this.windowSize = policy.passiveMinFrames();
    }

    /**
     * Scores one frame.
     *
     * @param frame       the frame
     * @param observation the analyzer's output for it
     * @return this frame's fused live score, 0..1, or NaN when the quality gate rejects the frame (not scored)
     */
    public double add(FaceFrame frame, FaceObservation observation) {
        return add(frame, observation, true);
    }

    /**
     * Scores one frame, or only checks it.
     *
     * @param frame       the frame
     * @param observation the analyzer's output for it
     * @param score       false to run only the veto cues, for a frame where the face detector did not run: the weighted
     *                    cues need a detector box
     * @return this frame's fused live score, or NaN when not scored
     */
    public double add(FaceFrame frame, FaceObservation observation, boolean score) {
        lastScores = Collections.emptyMap();
        for (PadDetector detector : vetoOnly) {
            if (detector.score(frame, observation).veto() && vetoedBy == null) {
                vetoedBy = detector.name();
            }
        }
        if (!score || QualityGate.check(observation, frame.width(), frame.height(), policy) != null) {
            return Double.NaN;
        }
        double fused = 0;
        Map<String, Double> scores = new LinkedHashMap<String, Double>();
        for (int i = 0; i < weighted.size(); i++) {
            double live = weighted.get(i).score(frame, observation).liveProbability();
            push(cueWindows.get(i), live);
            fused += weights.get(i) * live;
            scores.put(weighted.get(i).name(), live);
        }
        lastScores = Collections.unmodifiableMap(scores);
        push(window, fused);
        return fused;
    }

    /** @return each weighted cue's live probability on the last frame added, by name; empty when it was not scored */
    public Map<String, Double> lastScores() {
        return lastScores;
    }

    /** @return each weighted cue's median over the window, by name; diagnostics and audit only; empty before any */
    public Map<String, Double> cues() {
        Map<String, Double> cues = new LinkedHashMap<String, Double>();
        for (int i = 0; i < weighted.size(); i++) {
            if (!cueWindows.get(i).isEmpty()) {
                cues.put(weighted.get(i).name(), median(cueWindows.get(i)));
            }
        }
        return cues;
    }

    private void push(Deque<Double> values, double value) {
        values.addLast(value);
        if (values.size() > windowSize) {
            values.removeFirst();
        }
    }

    /**
     * @return the name of the cue that vetoed, for example {@code static_stream} (LIV-015), or {@code null}; once set
     *         it stays for the session
     */
    public String veto() {
        return vetoedBy;
    }

    /** @return the median fused score over the window, or NaN until it holds {@code passive.min.frames} frames */
    public double score() {
        if (window.size() < windowSize) {
            return Double.NaN;
        }
        return median(window);
    }

    private static double median(Iterable<Double> values) {
        List<Double> sorted = new ArrayList<Double>();
        for (Double v : values) {
            sorted.add(v);
        }
        Double[] a = sorted.toArray(new Double[0]);
        Arrays.sort(a);
        int mid = a.length / 2;
        return a.length % 2 == 1 ? a[mid] : (a[mid - 1] + a[mid]) / 2;
    }
}
