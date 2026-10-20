package io.mosip.registration.verification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Face verification settings from the flat registration-client configuration map, keys under {@value #PREFIX}. An
 * unusable value is replaced by its default and reported in {@link #warnings()}, as the liveness loader does.
 */
public final class FaceVerificationConfig {

    /** Prefix shared by the face verification configuration keys. */
    public static final String PREFIX = "mosip.registration.face.verification.";
    /** Default for {@code threshold}: OpenCV's SFace operating point, to be calibrated (A-17). */
    public static final double DEFAULT_THRESHOLD = 0.363;

    private final double threshold;
    private final List<String> warnings;

    private FaceVerificationConfig(double threshold, List<String> warnings) {
        this.threshold = threshold;
        this.warnings = Collections.unmodifiableList(warnings);
    }

    /**
     * @param config the flat configuration map; never modified
     * @return the resolved settings
     */
    public static FaceVerificationConfig load(Map<String, String> config) {
        List<String> warnings = new ArrayList<String>();
        String key = PREFIX + "threshold";
        String value = config.get(key);
        double threshold = DEFAULT_THRESHOLD;
        if (value != null && !value.trim().isEmpty()) {
            try {
                double parsed = Double.parseDouble(value.trim());
                if (parsed >= 0.0 && parsed <= 1.0) {
                    threshold = parsed;
                } else {
                    warnings.add(invalid(key, value));
                }
            } catch (NumberFormatException e) {
                warnings.add(invalid(key, value));
            }
        }
        return new FaceVerificationConfig(threshold, warnings);
    }

    private static String invalid(String key, String value) {
        return "Invalid value '" + value + "' for " + key + "; using default " + DEFAULT_THRESHOLD;
    }

    /** @return similarity at or above which two faces match, 0..1 */
    public double threshold() {
        return threshold;
    }

    /** @return problems found while loading; unmodifiable */
    public List<String> warnings() {
        return warnings;
    }
}
