package io.mosip.registration.verification;

import io.mosip.registration.liveness.api.FaceFrame;
import java.util.List;

/** Scripted verifier; see {@link FaceVerifiers#stub(java.util.Map)}. Stateless, so thread-safe. */
final class StubFaceVerifier implements FaceVerifier {

    /** Model version reported by the stub. */
    static final String MODEL_VERSION = "stub";
    /** Similarity reported for a scripted match. */
    static final double MATCH_SIMILARITY = 0.90;
    /** Similarity reported for a scripted non-match. */
    static final double NO_MATCH_SIMILARITY = 0.10;

    private final double threshold;
    private final VerificationResult.Outcome outcome;

    StubFaceVerifier(double threshold, VerificationResult.Outcome outcome) {
        this.threshold = threshold;
        this.outcome = outcome;
    }

    @Override
    public VerificationResult verify(FaceFrame probe, FaceFrame reference) {
        return result(outcome);
    }

    @Override
    public VerificationResult verify(FaceFrame probe, List<FaceFrame> references) {
        boolean probeProblem = outcome == VerificationResult.Outcome.NO_FACE_PROBE
                || outcome == VerificationResult.Outcome.MULTIPLE_FACES_PROBE;
        if (references.isEmpty() && !probeProblem) {
            return result(VerificationResult.Outcome.REFERENCE_UNUSABLE);
        }
        return result(outcome);
    }

    private VerificationResult result(VerificationResult.Outcome scripted) {
        double similarity;
        switch (scripted) {
            case MATCH:
                similarity = Math.max(MATCH_SIMILARITY, threshold);
                break;
            case NO_MATCH:
                similarity = Math.min(NO_MATCH_SIMILARITY, Math.max(0.0, threshold - 0.01));
                break;
            default:
                similarity = 0.0;
                break;
        }
        return new VerificationResult(scripted, similarity, threshold, MODEL_VERSION);
    }
}
