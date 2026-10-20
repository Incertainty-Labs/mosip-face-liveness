package io.mosip.registration.liveness.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The outcome of one liveness session. Immutable. {@link #diagnostics()} is for audit and diagnostics only and must
 * never be shown in the normal UI.
 *
 * <p>Final state, decision and error code always combine as in API.md §3.6: PASSED is LIVE with no error; FAILED is
 * ATTACK_SUSPECTED (LIV-008, 014, 015) or INCONCLUSIVE (LIV-004, 007, 009, 010); ERROR is INCONCLUSIVE with a device
 * or engine code; CANCELLED is INCONCLUSIVE with no error.
 */
public final class LivenessResult {

    private final Decision decision;
    private final SessionState finalState;
    private final double passiveScore;
    private final boolean activeUsed;
    private final List<ChallengeOutcome> challenges;
    private final int framesAnalysed;
    private final double measuredFps;
    private final long durationMs;
    private final LivenessErrorCode errorCode;
    private final Map<String, String> diagnostics;
    private final String engineVersion;
    private final Map<String, String> modelVersions;
    private final String evidenceDigest;
    private final String nonce;

    private LivenessResult(Builder b) {
        this.decision = b.decision;
        this.finalState = b.finalState;
        this.passiveScore = b.passiveScore;
        this.activeUsed = b.activeUsed;
        this.challenges = Collections.unmodifiableList(new ArrayList<ChallengeOutcome>(b.challenges));
        this.framesAnalysed = b.framesAnalysed;
        this.measuredFps = b.measuredFps;
        this.durationMs = b.durationMs;
        this.errorCode = b.errorCode;
        this.diagnostics = Collections.unmodifiableMap(new LinkedHashMap<String, String>(b.diagnostics));
        this.engineVersion = b.engineVersion;
        this.modelVersions = Collections.unmodifiableMap(new LinkedHashMap<String, String>(b.modelVersions));
        this.evidenceDigest = b.evidenceDigest;
        this.nonce = b.nonce;
    }

    /**
     * @param finalState how the session ended; must be a final state
     * @param decision   the verdict
     * @return a new builder
     */
    public static Builder builder(SessionState finalState, Decision decision) {
        return new Builder(finalState, decision);
    }

    /** @return the verdict */
    public Decision decision() {
        return decision;
    }

    /** @return how the session ended: PASSED, FAILED, ERROR or CANCELLED */
    public SessionState finalState() {
        return finalState;
    }

    /** @return fused passive score 0..1 (median over the window); for audit only */
    public double passiveScore() {
        return passiveScore;
    }

    /** @return whether active challenges were used */
    public boolean activeUsed() {
        return activeUsed;
    }

    /** @return each challenge run, in order, ending with NEUTRAL_HOLD when reached; unmodifiable */
    public List<ChallengeOutcome> challenges() {
        return challenges;
    }

    /** @return number of frames analysed */
    public int framesAnalysed() {
        return framesAnalysed;
    }

    /** @return frame rate measured during the session */
    public double measuredFps() {
        return measuredFps;
    }

    /** @return time from the first frame to the result, in milliseconds */
    public long durationMs() {
        return durationMs;
    }

    /** @return the error, or {@code null} when LIVE or CANCELLED */
    public LivenessErrorCode errorCode() {
        return errorCode;
    }

    /** @return cue scores and other details for audit and diagnostics; never shown in the UI; unmodifiable */
    public Map<String, String> diagnostics() {
        return diagnostics;
    }

    /** @return the engine name and version */
    public String engineVersion() {
        return engineVersion;
    }

    /** @return model name to version; unmodifiable */
    public Map<String, String> modelVersions() {
        return modelVersions;
    }

    /**
     * @return hex SHA-256 of nonce, challenge transcript and frame hashes; the binder later extends it with the
     *         signed image hash. {@code null} when the engine produces none (the stub engine)
     */
    public String evidenceDigest() {
        return evidenceDigest;
    }

    /**
     * @return the session's random nonce, 32 lowercase hex characters. Send it as the capture request's SBI
     *         {@code transactionId} and check the signed echo with {@code CaptureFreshness}
     */
    public String nonce() {
        return nonce;
    }

    /** @return compact list for audit and packet, for example {@code TURN_LEFT:PASSED,NEUTRAL_HOLD:PASSED} */
    public String challengeSummary() {
        StringBuilder sb = new StringBuilder();
        for (ChallengeOutcome c : challenges) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(c.type().name()).append(':').append(c.outcome().name());
        }
        return sb.toString();
    }

    /** Mutable builder for {@link LivenessResult}. Not thread-safe. */
    public static final class Builder {
        private final SessionState finalState;
        private final Decision decision;
        private double passiveScore;
        private boolean activeUsed;
        private List<ChallengeOutcome> challenges = Collections.emptyList();
        private int framesAnalysed;
        private double measuredFps;
        private long durationMs;
        private LivenessErrorCode errorCode;
        private Map<String, String> diagnostics = Collections.emptyMap();
        private String engineVersion = "";
        private Map<String, String> modelVersions = Collections.emptyMap();
        private String evidenceDigest;
        private String nonce;

        private Builder(SessionState finalState, Decision decision) {
            if (finalState == null || !finalState.isFinal() || decision == null) {
                throw new IllegalArgumentException("a final state and a decision are required");
            }
            this.finalState = finalState;
            this.decision = decision;
        }

        /** @param value fused passive score @return this builder */
        public Builder passiveScore(double value) {
            this.passiveScore = value;
            return this;
        }

        /** @param value whether challenges were used @return this builder */
        public Builder activeUsed(boolean value) {
            this.activeUsed = value;
            return this;
        }

        /** @param value challenge outcomes in order; copied @return this builder */
        public Builder challenges(List<ChallengeOutcome> value) {
            this.challenges = value;
            return this;
        }

        /** @param count frames analysed @param fps measured frame rate @return this builder */
        public Builder frames(int count, double fps) {
            this.framesAnalysed = count;
            this.measuredFps = fps;
            return this;
        }

        /** @param value duration in milliseconds @return this builder */
        public Builder durationMs(long value) {
            this.durationMs = value;
            return this;
        }

        /** @param value the error, or null @return this builder */
        public Builder errorCode(LivenessErrorCode value) {
            this.errorCode = value;
            return this;
        }

        /** @param value diagnostics map; copied @return this builder */
        public Builder diagnostics(Map<String, String> value) {
            this.diagnostics = value;
            return this;
        }

        /** @param engine engine name and version @param models model versions; copied @return this builder */
        public Builder versions(String engine, Map<String, String> models) {
            this.engineVersion = engine;
            this.modelVersions = models;
            return this;
        }

        /** @param value hex SHA-256 evidence digest, or null @return this builder */
        public Builder evidenceDigest(String value) {
            this.evidenceDigest = value;
            return this;
        }

        /** @param value the session nonce @return this builder */
        public Builder nonce(String value) {
            this.nonce = value;
            return this;
        }

        /** @return the immutable result */
        public LivenessResult build() {
            return new LivenessResult(this);
        }
    }
}
