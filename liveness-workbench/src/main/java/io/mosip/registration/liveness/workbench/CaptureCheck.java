package io.mosip.registration.liveness.workbench;

import io.mosip.registration.liveness.api.AnalysedFrame;
import io.mosip.registration.liveness.api.BindingResult;
import io.mosip.registration.liveness.api.FaceAnalyzer;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.binding.CaptureFreshness;
import io.mosip.registration.liveness.binding.GeometricCaptureBinder;
import java.io.IOException;
import java.util.List;

/**
 * What the Desktop client does with the signed capture after a pass (TECHNICAL_DESIGN §8), in order: the signature
 * must check, the device must have signed the session nonce back within {@code bind.freshness.max.age.ms} of the pass,
 * the ISO image must decode, and, with {@code bind.capture.enabled}, the image must show the scene the session just
 * saw ({@link GeometricCaptureBinder}). The first that fails decides: LIV-014, or LIV-012 for an undecodable image.
 */
public final class CaptureCheck {

    private final FaceAnalyzer analyzer;

    /** @param analyzer the face analyzer, for the binder */
    public CaptureCheck(FaceAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    /** The checks' results. Immutable. */
    public static final class Outcome {
        private final boolean signatureValid;
        private final boolean fresh;
        private final BindingResult binding;
        private final LivenessErrorCode errorCode;
        private final String reason;
        private final String evidenceDigest;
        private final FaceFrame image;

        Outcome(boolean signatureValid, boolean fresh, BindingResult binding, LivenessErrorCode errorCode,
                String reason, String evidenceDigest, FaceFrame image) {
            this.signatureValid = signatureValid;
            this.fresh = fresh;
            this.binding = binding;
            this.errorCode = errorCode;
            this.reason = reason;
            this.evidenceDigest = evidenceDigest;
            this.image = image;
        }

        /** @return whether the capture may be used: every check passed */
        public boolean bound() {
            return errorCode == null;
        }

        public boolean signatureValid() {
            return signatureValid;
        }

        /** @return whether the nonce and the signed time were right */
        public boolean fresh() {
            return fresh;
        }

        /** @return the binder's result; null when the binder is off or an earlier check failed */
        public BindingResult binding() {
            return binding;
        }

        /** @return LIV-014 or LIV-012 when refused; null when bound */
        public LivenessErrorCode errorCode() {
            return errorCode;
        }

        /** @return why, for the audit only */
        public String reason() {
            return reason;
        }

        /** @return the final evidence digest: the binder's, or the session's when the binder is off */
        public String evidenceDigest() {
            return evidenceDigest;
        }

        /** @return the decoded capture; null when it was not decoded */
        public FaceFrame image() {
            return image;
        }
    }

    /**
     * @param result       the PASSED result, with its nonce and evidence digest
     * @param capture      the signed capture that followed
     * @param passedAtMs   when the result arrived, on the wall clock the device signs with
     * @param policy       the workflow's policy
     * @param recentFrames the session's last analysed frames
     * @return the outcome
     */
    public Outcome check(LivenessResult result, SignedCapture capture, long passedAtMs, LivenessPolicy policy,
            List<AnalysedFrame> recentFrames) {
        String digest = result.evidenceDigest();
        if (!capture.signatureValid()) {
            return refused(false, false, null, "the capture's signature does not verify", digest, null);
        }
        long signedAt;
        try {
            signedAt = capture.timestampEpochMs();
        } catch (IllegalArgumentException e) {
            return refused(true, false, null, e.getMessage(), digest, null);
        }
        CaptureFreshness.Check freshness = CaptureFreshness.verify(result.nonce(), capture.transactionId(), signedAt,
                passedAtMs, policy.bindFreshnessMaxAgeMs());
        if (!freshness.fresh()) {
            return refused(true, false, null, freshness.reason(), digest, null);
        }
        FaceFrame image;
        try {
            image = IsoFace.decode(capture.bioValue());
        } catch (IOException | IllegalArgumentException e) {
            return new Outcome(true, true, null, LivenessErrorCode.INVALID_FRAME_DATA, e.getMessage(), digest, null);
        }
        if (!policy.bindCaptureEnabled()) {
            return new Outcome(true, true, null, null, "fresh; the binder is off", digest, image);
        }
        BindingResult binding = new GeometricCaptureBinder(analyzer, policy.bindMinSimilarity()).verify(result, image,
                recentFrames);
        return new Outcome(true, true, binding, binding.bound() ? null : LivenessErrorCode.CAPTURE_BINDING_FAILED,
                binding.reason(), binding.evidenceDigest(), image);
    }

    private static Outcome refused(boolean signatureValid, boolean fresh, BindingResult binding, String reason,
            String digest, FaceFrame image) {
        return new Outcome(signatureValid, fresh, binding, LivenessErrorCode.CAPTURE_BINDING_FAILED, reason, digest,
                image);
    }
}
