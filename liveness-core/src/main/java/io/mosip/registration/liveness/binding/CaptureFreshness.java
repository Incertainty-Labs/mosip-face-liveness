package io.mosip.registration.liveness.binding;

import io.mosip.registration.liveness.api.LivenessErrorCode;
import java.security.SecureRandom;

/**
 * Proves that a device-signed capture was made for this session and recently: the capture request carries a nonce as
 * its SBI {@code transactionId}, the device signs it back in the payload, and the signed timestamp must fall within a
 * window around a reference time. Runs on every capture, whether liveness or the capture binder is on or off; it
 * proves freshness, not who is in the image.
 *
 * <p>The reference time is when liveness PASSED, or when the capture request was sent if liveness is off. The window
 * is symmetric so modest clock differences between device and host do not fail genuine captures.
 */
public final class CaptureFreshness {

    private static final int NONCE_BYTES = 16;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private CaptureFreshness() {
    }

    /**
     * Checks one signed capture.
     *
     * @param expectedNonce          the nonce sent as the capture request's {@code transactionId}
     * @param signedTransactionId    the {@code transactionId} in the device-signed payload; may be null
     * @param signedTimestampEpochMs the device-signed capture timestamp, epoch milliseconds
     * @param referenceEpochMs       when liveness PASSED, or when the request was sent if liveness is off
     * @param maxAgeMs               the allowed distance between the two times ({@code bind.freshness.max.age.ms})
     * @return whether the capture is fresh and, if not, why
     * @throws IllegalArgumentException if the expected nonce is missing or the window is not positive
     */
    public static Check verify(String expectedNonce, String signedTransactionId, long signedTimestampEpochMs,
            long referenceEpochMs, long maxAgeMs) {
        if (expectedNonce == null || expectedNonce.isEmpty()) {
            throw new IllegalArgumentException("expectedNonce is required");
        }
        if (maxAgeMs <= 0) {
            throw new IllegalArgumentException("maxAgeMs must be positive: " + maxAgeMs);
        }
        if (!expectedNonce.equalsIgnoreCase(signedTransactionId)) {
            return Check.stale("signed transactionId does not echo the session nonce");
        }
        long distance = Math.abs(signedTimestampEpochMs - referenceEpochMs);
        if (distance > maxAgeMs) {
            return Check.stale("signed timestamp is " + distance + " ms from the reference; allowed " + maxAgeMs);
        }
        return Check.FRESH;
    }

    /**
     * Creates a nonce for a capture request: 128 random bits as 32 lowercase hex characters, which fits the SBI
     * {@code transactionId} format (4 to 50 characters of {@code [A-Za-z0-9-]}).
     *
     * @param random the random source
     * @return a new nonce
     */
    public static String newNonce(SecureRandom random) {
        byte[] bytes = new byte[NONCE_BYTES];
        random.nextBytes(bytes);
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = HEX[(bytes[i] >> 4) & 0x0f];
            out[i * 2 + 1] = HEX[bytes[i] & 0x0f];
        }
        return new String(out);
    }

    /** The outcome of {@link #verify}. Immutable. */
    public static final class Check {

        private static final Check FRESH = new Check(true, "");

        private final boolean fresh;
        private final String reason;

        private Check(boolean fresh, String reason) {
            this.fresh = fresh;
            this.reason = reason;
        }

        private static Check stale(String reason) {
            return new Check(false, reason);
        }

        /** @return whether the capture is fresh and was made for this session */
        public boolean fresh() {
            return fresh;
        }

        /** @return {@link LivenessErrorCode#CAPTURE_BINDING_FAILED} when not fresh, otherwise {@code null} */
        public LivenessErrorCode errorCode() {
            return fresh ? null : LivenessErrorCode.CAPTURE_BINDING_FAILED;
        }

        /** @return why the check failed, for audit only; empty when fresh */
        public String reason() {
            return reason;
        }
    }
}
