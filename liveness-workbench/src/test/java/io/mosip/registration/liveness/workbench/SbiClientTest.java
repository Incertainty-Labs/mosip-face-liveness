package io.mosip.registration.liveness.workbench;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The Workbench's SBI calls against a stand-in that reads requests exactly as MockMDS does. */
class SbiClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("Discovery finds the face device: MOSIPDISC /device with {\"type\":\"Face\"}, its id and status read"
            + " from the reply")
    void discover() throws Exception {
        try (FakeMockMds mds = new FakeMockMds()) {
            mds.reply("MOSIPDISC", "[{\"deviceId\":\"1\",\"deviceStatus\":\"Ready\",\"certification\":\"L0\","
                    + "\"serviceVersion\":\"0.9.5\",\"deviceSubId\":[\"0\"],\"callbackId\":\"http://127.0.0.1:4501/\","
                    + "\"specVersion\":[\"0.9.5\"],\"purpose\":\"Registration\"}]");

            List<SbiClient.Device> devices = new SbiClient("127.0.0.1", mds.port()).discover("Face");

            FakeMockMds.Request r = mds.requests.get(0);
            assertAll(() -> assertEquals("MOSIPDISC /device HTTP/1.1", r.line),
                    () -> assertEquals("Face", JSON.readTree(r.body).get("type").asText()),
                    () -> assertEquals(1, devices.size()),
                    () -> assertEquals("1", devices.get(0).deviceId()),
                    () -> assertEquals(Collections.singletonList(0), devices.get(0).subIds()),
                    () -> assertEquals("Ready", devices.get(0).status()));
        }
    }

    @Test
    @DisplayName("A profile is set with POST /admin/profile, and a reply other than errorCode 0 is an error")
    void profile() throws Exception {
        try (FakeMockMds mds = new FakeMockMds()) {
            mds.reply("POST", "{\"errorCode\":\"0\",\"errorInfo\":\"Success\"}");
            SbiClient client = new SbiClient("127.0.0.1", mds.port());

            client.setProfile("webcam");
            mds.reply("POST", "{\"errorCode\":\"999\",\"errorInfo\":\"broken\"}");

            JsonNode body = JSON.readTree(mds.requests.get(0).body);
            assertAll(() -> assertEquals("POST /admin/profile HTTP/1.1", mds.requests.get(0).line),
                    () -> assertEquals("Biometric Device", body.get("type").asText()),
                    () -> assertEquals("webcam", body.get("profileId").asText()),
                    () -> assertThrows(IOException.class, () -> client.setProfile("static")));
        }
    }

    @Test
    @DisplayName("RCAPTURE /capture asks for one full face (sub id 0) for registration, with the session nonce as"
            + " transactionId, and returns the reply as sent")
    void capture() throws Exception {
        try (FakeMockMds mds = new FakeMockMds()) {
            mds.reply("RCAPTURE", "{\"biometrics\":[]}");

            String reply = new SbiClient("127.0.0.1", mds.port()).capture("1", "9f2c0a11", 10000);

            FakeMockMds.Request r = mds.requests.get(0);
            JsonNode body = JSON.readTree(r.body);
            JsonNode bio = body.get("bio").get(0);
            assertAll(() -> assertEquals("RCAPTURE /capture HTTP/1.1", r.line),
                    () -> assertEquals("{\"biometrics\":[]}", reply.trim()),
                    () -> assertEquals("9f2c0a11", body.get("transactionId").asText()),
                    () -> assertEquals("Registration", body.get("purpose").asText()),
                    () -> assertEquals("0.9.5", body.get("specVersion").asText()),
                    () -> assertEquals("10000", body.get("timeout").asText()),
                    () -> assertEquals("Face", bio.get("type").asText()),
                    () -> assertEquals("1", bio.get("count").asText()),
                    () -> assertEquals("1", bio.get("deviceId").asText()),
                    () -> assertEquals("0", bio.get("deviceSubId").asText()),
                    () -> assertTrue(body.has("captureTime")));
        }
    }

    @Test
    @DisplayName("No device service on the port is an IOException, which the Workbench reports as LIV-001")
    void noService() throws Exception {
        int free;
        try (ServerSocket s = new ServerSocket(0)) {
            free = s.getLocalPort();
        }
        SbiClient client = new SbiClient("127.0.0.1", free);

        assertThrows(IOException.class, () -> client.discover("Face"));
    }
}
