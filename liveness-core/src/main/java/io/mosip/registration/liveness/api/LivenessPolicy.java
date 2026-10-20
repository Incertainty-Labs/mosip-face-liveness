package io.mosip.registration.liveness.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The resolved liveness settings for one workflow. Immutable; build it with {@link #builder(Workflow)}, normally
 * through {@code LivenessConfigLoader}.
 */
public final class LivenessPolicy {

    private final Workflow workflow;
    private final boolean enabled;
    private final LivenessMode mode;
    private final double passiveThreshold;
    private final int passiveMinFrames;
    private final boolean activeEnabled;
    private final List<ChallengeType> challengePool;
    private final int challengeMinCount;
    private final int challengeMaxCount;
    private final long challengeTimeoutMs;
    private final long sessionTimeoutMs;
    private final double blinkMinFps;
    private final int maxRetries;
    private final MaxRetriesAction onMaxRetries;
    private final boolean bindCaptureEnabled;
    private final double bindMinSimilarity;
    private final List<String> warnings;

    private LivenessPolicy(Builder b) {
        this.workflow = b.workflow;
        this.enabled = b.enabled;
        this.mode = b.mode;
        this.passiveThreshold = b.passiveThreshold;
        this.passiveMinFrames = b.passiveMinFrames;
        this.activeEnabled = b.activeEnabled;
        this.challengePool = Collections.unmodifiableList(new ArrayList<ChallengeType>(b.challengePool));
        this.challengeMinCount = b.challengeMinCount;
        this.challengeMaxCount = b.challengeMaxCount;
        this.challengeTimeoutMs = b.challengeTimeoutMs;
        this.sessionTimeoutMs = b.sessionTimeoutMs;
        this.blinkMinFps = b.blinkMinFps;
        this.maxRetries = b.maxRetries;
        this.onMaxRetries = b.onMaxRetries;
        this.bindCaptureEnabled = b.bindCaptureEnabled;
        this.bindMinSimilarity = b.bindMinSimilarity;
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(b.warnings));
    }

    /**
     * Starts a builder for the given workflow. Every setting must be supplied; there are no defaults here.
     *
     * @param workflow the workflow this policy applies to
     * @return a new builder
     */
    public static Builder builder(Workflow workflow) {
        return new Builder(workflow);
    }

    /** @return the workflow this policy applies to */
    public Workflow workflow() {
        return workflow;
    }

    /** @return whether liveness is checked at all */
    public boolean enabled() {
        return enabled;
    }

    /** @return how the session decides liveness */
    public LivenessMode mode() {
        return mode;
    }

    /** @return fused passive score (0..1) needed to skip the active challenges */
    public double passiveThreshold() {
        return passiveThreshold;
    }

    /** @return frames scored before the passive decision */
    public int passiveMinFrames() {
        return passiveMinFrames;
    }

    /** @return whether a low passive score may escalate to active challenges */
    public boolean activeEnabled() {
        return activeEnabled;
    }

    /** @return challenge types the generator may choose from, in configured order; unmodifiable */
    public List<ChallengeType> challengePool() {
        return challengePool;
    }

    /** @return minimum number of challenges per session, excluding the final neutral hold */
    public int challengeMinCount() {
        return challengeMinCount;
    }

    /** @return maximum number of challenges per session, excluding the final neutral hold */
    public int challengeMaxCount() {
        return challengeMaxCount;
    }

    /** @return time allowed for each challenge, in milliseconds */
    public long challengeTimeoutMs() {
        return challengeTimeoutMs;
    }

    /** @return time allowed for a whole session, in milliseconds */
    public long sessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    /** @return measured frame rate below which BLINK is dropped from the pool */
    public double blinkMinFps() {
        return blinkMinFps;
    }

    /** @return attempts allowed before the workflow reaches its limit */
    public int maxRetries() {
        return maxRetries;
    }

    /** @return what happens when the attempt limit is reached */
    public MaxRetriesAction onMaxRetries() {
        return onMaxRetries;
    }

    /** @return whether the signed device capture must be bound to the analysed frames */
    public boolean bindCaptureEnabled() {
        return bindCaptureEnabled;
    }

    /** @return minimum binder similarity (0..1) for the capture to count as bound */
    public double bindMinSimilarity() {
        return bindMinSimilarity;
    }

    /** @return problems found while building the policy, such as invalid values replaced by defaults; unmodifiable */
    public List<String> warnings() {
        return warnings;
    }

    /** Mutable builder for {@link LivenessPolicy}. Not thread-safe. */
    public static final class Builder {
        private final Workflow workflow;
        private boolean enabled;
        private LivenessMode mode;
        private double passiveThreshold;
        private int passiveMinFrames;
        private boolean activeEnabled;
        private List<ChallengeType> challengePool = Collections.emptyList();
        private int challengeMinCount;
        private int challengeMaxCount;
        private long challengeTimeoutMs;
        private long sessionTimeoutMs;
        private double blinkMinFps;
        private int maxRetries;
        private MaxRetriesAction onMaxRetries;
        private boolean bindCaptureEnabled;
        private double bindMinSimilarity;
        private final List<String> warnings = new ArrayList<String>();

        private Builder(Workflow workflow) {
            if (workflow == null) {
                throw new IllegalArgumentException("workflow must not be null");
            }
            this.workflow = workflow;
        }

        /** @param value see {@link LivenessPolicy#enabled()} @return this builder */
        public Builder enabled(boolean value) {
            this.enabled = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#mode()} @return this builder */
        public Builder mode(LivenessMode value) {
            this.mode = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#passiveThreshold()} @return this builder */
        public Builder passiveThreshold(double value) {
            this.passiveThreshold = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#passiveMinFrames()} @return this builder */
        public Builder passiveMinFrames(int value) {
            this.passiveMinFrames = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#activeEnabled()} @return this builder */
        public Builder activeEnabled(boolean value) {
            this.activeEnabled = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#challengePool()} @return this builder */
        public Builder challengePool(List<ChallengeType> value) {
            this.challengePool = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#challengeMinCount()} @return this builder */
        public Builder challengeMinCount(int value) {
            this.challengeMinCount = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#challengeMaxCount()} @return this builder */
        public Builder challengeMaxCount(int value) {
            this.challengeMaxCount = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#challengeTimeoutMs()} @return this builder */
        public Builder challengeTimeoutMs(long value) {
            this.challengeTimeoutMs = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#sessionTimeoutMs()} @return this builder */
        public Builder sessionTimeoutMs(long value) {
            this.sessionTimeoutMs = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#blinkMinFps()} @return this builder */
        public Builder blinkMinFps(double value) {
            this.blinkMinFps = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#maxRetries()} @return this builder */
        public Builder maxRetries(int value) {
            this.maxRetries = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#onMaxRetries()} @return this builder */
        public Builder onMaxRetries(MaxRetriesAction value) {
            this.onMaxRetries = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#bindCaptureEnabled()} @return this builder */
        public Builder bindCaptureEnabled(boolean value) {
            this.bindCaptureEnabled = value;
            return this;
        }

        /** @param value see {@link LivenessPolicy#bindMinSimilarity()} @return this builder */
        public Builder bindMinSimilarity(double value) {
            this.bindMinSimilarity = value;
            return this;
        }

        /** @param message a problem to report through {@link LivenessPolicy#warnings()} @return this builder */
        public Builder warning(String message) {
            this.warnings.add(message);
            return this;
        }

        /** @return the immutable policy */
        public LivenessPolicy build() {
            return new LivenessPolicy(this);
        }
    }
}
