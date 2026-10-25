package io.mosip.registration.liveness.workbench;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.AnalysedFrame;
import io.mosip.registration.liveness.api.Decision;
import io.mosip.registration.liveness.api.FaceAnalyzer;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.Rect;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What happens after a pass: the signed capture is checked, decoded and bound to the session, or refused. */
class CaptureCheckTest {

    private static final String NONCE = "9f2c0a1100000000000000000000abcd";
    private static final FaceObservation FACE = FaceObservation.builder().faces(1, new Rect(60, 80, 120, 140))
            .landmarks(new float[] {90, 130, 150, 130, 120, 160, 100, 190, 140, 190}).build();
    private static TestDevice device;
    private static byte[] iso;
    private static FaceFrame isoFrame;

    @BeforeAll
    static void setUp() throws Exception {
        device = TestDevice.create();
        try (InputStream in = CaptureCheckTest.class.getResourceAsStream("/faces/mosip-mockmds-face.iso")) {
            iso = in.readAllBytes();
        }
        isoFrame = IsoFace.decode(iso);
    }

    @Test
    @DisplayName("A capture the device signed for this session, within the window, of the scene the session saw, is"
            + " bound, and the evidence digest is extended with it")
    void freshAndBound() throws Exception {
        long passedAt = Instant.parse("2026-10-25T10:15:00Z").toEpochMilli();

        CaptureCheck.Outcome o = check(capture(NONCE, "2026-10-25T10:15:05Z"), passedAt, policy());

        assertAll(() -> assertTrue(o.bound(), o.reason()), () -> assertNull(o.errorCode()),
                () -> assertTrue(o.signatureValid()), () -> assertTrue(o.fresh()),
                () -> assertEquals(1.0, o.binding().similarity(), 1e-9),
                () -> assertNotEquals("abcd", o.evidenceDigest()), () -> assertEquals(64, o.evidenceDigest().length()));
    }

    @Test
    @DisplayName("Not signed back with the session nonce, or signed more than bind.freshness.max.age.ms from the pass:"
            + " LIV-014, with the reason")
    void notFresh() throws Exception {
        long passedAt = Instant.parse("2026-10-25T10:15:00Z").toEpochMilli();

        CaptureCheck.Outcome otherSession = check(capture("0000", "2026-10-25T10:15:05Z"), passedAt, policy());
        CaptureCheck.Outcome late = check(capture(NONCE, "2026-10-25T10:16:30Z"), passedAt, policy());

        assertAll(() -> assertFalse(otherSession.bound()),
                () -> assertEquals(LivenessErrorCode.CAPTURE_BINDING_FAILED, otherSession.errorCode()),
                () -> assertTrue(otherSession.reason().contains("nonce"), otherSession.reason()),
                () -> assertFalse(late.bound()), () -> assertFalse(late.fresh()),
                () -> assertEquals(LivenessErrorCode.CAPTURE_BINDING_FAILED, late.errorCode()),
                () -> assertTrue(late.reason().contains("timestamp"), late.reason()));
    }

    @Test
    @DisplayName("A capture whose signature does not check is refused before anything else (LIV-014)")
    void badSignature() throws Exception {
        String reply = device.captureReply(iso, NONCE, "2026-10-25T10:15:05Z");
        String data = reply.substring(reply.indexOf("\"data\":\"") + 8);
        data = data.substring(0, data.indexOf('"'));
        String forged = data.substring(0, data.lastIndexOf('.') + 1) + "AAAA";

        CaptureCheck.Outcome o = check(SignedCapture.parse(reply.replace(data, forged)),
                Instant.parse("2026-10-25T10:15:00Z").toEpochMilli(), policy());

        assertAll(() -> assertFalse(o.bound()), () -> assertFalse(o.signatureValid()),
                () -> assertEquals(LivenessErrorCode.CAPTURE_BINDING_FAILED, o.errorCode()));
    }

    @Test
    @DisplayName("A capture that shows another scene than the session's last frames is not bound (LIV-014)")
    void otherScene() throws Exception {
        byte[] grey = new byte[isoFrame.rgb().length];
        java.util.Arrays.fill(grey, (byte) 128);
        FaceFrame elsewhere = new FaceFrame(isoFrame.width(), isoFrame.height(), grey, 0, 0, "test");
        List<AnalysedFrame> recent = Collections.singletonList(new AnalysedFrame(elsewhere,
                FaceObservation.builder().faces(1, new Rect(10, 10, 60, 70))
                        .landmarks(new float[] {20, 30, 50, 30, 35, 45, 25, 60, 45, 60}).build()));

        CaptureCheck.Outcome o = new CaptureCheck(analyzer()).check(passed(), capture(NONCE, "2026-10-25T10:15:05Z"),
                Instant.parse("2026-10-25T10:15:00Z").toEpochMilli(), policy(), recent);

        assertAll(() -> assertFalse(o.bound()), () -> assertTrue(o.fresh()),
                () -> assertEquals(LivenessErrorCode.CAPTURE_BINDING_FAILED, o.errorCode()),
                () -> assertTrue(o.binding().similarity() < 0.6, "similarity " + o.binding().similarity()));
    }

    @Test
    @DisplayName("An image that cannot be decoded is LIV-012; with the binder off a fresh capture counts without a"
            + " scene check, and the digest stays the session's")
    void undecodableAndBinderOff() throws Exception {
        long passedAt = Instant.parse("2026-10-25T10:15:00Z").toEpochMilli();
        SignedCapture broken = SignedCapture.parse(device.captureReply(new byte[] {1, 2, 3}, NONCE,
                "2026-10-25T10:15:05Z"));
        Map<String, String> off = new HashMap<String, String>();
        off.put("mosip.registration.face.liveness.bind.capture.enabled", "false");

        CaptureCheck.Outcome undecodable = check(broken, passedAt, policy());
        CaptureCheck.Outcome binderOff = check(capture(NONCE, "2026-10-25T10:15:05Z"), passedAt, policy(off));

        assertAll(() -> assertEquals(LivenessErrorCode.INVALID_FRAME_DATA, undecodable.errorCode()),
                () -> assertTrue(binderOff.bound()), () -> assertNull(binderOff.binding()),
                () -> assertEquals("abcd", binderOff.evidenceDigest()));
    }

    private static CaptureCheck.Outcome check(SignedCapture capture, long passedAt, LivenessPolicy policy) {
        return new CaptureCheck(analyzer()).check(passed(), capture, passedAt, policy,
                Collections.singletonList(new AnalysedFrame(isoFrame, FACE)));
    }

    private static SignedCapture capture(String transactionId, String timestamp) throws Exception {
        return SignedCapture.parse(device.captureReply(iso, transactionId, timestamp));
    }

    private static FaceAnalyzer analyzer() {
        return frame -> FACE;
    }

    private static LivenessResult passed() {
        return LivenessResult.builder(SessionState.PASSED, Decision.LIVE).nonce(NONCE).evidenceDigest("abcd").build();
    }

    private static LivenessPolicy policy() {
        return policy(new HashMap<String, String>());
    }

    private static LivenessPolicy policy(Map<String, String> extra) {
        Map<String, String> config = new HashMap<String, String>(extra);
        config.put("mosip.registration.face.liveness.enabled", "true");
        return LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);
    }

    @SuppressWarnings("unused")
    private static String utc(Instant t) {
        return DateTimeFormatter.ISO_INSTANT.format(t.truncatedTo(ChronoUnit.SECONDS));
    }
}
