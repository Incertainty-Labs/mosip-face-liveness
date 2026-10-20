package io.mosip.registration.liveness.config;

import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.LivenessMode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.MaxRetriesAction;
import io.mosip.registration.liveness.api.Workflow;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a {@link LivenessPolicy} for one workflow from the flat registration-client configuration map, using the
 * keys under {@value #PREFIX} documented in API.md §5.
 *
 * <p>Each setting resolves as: per-workflow key {@code policy.<code>.<setting>} (only for settings that allow it),
 * then the global key, then the named default constant. A value that cannot be used is replaced by the default and
 * reported in {@link LivenessPolicy#warnings()}, so a typo never disables liveness silently.
 */
public final class LivenessConfigLoader {

    /** Prefix shared by every liveness configuration key. */
    public static final String PREFIX = "mosip.registration.face.liveness.";

    /** Default for {@code enabled}. */
    public static final boolean DEFAULT_ENABLED = true;
    /** Default for {@code mode}. */
    public static final LivenessMode DEFAULT_MODE = LivenessMode.HYBRID;
    /** Default for {@code passive.threshold}. */
    public static final double DEFAULT_PASSIVE_THRESHOLD = 0.80;
    /** Default for {@code passive.min.frames}. */
    public static final int DEFAULT_PASSIVE_MIN_FRAMES = 8;
    /** Default for {@code active.enabled}. */
    public static final boolean DEFAULT_ACTIVE_ENABLED = true;
    /** Default for {@code challenge.pool}. */
    public static final List<ChallengeType> DEFAULT_CHALLENGE_POOL = Collections.unmodifiableList(Arrays.asList(
            ChallengeType.TURN_LEFT, ChallengeType.TURN_RIGHT, ChallengeType.LOOK_UP, ChallengeType.SMILE,
            ChallengeType.OPEN_MOUTH, ChallengeType.CLOSE_EYES, ChallengeType.BLINK));
    /** Default for {@code challenge.min.count}. */
    public static final int DEFAULT_CHALLENGE_MIN_COUNT = 2;
    /** Default for {@code challenge.max.count}. */
    public static final int DEFAULT_CHALLENGE_MAX_COUNT = 3;
    /** Default for {@code challenge.timeout.ms}. */
    public static final long DEFAULT_CHALLENGE_TIMEOUT_MS = 5000L;
    /** Default for {@code session.timeout.ms}. */
    public static final long DEFAULT_SESSION_TIMEOUT_MS = 30000L;
    /** Default for {@code blink.min.fps}. */
    public static final double DEFAULT_BLINK_MIN_FPS = 15.0;
    /** Default for {@code max.retries}. */
    public static final int DEFAULT_MAX_RETRIES = 3;
    /** Default for {@code on.max.retries}. */
    public static final MaxRetriesAction DEFAULT_ON_MAX_RETRIES = MaxRetriesAction.BLOCK;
    /** Default for {@code bind.capture.enabled}. */
    public static final boolean DEFAULT_BIND_CAPTURE_ENABLED = true;
    /** Default for {@code bind.min.similarity}. */
    public static final double DEFAULT_BIND_MIN_SIMILARITY = 0.60;

    private LivenessConfigLoader() {
    }

    /**
     * Resolves the policy for one workflow.
     *
     * @param config   the flat configuration map (for example {@code ApplicationContext.map()} on Desktop or the
     *                 global parameters on Android); never modified
     * @param workflow the workflow to resolve the policy for
     * @return the resolved, immutable policy
     */
    public static LivenessPolicy load(Map<String, String> config, Workflow workflow) {
        LivenessPolicy.Builder b = LivenessPolicy.builder(workflow);
        Resolver r = new Resolver(config, workflow, b);
        return b
                .enabled(r.bool("enabled", false, DEFAULT_ENABLED))
                .mode(r.enumValue("mode", true, LivenessMode.class, DEFAULT_MODE))
                .passiveThreshold(r.decimal("passive.threshold", true, DEFAULT_PASSIVE_THRESHOLD))
                .passiveMinFrames(r.integer("passive.min.frames", false, DEFAULT_PASSIVE_MIN_FRAMES))
                .activeEnabled(r.bool("active.enabled", true, DEFAULT_ACTIVE_ENABLED))
                .challengePool(DEFAULT_CHALLENGE_POOL)
                .challengeMinCount(r.integer("challenge.min.count", true, DEFAULT_CHALLENGE_MIN_COUNT))
                .challengeMaxCount(r.integer("challenge.max.count", false, DEFAULT_CHALLENGE_MAX_COUNT))
                .challengeTimeoutMs(r.integer("challenge.timeout.ms", true, DEFAULT_CHALLENGE_TIMEOUT_MS))
                .sessionTimeoutMs(r.integer("session.timeout.ms", false, DEFAULT_SESSION_TIMEOUT_MS))
                .blinkMinFps(r.decimal("blink.min.fps", false, DEFAULT_BLINK_MIN_FPS))
                .maxRetries(r.integer("max.retries", true, DEFAULT_MAX_RETRIES))
                .onMaxRetries(r.enumValue("on.max.retries", true, MaxRetriesAction.class, DEFAULT_ON_MAX_RETRIES))
                .bindCaptureEnabled(r.bool("bind.capture.enabled", false, DEFAULT_BIND_CAPTURE_ENABLED))
                .bindMinSimilarity(r.decimal("bind.min.similarity", false, DEFAULT_BIND_MIN_SIMILARITY))
                .build();
    }

    /** Finds and parses raw values, reporting unusable ones as warnings on the builder. */
    private static final class Resolver {
        private final Map<String, String> config;
        private final String policyPrefix;
        private final LivenessPolicy.Builder builder;

        Resolver(Map<String, String> config, Workflow workflow, LivenessPolicy.Builder builder) {
            this.config = config;
            this.policyPrefix = PREFIX + "policy." + workflow.policyCode() + ".";
            this.builder = builder;
        }

        boolean bool(String setting, boolean perWorkflow, boolean defaultValue) {
            String key = keyFor(setting, perWorkflow);
            if (key == null) {
                return defaultValue;
            }
            String value = value(key).toLowerCase(Locale.ROOT);
            if (value.equals("true")) {
                return true;
            }
            if (value.equals("false")) {
                return false;
            }
            return invalid(key, defaultValue);
        }

        double decimal(String setting, boolean perWorkflow, double defaultValue) {
            String key = keyFor(setting, perWorkflow);
            if (key == null) {
                return defaultValue;
            }
            try {
                return Double.parseDouble(value(key));
            } catch (NumberFormatException e) {
                return invalid(key, defaultValue);
            }
        }

        int integer(String setting, boolean perWorkflow, int defaultValue) {
            String key = keyFor(setting, perWorkflow);
            if (key == null) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value(key));
            } catch (NumberFormatException e) {
                return invalid(key, defaultValue);
            }
        }

        long integer(String setting, boolean perWorkflow, long defaultValue) {
            String key = keyFor(setting, perWorkflow);
            if (key == null) {
                return defaultValue;
            }
            try {
                return Long.parseLong(value(key));
            } catch (NumberFormatException e) {
                return invalid(key, defaultValue);
            }
        }

        <E extends Enum<E>> E enumValue(String setting, boolean perWorkflow, Class<E> type, E defaultValue) {
            String key = keyFor(setting, perWorkflow);
            if (key == null) {
                return defaultValue;
            }
            try {
                return Enum.valueOf(type, value(key).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return invalid(key, defaultValue);
            }
        }

        /** @return the most specific key that has a non-blank value, or {@code null} to use the default */
        private String keyFor(String setting, boolean perWorkflow) {
            if (perWorkflow && hasValue(policyPrefix + setting)) {
                return policyPrefix + setting;
            }
            return hasValue(PREFIX + setting) ? PREFIX + setting : null;
        }

        private boolean hasValue(String key) {
            String value = config.get(key);
            return value != null && !value.trim().isEmpty();
        }

        private String value(String key) {
            return config.get(key).trim();
        }

        private <T> T invalid(String key, T defaultValue) {
            builder.warning("Invalid value '" + config.get(key) + "' for " + key + "; using default " + defaultValue);
            return defaultValue;
        }
    }
}
