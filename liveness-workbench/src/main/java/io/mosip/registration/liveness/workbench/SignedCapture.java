package io.mosip.registration.liveness.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * One device-signed face from an SBI 0.9.5 registration capture: the signed JWT's payload (the ISO image, the echoed
 * {@code transactionId}, the signed time) and whether its RS256 signature checks against the certificate in its own
 * header. That proves the payload is what the device signed; whether that device is trusted is the client's check
 * against its trust store, which the Workbench does not make. Immutable.
 */
public final class SignedCapture {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonNode payload;
    private final X509Certificate certificate;
    private final boolean signatureValid;

    private SignedCapture(JsonNode payload, X509Certificate certificate, boolean signatureValid) {
        this.payload = payload;
        this.certificate = certificate;
        this.signatureValid = signatureValid;
    }

    /**
     * @param reply the RCAPTURE reply
     * @return its first captured face
     * @throws IOException if the device reported an error, sent no capture, or sent one that cannot be read
     */
    public static SignedCapture parse(String reply) throws IOException {
        JsonNode biometrics = JSON.readTree(reply).path("biometrics");
        if (biometrics.size() == 0) {
            throw new IOException("The device sent no capture");
        }
        JsonNode bio = biometrics.get(0);
        String errorCode = bio.path("error").path("errorCode").asText("0");
        if (!"0".equals(errorCode)) {
            throw new IOException("Device error " + errorCode + ": " + bio.path("error").path("errorInfo").asText());
        }
        String[] jwt = bio.path("data").asText().split("\\.");
        if (jwt.length != 3) {
            throw new IOException("The capture is not a signed JWT");
        }
        Base64.Decoder url = Base64.getUrlDecoder();
        JsonNode header = JSON.readTree(url.decode(jwt[0]));
        JsonNode payload = JSON.readTree(url.decode(jwt[1]));
        X509Certificate certificate;
        boolean valid;
        try {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                    new ByteArrayInputStream(Base64.getMimeDecoder().decode(header.path("x5c").path(0).asText())));
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initVerify(certificate.getPublicKey());
            rsa.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
            valid = "RS256".equals(header.path("alg").asText()) && rsa.verify(url.decode(jwt[2]));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("The capture's signature cannot be checked: " + e.getMessage(), e);
        }
        return new SignedCapture(payload, certificate, valid);
    }

    /** @return whether the signature checks against the certificate in the JWT's header */
    public boolean signatureValid() {
        return signatureValid;
    }

    /** @return the certificate the device signed with */
    public X509Certificate deviceCertificate() {
        return certificate;
    }

    /** @return the {@code transactionId} the device signed, which must echo the session nonce */
    public String transactionId() {
        return payload.path("transactionId").asText(null);
    }

    /**
     * @return the signed capture time, epoch milliseconds; SBI timestamps are ISO 8601 in UTC ({@code Z}), and an
     *         offset is read too
     * @throws IllegalArgumentException if it cannot be read
     */
    public long timestampEpochMs() {
        String timestamp = payload.path("timestamp").asText();
        try {
            return Instant.parse(timestamp).toEpochMilli();
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(timestamp).toInstant().toEpochMilli();
            } catch (DateTimeParseException again) {
                throw new IllegalArgumentException("Unreadable signed timestamp: " + timestamp, again);
            }
        }
    }

    /** @return the signed image, an ISO/IEC 19794-5 face record */
    public byte[] bioValue() {
        return Base64.getUrlDecoder().decode(payload.path("bioValue").asText());
    }

    /** @return the device's own quality score, for display */
    public String qualityScore() {
        return payload.path("qualityScore").asText();
    }
}
