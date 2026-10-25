package io.mosip.registration.liveness.api;

/** Whether the device-signed capture matches the frames the session analysed. Immutable. */
public final class BindingResult {

    private final boolean bound;
    private final double similarity;
    private final String reason;
    private final String evidenceDigest;

    /**
     * @param bound          whether the capture is bound to the session
     * @param similarity     similarity 0..1 that was compared with {@code bind.min.similarity}
     * @param reason         why binding failed, for audit; empty when bound
     * @param evidenceDigest the session digest extended with the SHA-256 of the signed capture the binder compared
     *                       (for {@code GeometricCaptureBinder}, its decoded pixels), hex
     * @throws IllegalArgumentException if the similarity is outside 0..1
     */
    public BindingResult(boolean bound, double similarity, String reason, String evidenceDigest) {
        if (!(similarity >= 0.0 && similarity <= 1.0)) {
            throw new IllegalArgumentException("similarity must be within 0..1: " + similarity);
        }
        this.bound = bound;
        this.similarity = similarity;
        this.reason = reason;
        this.evidenceDigest = evidenceDigest;
    }

    /** @return whether the capture is bound to the session */
    public boolean bound() {
        return bound;
    }

    /** @return the similarity score 0..1 */
    public double similarity() {
        return similarity;
    }

    /** @return why binding failed, for audit; empty when bound */
    public String reason() {
        return reason;
    }

    /** @return the final evidence digest to store in the packet */
    public String evidenceDigest() {
        return evidenceDigest;
    }
}
