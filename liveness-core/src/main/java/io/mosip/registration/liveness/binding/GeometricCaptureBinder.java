package io.mosip.registration.liveness.binding;

import io.mosip.registration.liveness.api.AnalysedFrame;
import io.mosip.registration.liveness.api.BindingResult;
import io.mosip.registration.liveness.api.CaptureBinder;
import io.mosip.registration.liveness.api.FaceAnalyzer;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.Rect;
import io.mosip.registration.liveness.api.SessionState;
import java.util.List;

/**
 * The Desktop capture binder (TECHNICAL_DESIGN §8): the signed capture must show the scene the liveness session just
 * saw. Valid only when the stream and the capture come from the same camera (the Desktop SBI stream and its RCAPTURE).
 * The capture is compared with each of the session's last frames, and the best pair counts; a pair scores the mean
 * of three cues:
 * <ul>
 * <li>face box overlap (IoU) with coordinates scaled to 0..1, since the capture's resolution may differ;</li>
 * <li>landmark agreement: the mean distance of five points (eye corners, nose tip, mouth corners), with the
 * capture's points scaled to the stream frame's size, divided by the distance between the eyes, as
 * {@code max(0, 1 - d / 0.25)};</li>
 * <li>a 64-bit perceptual hash (pHash) of the face crop, as {@code 1 - Hamming / 64}.</li>
 * </ul>
 * The capture must be the camera's whole frame, at any resolution: a capture cropped to another aspect ratio puts the
 * face elsewhere in 0..1 and does not bind. Recent frames that cannot be compared (not exactly one face, a landmark
 * that is not a number, a box that covers under 2x2 pixels of the frame) are skipped. Bound at
 * {@code bind.min.similarity}. Never bound when liveness did not pass, no recent frame can be compared, or the capture
 * does not show exactly one face that can be compared. The evidence digest becomes SHA-256 over the session's digest
 * and the SHA-256 of the capture's decoded pixels, the image that was compared. Thread-safe if the analyzer is.
 *
 * <p>It proves "same scene, same camera", not identity: a different face warped exactly onto the live face's position
 * and landmarks could still score high. Identity is the face matcher's job (Android's binder, ADR 0001).
 * On test faces: the same scene 1.0, the scene drifted 8 px 0.78, another person where the face was 0.29.
 */
public final class GeometricCaptureBinder implements CaptureBinder {

    /** Landmark distance, relative to the eye distance, at which the landmark cue reaches 0. */
    static final double LANDMARK_SPAN = 0.25;

    private final FaceAnalyzer analyzer;
    private final double minSimilarity;

    /**
     * @param analyzer      the face analyzer, the same as the session's
     * @param minSimilarity {@code bind.min.similarity}
     */
    public GeometricCaptureBinder(FaceAnalyzer analyzer, double minSimilarity) {
        this.analyzer = analyzer;
        this.minSimilarity = minSimilarity;
    }

    @Override
    public BindingResult verify(LivenessResult result, FaceFrame finalCapture, List<AnalysedFrame> recentFrames) {
        String digest = Digests.extend(result.evidenceDigest(), finalCapture.rgb());
        if (result.finalState() != SessionState.PASSED) {
            return new BindingResult(false, 0, "liveness did not pass", digest);
        }
        if (recentFrames.isEmpty()) {
            return new BindingResult(false, 0, "the session kept no frames to compare", digest);
        }
        FaceObservation capture = analyzer.analyze(finalCapture);
        if (capture.faceCount() != 1) {
            return new BindingResult(false, 0, capture.faceCount() == 0 ? "no face in the capture"
                    : "more than one face in the capture", digest);
        }
        if (!comparable(finalCapture, capture)) {
            return new BindingResult(false, 0, "the face in the capture cannot be compared", digest);
        }
        double best = 0;
        boolean compared = false;
        for (AnalysedFrame recent : recentFrames) {
            if (comparable(recent.frame(), recent.observation())) {
                best = Math.max(best, similarity(recent.frame(), recent.observation(), finalCapture, capture));
                compared = true;
            }
        }
        if (!compared) {
            return new BindingResult(false, 0, "no recent frame shows one face to compare", digest);
        }
        boolean bound = best >= minSimilarity;
        return new BindingResult(bound, best, bound ? "bound" : "the capture does not match the live session",
                digest);
    }

    /** One face, with five finite landmarks and a box that covers at least 2x2 pixels of the frame. */
    private static boolean comparable(FaceFrame frame, FaceObservation face) {
        return face.faceCount() == 1 && face.box() != null && Landmarks.usable(face.landmarks())
                && PerceptualHash.covers(frame, face.box());
    }

    private static double similarity(FaceFrame streamFrame, FaceObservation stream, FaceFrame captureFrame,
            FaceObservation capture) {
        double iou = iou(normalised(stream.box(), streamFrame), normalised(capture.box(), captureFrame));
        // The capture's landmarks are brought to the stream frame's scale, as the boxes are compared at one scale
        double landmarks = Landmarks.agreement(stream.landmarks(), capture.landmarks(),
                (double) streamFrame.width() / captureFrame.width(),
                (double) streamFrame.height() / captureFrame.height(), LANDMARK_SPAN);
        double phash = 1.0 - Long.bitCount(PerceptualHash.of(streamFrame, stream.box())
                ^ PerceptualHash.of(captureFrame, capture.box())) / 64.0;
        return (iou + landmarks + phash) / 3;
    }

    /** @return x0, y0, x1, y1 as shares of the frame's width and height */
    private static double[] normalised(Rect box, FaceFrame frame) {
        return new double[] {(double) box.x() / frame.width(), (double) box.y() / frame.height(),
            (double) (box.x() + box.width()) / frame.width(), (double) (box.y() + box.height()) / frame.height()};
    }

    private static double iou(double[] a, double[] b) {
        double w = Math.min(a[2], b[2]) - Math.max(a[0], b[0]);
        double h = Math.min(a[3], b[3]) - Math.max(a[1], b[1]);
        double inter = w > 0 && h > 0 ? w * h : 0;
        double union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter;
        return union > 0 ? inter / union : 0;
    }
}
