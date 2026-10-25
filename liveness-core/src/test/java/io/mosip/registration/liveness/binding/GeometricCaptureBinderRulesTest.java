package io.mosip.registration.liveness.binding;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.AnalysedFrame;
import io.mosip.registration.liveness.api.BindingResult;
import io.mosip.registration.liveness.api.Decision;
import io.mosip.registration.liveness.api.FaceAnalyzer;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.Rect;
import io.mosip.registration.liveness.api.SessionState;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The binder's rules with a scripted analyzer: when a capture is never bound, and the digest it returns. */
class GeometricCaptureBinderRulesTest {

    private static final FaceObservation ONE_FACE = FaceObservation.builder().faces(1, new Rect(16, 8, 32, 32))
            .landmarks(new float[] {24, 20, 40, 20, 32, 28, 26, 34, 38, 34}).build();
    private static final FaceFrame FRAME = frame((byte) 90);

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"not passed", "no recent frames", "no face in the capture", "two faces in the capture"})
    @DisplayName("TC-ENG-22: a capture is never bound when liveness did not pass, the session kept no frames, or the"
            + " capture does not show exactly one face")
    void neverBound(String why) {
        FaceObservation captured = why.equals("no face in the capture") ? FaceObservation.builder().build()
                : why.equals("two faces in the capture") ? FaceObservation.builder().faces(2, ONE_FACE.box())
                        .landmarks(ONE_FACE.landmarks()).build() : ONE_FACE;
        LivenessResult result = why.equals("not passed")
                ? LivenessResult.builder(SessionState.FAILED, Decision.INCONCLUSIVE).build() : passed("abcd");
        List<AnalysedFrame> recent = why.equals("no recent frames") ? Collections.<AnalysedFrame>emptyList()
                : Collections.singletonList(new AnalysedFrame(FRAME, ONE_FACE));

        BindingResult r = binder(captured, 0.6).verify(result, FRAME, recent);

        assertAll(() -> assertFalse(r.bound(), why), () -> assertEquals(0.0, r.similarity(), 1e-9, why),
                () -> assertFalse(r.reason().isEmpty(), why));
    }

    @Test
    @DisplayName("The evidence digest becomes SHA-256(session digest, SHA-256 of the capture's pixels)")
    void digestAppendsTheCapture() throws Exception {
        BindingResult r = binder(ONE_FACE, 0.6).verify(passed("9f2c"), FRAME,
                Collections.singletonList(new AnalysedFrame(FRAME, ONE_FACE)));

        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        expected.update("9f2c".getBytes(StandardCharsets.UTF_8));
        expected.update(MessageDigest.getInstance("SHA-256").digest(FRAME.rgb()));
        StringBuilder hex = new StringBuilder();
        for (byte b : expected.digest()) {
            hex.append(String.format("%02x", b));
        }
        assertAll(() -> assertTrue(r.bound()), () -> assertEquals(hex.toString(), r.evidenceDigest()));
    }

    @Test
    @DisplayName("Same face position and landmarks but a different picture inside the box scores clearly lower than"
            + " the same picture: the pHash sees what the geometry cannot")
    void differentPictureAtTheSamePlaceScoresLower() {
        List<AnalysedFrame> recent = Collections.singletonList(new AnalysedFrame(FRAME, ONE_FACE));
        byte[] other = new byte[FRAME.rgb().length];
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 64; x++) {
                byte v = (byte) (((x / 4 + y / 4) % 2) * 200);
                int i = (y * 64 + x) * 3;
                other[i] = v;
                other[i + 1] = v;
                other[i + 2] = v;
            }
        }
        FaceFrame otherPicture = new FaceFrame(64, 48, other, 0, 0, "test");

        double same = binder(ONE_FACE, 0.6).verify(passed("a"), FRAME, recent).similarity();
        double different = binder(ONE_FACE, 0.6).verify(passed("a"), otherPicture, recent).similarity();

        assertAll(() -> assertEquals(1.0, same, 1e-9), () -> assertTrue(different < 0.9, "different " + different));
    }

    @Test
    @DisplayName("The pass mark is bind.min.similarity, as given: the same pair binds at 0.6 and not at 1.01")
    void thresholdIsTheConfiguredOne() {
        List<AnalysedFrame> recent = Collections.singletonList(new AnalysedFrame(FRAME, ONE_FACE));

        assertAll(() -> assertTrue(binder(ONE_FACE, 0.6).verify(passed("a"), FRAME, recent).bound()),
                () -> assertFalse(binder(ONE_FACE, 1.01).verify(passed("a"), FRAME, recent).bound()));
    }

    private static GeometricCaptureBinder binder(FaceObservation captured, double min) {
        FaceAnalyzer analyzer = frame -> captured;
        return new GeometricCaptureBinder(analyzer, min);
    }

    private static LivenessResult passed(String digest) {
        return LivenessResult.builder(SessionState.PASSED, Decision.LIVE).evidenceDigest(digest).build();
    }

    private static FaceFrame frame(byte value) {
        byte[] rgb = new byte[64 * 48 * 3];
        for (int i = 0; i < rgb.length; i++) {
            rgb[i] = (byte) (value + (i * 7 % 41));
        }
        return new FaceFrame(64, 48, rgb, 0, 0, "test");
    }
}
