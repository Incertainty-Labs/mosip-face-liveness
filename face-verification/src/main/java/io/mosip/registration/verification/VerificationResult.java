package io.mosip.registration.verification;

/** The outcome of one 1:1 face verification. Immutable. Similarity is for audit only, never shown in the UI. */
public final class VerificationResult {

    /** How a verification ended, naming which image is at fault when it failed. */
    public enum Outcome {
        /** Same person: similarity at or above the threshold. */
        MATCH,
        /** Different people. */
        NO_MATCH,
        /** No face in the probe: the user can retry (LIV-004 guidance; LIV-014 for a signed capture in a binder). */
        NO_FACE_PROBE,
        /** More than one face in the probe: the user can retry (LIV-005 guidance). */
        MULTIPLE_FACES_PROBE,
        /** No usable reference (no face, several faces, or no references at all): a broken enrolment, not a retry. */
        REFERENCE_UNUSABLE
    }

    private final Outcome outcome;
    private final double similarity;
    private final double threshold;
    private final String modelVersion;

    /**
     * @param outcome      how the verification ended; required
     * @param similarity   0..1 (see {@link #similarityFromCosine(double)}); 0 when no comparison was possible
     * @param threshold    the decision threshold applied, 0..1
     * @param modelVersion the matcher models, for audit, for example {@code yunet=2023mar; sface=2021dec}
     * @throws IllegalArgumentException if the outcome or model version is missing or a value is outside 0..1
     */
    public VerificationResult(Outcome outcome, double similarity, double threshold, String modelVersion) {
        if (outcome == null || modelVersion == null) {
            throw new IllegalArgumentException("outcome and modelVersion are required");
        }
        if (!(similarity >= 0.0 && similarity <= 1.0) || !(threshold >= 0.0 && threshold <= 1.0)) {
            throw new IllegalArgumentException("similarity and threshold must be within 0..1");
        }
        this.outcome = outcome;
        this.similarity = similarity;
        this.threshold = threshold;
        this.modelVersion = modelVersion;
    }

    /**
     * Maps a cosine similarity (-1..1) to the reported similarity: values below 0 are reported as 0, since faces less
     * alike than unrelated ones carry no extra meaning for the match decision.
     *
     * @param cosine cosine similarity of two L2-normalised embeddings
     * @return the similarity, 0..1
     */
    public static double similarityFromCosine(double cosine) {
        return Math.min(1.0, Math.max(0.0, cosine));
    }

    /** @return how the verification ended */
    public Outcome outcome() {
        return outcome;
    }

    /** @return whether the faces matched */
    public boolean matched() {
        return outcome == Outcome.MATCH;
    }

    /** @return the similarity, 0..1; audit only */
    public double similarity() {
        return similarity;
    }

    /** @return the threshold that was applied */
    public double threshold() {
        return threshold;
    }

    /** @return the matcher models, for audit and packet evidence */
    public String modelVersion() {
        return modelVersion;
    }
}
