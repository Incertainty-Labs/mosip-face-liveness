package io.mosip.registration.liveness.config;

import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.LivenessMode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.MaxRetriesAction;
import io.mosip.registration.liveness.api.Workflow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    /** Default for {@code enabled}: off, so a client without liveness config behaves exactly as before (MOSIP, 21 Oct). */
    public static final boolean DEFAULT_ENABLED = false;
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
    /** Default for {@code bind.freshness.max.age.ms}. */
    public static final long DEFAULT_BIND_FRESHNESS_MAX_AGE_MS = 60000L;
    /**
     * Default for {@code detector.min.score}: 0.70, as TECHNICAL_DESIGN first set it. OpenCV's 0.90 lost the face in 31 of
     * 41 frames of a real look-up, so LOOK_UP could not pass; at 0.70 no bona fide clip gained a false face (A-11).
     */
    public static final double DEFAULT_DETECTOR_MIN_SCORE = 0.70;
    /**
     * Default for {@code quality.min.face.width}: 0.18 of the frame width, 115 px on 640. TECHNICAL_DESIGN had a quarter,
     * but at a desk webcam real faces measured 125 to 191 px and MiniFASNet scored them 0.84 to 0.99 (A-09).
     */
    public static final double DEFAULT_QUALITY_MIN_FACE_WIDTH = 0.18;
    /** Default for {@code quality.max.centre.offset}: centre within the middle half of the frame. */
    public static final double DEFAULT_QUALITY_MAX_CENTRE_OFFSET = 0.25;
    /** Default for {@code quality.min.sharpness}: sharp faces measure 0.6 to 0.9, blurred ones 0.1 to 0.3. */
    public static final double DEFAULT_QUALITY_MIN_SHARPNESS = 0.40;
    /** Default for {@code quality.min.brightness}: mean luma 60 of 255. */
    public static final double DEFAULT_QUALITY_MIN_BRIGHTNESS = 0.235;
    /** Default for {@code quality.max.brightness}: mean luma 200 of 255. */
    public static final double DEFAULT_QUALITY_MAX_BRIGHTNESS = 0.784;
    /** Default for {@code pad.weights}: the two MiniFASNet models equally (the moire cue was cut, A-09). */
    public static final Map<String, Double> DEFAULT_PAD_WEIGHTS;

    static {
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        weights.put("minifasnet_v2", 0.5);
        weights.put("minifasnet_v1se", 0.5);
        DEFAULT_PAD_WEIGHTS = Collections.unmodifiableMap(weights);
    }

    /** The passive PAD cues this build has, by {@code PadDetector.name()}; {@code pad.weights} may name only these. */
    static final Set<String> PAD_CUES = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList("minifasnet_v2", "minifasnet_v1se")));
    /** How far the {@code pad.weights} sum may be from 1. */
    private static final double WEIGHT_SUM_TOLERANCE = 0.001;
    /** Upper bound for {@code challenge.min.count} and {@code challenge.max.count}. */
    public static final int MAX_CHALLENGES = 5;

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

        int minCount = r.integer("challenge.min.count", true, DEFAULT_CHALLENGE_MIN_COUNT, 1, MAX_CHALLENGES);
        int maxCount = r.integer("challenge.max.count", false, DEFAULT_CHALLENGE_MAX_COUNT, 1, MAX_CHALLENGES);
        if (minCount > maxCount) {
            b.warning("challenge.min.count (" + minCount + ") is above challenge.max.count (" + maxCount
                    + "); using defaults " + DEFAULT_CHALLENGE_MIN_COUNT + " and " + DEFAULT_CHALLENGE_MAX_COUNT);
            minCount = DEFAULT_CHALLENGE_MIN_COUNT;
            maxCount = DEFAULT_CHALLENGE_MAX_COUNT;
        }

        double minBrightness = r.decimal("quality.min.brightness", false, DEFAULT_QUALITY_MIN_BRIGHTNESS, 0.0, 1.0);
        double maxBrightness = r.decimal("quality.max.brightness", false, DEFAULT_QUALITY_MAX_BRIGHTNESS, 0.0, 1.0);
        if (minBrightness >= maxBrightness) {
            b.warning("quality.min.brightness (" + minBrightness + ") is not below quality.max.brightness (" + maxBrightness
                    + "); using defaults " + DEFAULT_QUALITY_MIN_BRIGHTNESS + " and " + DEFAULT_QUALITY_MAX_BRIGHTNESS);
            minBrightness = DEFAULT_QUALITY_MIN_BRIGHTNESS;
            maxBrightness = DEFAULT_QUALITY_MAX_BRIGHTNESS;
        }

        return b
                .enabled(r.bool("enabled", false, DEFAULT_ENABLED))
                .mode(r.enumValue("mode", true, LivenessMode.class, DEFAULT_MODE))
                .passiveThreshold(r.decimal("passive.threshold", true, DEFAULT_PASSIVE_THRESHOLD, 0.0, 1.0))
                .passiveMinFrames(r.integer("passive.min.frames", false, DEFAULT_PASSIVE_MIN_FRAMES, 3, 60))
                .activeEnabled(r.bool("active.enabled", true, DEFAULT_ACTIVE_ENABLED))
                .challengePool(r.challengeList("challenge.pool", true, DEFAULT_CHALLENGE_POOL))
                .challengeMinCount(minCount)
                .challengeMaxCount(maxCount)
                .challengeTimeoutMs(r.integer("challenge.timeout.ms", true, DEFAULT_CHALLENGE_TIMEOUT_MS, 2000L, 15000L))
                .sessionTimeoutMs(r.integer("session.timeout.ms", false, DEFAULT_SESSION_TIMEOUT_MS, 10000L, 120000L))
                .blinkMinFps(r.decimal("blink.min.fps", false, DEFAULT_BLINK_MIN_FPS, 1.0, 120.0))
                .maxRetries(r.integer("max.retries", true, DEFAULT_MAX_RETRIES, 1, 10))
                .onMaxRetries(r.enumValue("on.max.retries", true, MaxRetriesAction.class, DEFAULT_ON_MAX_RETRIES))
                .bindCaptureEnabled(r.bool("bind.capture.enabled", false, DEFAULT_BIND_CAPTURE_ENABLED))
                .bindMinSimilarity(r.decimal("bind.min.similarity", false, DEFAULT_BIND_MIN_SIMILARITY, 0.0, 1.0))
                .bindFreshnessMaxAgeMs(r.integer("bind.freshness.max.age.ms", false,
                        DEFAULT_BIND_FRESHNESS_MAX_AGE_MS, 1000L, 300000L))
                .detectorMinScore(r.decimal("detector.min.score", false, DEFAULT_DETECTOR_MIN_SCORE, 0.5, 0.99))
                .qualityMinFaceWidth(r.decimal("quality.min.face.width", false, DEFAULT_QUALITY_MIN_FACE_WIDTH, 0.05, 0.9))
                .qualityMaxCentreOffset(r.decimal("quality.max.centre.offset", false, DEFAULT_QUALITY_MAX_CENTRE_OFFSET, 0.05,
                        0.5))
                .qualityMinSharpness(r.decimal("quality.min.sharpness", false, DEFAULT_QUALITY_MIN_SHARPNESS, 0.0, 1.0))
                .qualityMinBrightness(minBrightness)
                .qualityMaxBrightness(maxBrightness)
                .padWeights(r.weights("pad.weights", DEFAULT_PAD_WEIGHTS))
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

        double decimal(String setting, boolean perWorkflow, double defaultValue, double min, double max) {
            String key = keyFor(setting, perWorkflow);
            if (key == null) {
                return defaultValue;
            }
            try {
                double parsed = Double.parseDouble(value(key));
                return parsed >= min && parsed <= max ? parsed : invalid(key, defaultValue);
            } catch (NumberFormatException e) {
                return invalid(key, defaultValue);
            }
        }

        int integer(String setting, boolean perWorkflow, int defaultValue, int min, int max) {
            return (int) integer(setting, perWorkflow, (long) defaultValue, (long) min, (long) max);
        }

        long integer(String setting, boolean perWorkflow, long defaultValue, long min, long max) {
            String key = keyFor(setting, perWorkflow);
            if (key == null) {
                return defaultValue;
            }
            try {
                long parsed = Long.parseLong(value(key));
                return parsed >= min && parsed <= max ? parsed : invalid(key, defaultValue);
            } catch (NumberFormatException e) {
                return invalid(key, defaultValue);
            }
        }

        List<ChallengeType> challengeList(String setting, boolean perWorkflow, List<ChallengeType> defaultValue) {
            String key = keyFor(setting, perWorkflow);
            if (key == null) {
                return defaultValue;
            }
            List<ChallengeType> pool = new ArrayList<ChallengeType>();
            for (String name : value(key).split(",", -1)) {
                ChallengeType type = selectableChallenge(name.trim());
                if (type == null) {
                    return invalid(key, defaultValue);
                }
                pool.add(type);
            }
            return pool;
        }

        /**
         * {@code name:weight,name:weight}, global only. Each weight is within 0..1, each name appears once and the
         * weights sum to 1; names of cues this build does not have are dropped (the caller renormalises).
         */
        Map<String, Double> weights(String setting, Map<String, Double> defaultValue) {
            String key = keyFor(setting, false);
            if (key == null) {
                return defaultValue;
            }
            Map<String, Double> weights = new LinkedHashMap<String, Double>();
            double sum = 0;
            for (String entry : value(key).split(",", -1)) {
                String[] nameWeight = entry.split(":", -1);
                if (nameWeight.length != 2 || weights.containsKey(nameWeight[0].trim())) {
                    return invalid(key, defaultValue);
                }
                double weight;
                try {
                    weight = Double.parseDouble(nameWeight[1].trim());
                } catch (NumberFormatException e) {
                    return invalid(key, defaultValue);
                }
                if (!(weight >= 0.0 && weight <= 1.0)) {
                    return invalid(key, defaultValue);
                }
                weights.put(nameWeight[0].trim(), weight);
                sum += weight;
            }
            if (Math.abs(sum - 1.0) > WEIGHT_SUM_TOLERANCE) {
                return invalid(key, defaultValue);
            }
            for (String name : new ArrayList<String>(weights.keySet())) {
                if (!PAD_CUES.contains(name)) {
                    builder.warning("Unknown passive cue '" + name + "' in " + key + " (known: " + PAD_CUES
                            + "); ignored");
                    weights.remove(name);
                }
            }
            return weights.isEmpty() ? invalid(key, defaultValue) : weights;
        }

        /** @return the challenge named, or {@code null} if unknown or not selectable (NEUTRAL_HOLD is always added) */
        private ChallengeType selectableChallenge(String name) {
            try {
                ChallengeType type = ChallengeType.valueOf(name.toUpperCase(Locale.ROOT));
                return type == ChallengeType.NEUTRAL_HOLD ? null : type;
            } catch (IllegalArgumentException e) {
                return null;
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
