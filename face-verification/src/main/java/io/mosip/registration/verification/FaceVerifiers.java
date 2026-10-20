package io.mosip.registration.verification;

import java.util.Locale;
import java.util.Map;

/** Factory for {@link FaceVerifier} implementations. */
public final class FaceVerifiers {

    private FaceVerifiers() {
    }

    /**
     * A scripted verifier for client work before the real one exists. It looks at no pixels and returns the outcome
     * named by {@code mosip.registration.face.verification.stub.force} ({@code MATCH} by default or when unknown).
     * Its model version is {@code stub}.
     *
     * @param config the flat configuration map; reads {@code threshold} and {@code stub.force}
     * @return the stub verifier
     */
    public static FaceVerifier stub(Map<String, String> config) {
        return new StubFaceVerifier(FaceVerificationConfig.load(config).threshold(), forcedOutcome(config));
    }

    private static VerificationResult.Outcome forcedOutcome(Map<String, String> config) {
        String value = config.get(FaceVerificationConfig.PREFIX + "stub.force");
        if (value == null) {
            return VerificationResult.Outcome.MATCH;
        }
        try {
            return VerificationResult.Outcome.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return VerificationResult.Outcome.MATCH;
        }
    }
}
