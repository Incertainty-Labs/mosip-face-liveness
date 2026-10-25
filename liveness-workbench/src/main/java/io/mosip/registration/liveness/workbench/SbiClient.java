package io.mosip.registration.liveness.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The SBI 0.9.5 calls the Workbench makes to a device service such as MockMDS: discovery, MockMDS's profile switch, and
 * the registration capture. Requests are written by hand, as the stream reader does, because MockMDS reads the body
 * length from a {@code Content-Length:} header at a fixed offset and HTTP libraries vary in how they spell it.
 */
public final class SbiClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 10000;

    private final String host;
    private final int port;

    /**
     * @param host the device service host, normally 127.0.0.1
     * @param port its port (MockMDS starts at 4501)
     */
    public SbiClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** One device from discovery. Immutable. */
    public static final class Device {
        private final String deviceId;
        private final List<Integer> subIds;
        private final String status;
        private final String certification;

        Device(String deviceId, List<Integer> subIds, String status, String certification) {
            this.deviceId = deviceId;
            this.subIds = Collections.unmodifiableList(subIds);
            this.status = status;
            this.certification = certification;
        }

        public String deviceId() {
            return deviceId;
        }

        public List<Integer> subIds() {
            return subIds;
        }

        /** @return for example Ready, Busy, Not Ready */
        public String status() {
            return status;
        }

        /** @return for example L0 */
        public String certification() {
            return certification;
        }
    }

    /**
     * @param type the modality, for example Face
     * @return the devices of that type
     * @throws IOException if the service cannot be reached or answers badly
     */
    public List<Device> discover(String type) throws IOException {
        ObjectNode body = JSON.createObjectNode().put("type", type);
        JsonNode reply = JSON.readTree(exchange("MOSIPDISC", "/device", body.toString(), READ_TIMEOUT_MS));
        List<Device> devices = new ArrayList<Device>();
        for (JsonNode d : reply) {
            List<Integer> subIds = new ArrayList<Integer>();
            for (JsonNode s : d.path("deviceSubId")) {
                subIds.add(Integer.parseInt(s.asText()));
            }
            devices.add(new Device(d.path("deviceId").asText(), subIds, d.path("deviceStatus").asText(),
                    d.path("certification").asText()));
        }
        return devices;
    }

    /**
     * MockMDS only: picks the profile its face device streams from, for example webcam, static or a clip profile.
     *
     * @param profileId the profile
     * @throws IOException if the service cannot be reached or refuses
     */
    public void setProfile(String profileId) throws IOException {
        ObjectNode body = JSON.createObjectNode().put("type", "Biometric Device").put("profileId", profileId);
        JsonNode reply = JSON.readTree(exchange("POST", "/admin/profile", body.toString(), READ_TIMEOUT_MS));
        if (!"0".equals(reply.path("errorCode").asText())) {
            throw new IOException("Profile " + profileId + " refused: " + reply.path("errorInfo").asText());
        }
    }

    /**
     * A registration capture of one full face.
     *
     * @param deviceId      the face device's id
     * @param transactionId the liveness session's nonce, which the device signs back (capture freshness)
     * @param timeoutMs     how long the device may take
     * @return the reply as the device sent it
     * @throws IOException if the service cannot be reached
     */
    public String capture(String deviceId, String transactionId, int timeoutMs) throws IOException {
        ObjectNode body = JSON.createObjectNode();
        body.put("env", "Staging");
        body.put("purpose", "Registration");
        body.put("specVersion", "0.9.5");
        body.put("timeout", String.valueOf(timeoutMs));
        body.put("captureTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        body.put("transactionId", transactionId);
        ArrayNode bio = body.putArray("bio");
        ObjectNode face = bio.addObject();
        face.put("type", "Face");
        face.put("count", "1");
        face.putArray("exception");
        face.put("requestedScore", "60");
        face.put("deviceId", deviceId);
        face.put("deviceSubId", "0");
        face.put("previousHash", "");
        body.putNull("customOpts");
        return exchange("RCAPTURE", "/capture", body.toString(), timeoutMs + READ_TIMEOUT_MS);
    }

    private String exchange(String method, String path, String json, int readTimeoutMs) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setSoTimeout(readTimeoutMs);
            OutputStream out = s.getOutputStream();
            String head = method + " " + path + " HTTP/1.1\r\n" + "Host: " + host + ":" + port + "\r\n"
                    + "Content-Type: application/json\r\n" + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
            return replyBody(s.getInputStream(), method);
        }
    }

    /** The body of an HTTP reply read to the end: its Content-Length bytes when given, else all of it. */
    private static String replyBody(InputStream in, String method) throws IOException {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            all.write(buf, 0, n);
        }
        String reply = new String(all.toByteArray(), StandardCharsets.UTF_8);
        int split = reply.indexOf("\r\n\r\n");
        if (split < 0 || !reply.startsWith("HTTP/1.") || !reply.substring(0, reply.indexOf("\r\n")).contains(" 200")) {
            throw new IOException(method + ": unexpected reply " + reply.substring(0, Math.min(80, reply.length())));
        }
        String body = reply.substring(split + 4);
        for (String header : reply.substring(0, split).split("\r\n")) {
            if (header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                int length = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim());
                return body.substring(0, Math.min(length, body.length()));
            }
        }
        return body.trim();
    }
}
