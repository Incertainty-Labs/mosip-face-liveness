package io.mosip.registration.liveness.binding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** The evidence digest's last step (API.md §3.6): the capture's hash appended to the session's digest. */
final class Digests {

    private Digests() {
    }

    /**
     * @param sessionDigest the session's evidence digest (hex), or null when the engine made none
     * @param capturePixels the capture's decoded pixels
     * @return hex SHA-256 over the session digest's text (empty when null) and the SHA-256 of the pixels
     */
    static String extend(String sessionDigest, byte[] capturePixels) {
        MessageDigest digest = sha256();
        digest.update((sessionDigest == null ? "" : sessionDigest).getBytes(StandardCharsets.UTF_8));
        digest.update(sha256().digest(capturePixels));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }
}
