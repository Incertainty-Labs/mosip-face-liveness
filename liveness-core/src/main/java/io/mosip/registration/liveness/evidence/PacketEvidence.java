package io.mosip.registration.liveness.evidence;

import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the liveness keys for the face BIR's {@code others} map (API.md §9), so both clients write identical packet
 * evidence. Desktop passes the map to {@code BIRBuilder.buildBIR(...).withOthers(...)}, Android to
 * {@code RegistrationServiceImpl.buildBIR(...)}.
 */
public final class PacketEvidence {

    private PacketEvidence() {
    }

    /**
     * @param result the finished session
     * @param policy the policy the session ran with; supplies the mode and the applied passive threshold
     * @return the API.md §9 keys in documented order, without {@code LIVENESS_EVIDENCE_DIGEST} when the engine made
     *         no digest (stub engine); never a null value; unmodifiable
     */
    public static Map<String, String> of(LivenessResult result, LivenessPolicy policy) {
        Map<String, String> keys = new LinkedHashMap<String, String>();
        keys.put("LIVENESS_DECISION", result.decision().name());
        keys.put("LIVENESS_SCORE", twoPlaces(result.passiveScore()));
        keys.put("LIVENESS_THRESHOLD", twoPlaces(policy.passiveThreshold()));
        keys.put("LIVENESS_MODE", policy.mode().name());
        keys.put("LIVENESS_CHALLENGES", result.challengeSummary());
        keys.put("LIVENESS_ENGINE_VERSION", engineVersion(result));
        if (result.evidenceDigest() != null) {
            keys.put("LIVENESS_EVIDENCE_DIGEST", result.evidenceDigest());
        }
        return Collections.unmodifiableMap(keys);
    }

    private static String twoPlaces(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String engineVersion(LivenessResult result) {
        StringBuilder sb = new StringBuilder(result.engineVersion());
        for (Map.Entry<String, String> model : result.modelVersions().entrySet()) {
            sb.append("; ").append(model.getKey()).append('=').append(model.getValue());
        }
        return sb.toString();
    }
}
