package io.mosip.registration.verification;

import io.mosip.registration.liveness.api.FaceFrame;
import java.util.List;

/**
 * 1:1 face verification: is the probe face the same person as the enrolled reference? Used for operator and
 * supervisor face authentication and for the Android capture binder (ADR 0001); glossary term "Face verification".
 *
 * <p>Implementations are thread-safe; create one per application. {@code verify} runs neural networks and blocks
 * for tens of milliseconds, so call it off the UI thread. Problems with the images are outcomes, never exceptions.
 */
public interface FaceVerifier {

    /**
     * @param probe     the face to check, for example the new signed capture, decoded by the client
     * @param reference the enrolled face, for example an onboarding template, decoded by the client
     * @return the outcome, never null
     */
    VerificationResult verify(FaceFrame probe, FaceFrame reference);

    /**
     * Verifies the probe against several references (a user with several enrolled templates), analysing the probe
     * only once. Returns the result for the most similar usable reference, so MATCH if any reference matches;
     * REFERENCE_UNUSABLE only if no reference is usable, including an empty list. Probe problems win over both.
     *
     * @param probe      the face to check
     * @param references the enrolled faces
     * @return the best result, never null
     */
    VerificationResult verify(FaceFrame probe, List<FaceFrame> references);
}
