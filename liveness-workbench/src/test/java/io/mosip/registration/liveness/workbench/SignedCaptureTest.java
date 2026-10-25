package io.mosip.registration.liveness.workbench;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reading and checking a device-signed registration capture (SBI 0.9.5, MockMDS's shape). */
class SignedCaptureTest {

    private static TestDevice device;

    @BeforeAll
    static void device() throws Exception {
        device = TestDevice.create();
    }

    @Test
    @DisplayName("The signed payload gives the image, the echoed transactionId and the signed time; the signature"
            + " checks against the certificate the device sent")
    void readsAndVerifies() throws Exception {
        byte[] iso = "ISO-FACE".getBytes(StandardCharsets.US_ASCII);

        SignedCapture c = SignedCapture.parse(device.captureReply(iso, "9f2c0a11", "2026-10-25T10:15:30Z"));

        assertAll(() -> assertTrue(c.signatureValid()),
                () -> assertEquals("9f2c0a11", c.transactionId()),
                () -> assertEquals(Instant.parse("2026-10-25T10:15:30Z").toEpochMilli(), c.timestampEpochMs()),
                () -> assertArrayEquals(iso, c.bioValue()),
                () -> assertEquals("CN=Workbench Test Device", c.deviceCertificate().getSubjectX500Principal()
                        .getName()));
    }

    @Test
    @DisplayName("A payload changed after signing fails the signature check")
    void tamperedPayloadFails() throws Exception {
        String reply = device.captureReply("ISO".getBytes(StandardCharsets.US_ASCII), "abc", "2026-10-25T10:15:30Z");
        String data = reply.substring(reply.indexOf("\"data\":\"") + 8, reply.indexOf("\"", reply.indexOf("\"data\":\"")
                + 8));
        String[] parts = data.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                        .replace("\"abc\"", "\"xyz\"").getBytes(StandardCharsets.UTF_8));

        SignedCapture forged = SignedCapture.parse(reply.replace(parts[1], forgedPayload));

        assertAll(() -> assertEquals("xyz", forged.transactionId()), () -> assertFalse(forged.signatureValid()));
    }

    @Test
    @DisplayName("Timestamps with milliseconds or an offset are read too")
    void timestampForms() throws Exception {
        byte[] iso = {1};
        long expected = Instant.parse("2026-10-25T10:15:30.250Z").toEpochMilli();

        assertAll(() -> assertEquals(expected, SignedCapture.parse(device.captureReply(iso, "a",
                        "2026-10-25T10:15:30.250Z")).timestampEpochMs()),
                () -> assertEquals(expected, SignedCapture.parse(device.captureReply(iso, "a",
                        "2026-10-25T15:45:30.250+05:30")).timestampEpochMs()));
    }

    @Test
    @DisplayName("A device error in the reply, or a reply without a capture, is an IOException with the device's"
            + " reason")
    void deviceErrors() {
        String failed = "{\"biometrics\":[{\"specVersion\":\"0.9.5\",\"data\":\"\",\"hash\":\"\","
                + "\"error\":{\"errorCode\":\"701\",\"errorInfo\":\"Capture Timeout\"}}]}";

        IOException e = assertThrows(IOException.class, () -> SignedCapture.parse(failed));
        assertAll(() -> assertTrue(e.getMessage().contains("701") && e.getMessage().contains("Capture Timeout")),
                () -> assertThrows(IOException.class, () -> SignedCapture.parse("{\"biometrics\":[]}")));
    }
}
