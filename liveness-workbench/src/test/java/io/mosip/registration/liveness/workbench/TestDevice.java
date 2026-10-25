package io.mosip.registration.liveness.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * A test-only signing device: a throwaway RSA key and self-signed certificate made with the JDK's keytool for each
 * test run, never kept, and replies shaped as MockMDS's registration capture.
 */
final class TestDevice {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    final PrivateKey key;
    final X509Certificate certificate;

    private TestDevice(PrivateKey key, X509Certificate certificate) {
        this.key = key;
        this.certificate = certificate;
    }

    static TestDevice create() throws Exception {
        Path dir = Files.createTempDirectory("workbench-device");
        File store = dir.resolve("device.p12").toFile();
        String keytool = System.getProperty("java.home") + File.separator + "bin" + File.separator + "keytool";
        Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "device", "-keyalg", "RSA", "-keysize",
                "2048", "-validity", "2", "-dname", "CN=Workbench Test Device", "-storetype", "PKCS12", "-keystore",
                store.getAbsolutePath(), "-storepass", "testpass", "-keypass", "testpass").redirectErrorStream(true)
                .start();
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
            throw new IllegalStateException("keytool failed: "
                    + new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(store)) {
            ks.load(in, "testpass".toCharArray());
        }
        TestDevice device = new TestDevice((PrivateKey) ks.getKey("device", "testpass".toCharArray()),
                (X509Certificate) ks.getCertificate("device"));
        store.delete();
        dir.toFile().delete();
        return device;
    }

    /** A signed JWT as MockMDS makes one: header with x5c and RS256, then the payload, then the signature. */
    String sign(String payloadJson) throws Exception {
        ObjectNode header = JSON.createObjectNode();
        header.putArray("x5c").add(Base64.getEncoder().encodeToString(certificate.getEncoded()));
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        String signingInput = URL.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8)) + "."
                + URL.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        Signature rsa = Signature.getInstance("SHA256withRSA");
        rsa.initSign(key);
        rsa.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + URL.encodeToString(rsa.sign());
    }

    /** A registration capture reply for one face, as MockMDS's SBIServiceResponse builds it. */
    String captureReply(byte[] iso, String transactionId, String timestamp) throws Exception {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("bioType", "Face");
        payload.put("bioSubType", "");
        payload.put("deviceCode", "b692b595-3523-face-99fc-bd76e35f290f");
        payload.put("deviceServiceVersion", "0.9.5");
        payload.put("env", "Staging");
        payload.put("purpose", "Registration");
        payload.put("requestedScore", "60");
        payload.put("qualityScore", "95");
        payload.put("transactionId", transactionId);
        payload.put("timestamp", timestamp);
        payload.put("bioValue", URL.encodeToString(iso));
        ObjectNode bio = JSON.createObjectNode();
        bio.put("specVersion", "0.9.5");
        bio.put("data", sign(payload.toString()));
        bio.put("hash", "00");
        bio.putObject("error").put("errorCode", "0").put("errorInfo", "Success");
        ObjectNode reply = JSON.createObjectNode();
        reply.putArray("biometrics").add(bio);
        return reply.toString();
    }
}
