package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.Rect;

/**
 * Decides whether a frame is good enough to judge liveness, using the limits in the policy. A failing frame gives
 * guidance only (LIV-004, 005 or 006), never an attack verdict. Order: face count, then framing (size, centring), then
 * image (brightness, sharpness).
 */
public final class QualityGate {

    private static final String FACE_NOT_FOUND = "liveness.guidance.face_not_found";
    private static final String ONE_FACE_ONLY = "liveness.guidance.one_face_only";
    private static final String IMPROVE_LIGHTING = "liveness.guidance.improve_lighting";

    private QualityGate() {
    }

    /**
     * @param observation what the analyzer saw
     * @param frameWidth  frame width in pixels
     * @param frameHeight frame height in pixels
     * @param policy      the limits
     * @return {@code null} when the frame passes, otherwise the error code and the guidance message to show
     */
    public static Verdict check(FaceObservation observation, int frameWidth, int frameHeight, LivenessPolicy policy) {
        if (observation.faceCount() == 0) {
            return new Verdict(LivenessErrorCode.FACE_NOT_DETECTED, FACE_NOT_FOUND);
        }
        if (observation.faceCount() > 1) {
            return new Verdict(LivenessErrorCode.MULTIPLE_FACES, ONE_FACE_ONLY);
        }
        Rect box = observation.box();
        double centreX = box.x() + box.width() / 2.0;
        double centreY = box.y() + box.height() / 2.0;
        boolean tooSmall = box.width() < policy.qualityMinFaceWidth() * frameWidth;
        boolean offCentre = Math.abs(centreX - frameWidth / 2.0) > policy.qualityMaxCentreOffset() * frameWidth
                || Math.abs(centreY - frameHeight / 2.0) > policy.qualityMaxCentreOffset() * frameHeight;
        if (tooSmall || offCentre) {
            return new Verdict(LivenessErrorCode.POOR_FACE_QUALITY, FACE_NOT_FOUND);
        }
        boolean badLight = observation.brightness() < policy.qualityMinBrightness()
                || observation.brightness() > policy.qualityMaxBrightness();
        if (badLight || observation.sharpness() < policy.qualityMinSharpness()) {
            return new Verdict(LivenessErrorCode.POOR_FACE_QUALITY, IMPROVE_LIGHTING);
        }
        return null;
    }

    /** Why a frame failed: the error code and the guidance message key. Immutable. */
    public static final class Verdict {
        private final LivenessErrorCode code;
        private final String messageKey;

        Verdict(LivenessErrorCode code, String messageKey) {
            this.code = code;
            this.messageKey = messageKey;
        }

        /** @return LIV-004, LIV-005 or LIV-006 */
        public LivenessErrorCode code() {
            return code;
        }

        /** @return the guidance message key to show */
        public String messageKey() {
            return messageKey;
        }
    }
}
