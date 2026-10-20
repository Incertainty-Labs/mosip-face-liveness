package io.mosip.registration.liveness.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One presentation-attack cue's verdict on one frame. Immutable. */
public final class PadScore {

    private final double liveProbability;
    private final boolean veto;
    private final Map<String, Double> cues;

    /**
     * @param liveProbability probability 0..1 that the frame shows a live face
     * @param veto            {@code true} means "attack, stop now" regardless of the fused score
     * @param cues            named cue values for audit and diagnostics only; copied
     * @throws IllegalArgumentException if the probability is outside 0..1
     */
    public PadScore(double liveProbability, boolean veto, Map<String, Double> cues) {
        if (!(liveProbability >= 0.0 && liveProbability <= 1.0)) {
            throw new IllegalArgumentException("liveProbability must be within 0..1: " + liveProbability);
        }
        this.liveProbability = liveProbability;
        this.veto = veto;
        this.cues = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(cues));
    }

    /** @return probability 0..1 that the frame shows a live face */
    public double liveProbability() {
        return liveProbability;
    }

    /** @return whether this cue alone decides "attack" */
    public boolean veto() {
        return veto;
    }

    /** @return named cue values; diagnostics only, never shown in the UI; unmodifiable */
    public Map<String, Double> cues() {
        return cues;
    }
}
