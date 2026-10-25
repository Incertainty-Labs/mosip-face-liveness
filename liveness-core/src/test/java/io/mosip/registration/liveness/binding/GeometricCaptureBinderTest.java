package io.mosip.registration.liveness.binding;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.AnalysedFrame;
import io.mosip.registration.liveness.api.BindingResult;
import io.mosip.registration.liveness.api.Decision;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.engine.onnx.OnnxFaceAnalyzer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The Desktop binder with the real face analyzer, on the test faces (MOSIP's mock face, Minivision's samples). */
class GeometricCaptureBinderTest {

    static final double MIN_SIMILARITY = 0.60;
    private static OnnxFaceAnalyzer analyzer;

    @BeforeAll
    static void load() throws Exception {
        analyzer = OnnxFaceAnalyzer.load(new File("../models").toPath(), 0.7);
    }

    @AfterAll
    static void close() {
        analyzer.close();
    }

    @Test
    @DisplayName("TC-ENG-21: the capture of the scene the session just saw is bound, with a similarity near 1")
    void sameSceneIsBound() throws IOException {
        FaceFrame stream = frame(image("mosip-mockmds-face.png"));

        BindingResult r = new GeometricCaptureBinder(analyzer, MIN_SIMILARITY).verify(passed(), stream,
                recent(stream));

        assertAll(() -> assertTrue(r.bound(), r.reason()),
                () -> assertTrue(r.similarity() > 0.95, "similarity " + r.similarity()));
    }

    @Test
    @DisplayName("TC-ENG-21: a capture a little moved from the last stream frame (8 px, as a live face drifts) is bound")
    void driftedSceneIsBound() throws IOException {
        BufferedImage face = image("mosip-mockmds-face.png");
        FaceFrame stream = frame(face);
        FaceFrame capture = frame(shifted(face, 8, 4));

        BindingResult r = new GeometricCaptureBinder(analyzer, MIN_SIMILARITY).verify(passed(), capture,
                recent(stream));

        System.out.println("drifted: similarity " + r.similarity());
        assertTrue(r.bound(), "similarity " + r.similarity());
    }

    @Test
    @DisplayName("TC-ENG-21: the same scene and the drifted scene captured at twice the stream's resolution are bound"
            + " as at the stream's own")
    void higherResolutionCaptureIsBound() throws IOException {
        BufferedImage face = image("mosip-mockmds-face.png");
        FaceFrame stream = frame(face);
        int w = face.getWidth() * 2;
        int h = face.getHeight() * 2;
        GeometricCaptureBinder binder = new GeometricCaptureBinder(analyzer, MIN_SIMILARITY);

        BindingResult same = binder.verify(passed(), frame(scaled(face, w, h)), recent(stream));
        BindingResult drifted = binder.verify(passed(), frame(scaled(shifted(face, 8, 4), w, h)), recent(stream));
        double driftedAtStreamSize = binder.verify(passed(), frame(shifted(face, 8, 4)), recent(stream)).similarity();

        System.out.println("x2: same " + same.similarity() + ", drifted " + drifted.similarity());
        assertAll(() -> assertTrue(same.similarity() > 0.9, "same " + same.similarity()),
                () -> assertTrue(drifted.bound(), "drifted " + drifted.similarity()),
                () -> assertTrue(Math.abs(drifted.similarity() - driftedAtStreamSize) < 0.1,
                        "drifted " + drifted.similarity() + " vs " + driftedAtStreamSize + " at the stream's size"));
    }

    @Test
    @DisplayName("TC-ENG-22: a capture of another person, where the live face was, is not bound (LIV-014)")
    void otherPersonIsNotBound() throws IOException {
        FaceFrame stream = frame(image("mosip-mockmds-face.png"));
        FaceFrame capture = frame(scaled(image("minivision-sample-T1.png"), 240, 320));

        BindingResult r = new GeometricCaptureBinder(analyzer, MIN_SIMILARITY).verify(passed(), capture,
                recent(stream));

        System.out.println("other person: similarity " + r.similarity());
        assertAll(() -> assertFalse(r.bound(), "similarity " + r.similarity()),
                () -> assertTrue(r.similarity() < MIN_SIMILARITY - 0.1, "a clear margin: " + r.similarity()));
    }

    /** The image moved right by dx and down by dy, the uncovered edge filled with the edge pixels. */
    static BufferedImage shifted(BufferedImage src, int dx, int dy) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                out.setRGB(x, y, src.getRGB(Math.max(0, x - dx), Math.max(0, y - dy)));
            }
        }
        return out;
    }

    static BufferedImage scaled(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    static LivenessResult passed() {
        return LivenessResult.builder(SessionState.PASSED, Decision.LIVE)
                .evidenceDigest("9f2c00000000000000000000000000000000000000000000000000000000abcd").build();
    }

    /** Five analysed copies of the frame, as the session's last frames. */
    static List<AnalysedFrame> recent(FaceFrame frame) {
        List<AnalysedFrame> frames = new ArrayList<AnalysedFrame>();
        for (int i = 0; i < 5; i++) {
            frames.add(new AnalysedFrame(frame, analyzer.analyze(frame)));
        }
        return frames;
    }

    static BufferedImage image(String name) throws IOException {
        try (InputStream in = GeometricCaptureBinderTest.class.getResourceAsStream("/faces/" + name)) {
            return ImageIO.read(in);
        }
    }

    static FaceFrame frame(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        byte[] rgb = new byte[w * h * 3];
        for (int y = 0, i = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = image.getRGB(x, y);
                rgb[i++] = (byte) (p >> 16);
                rgb[i++] = (byte) (p >> 8);
                rgb[i++] = (byte) p;
            }
        }
        return new FaceFrame(w, h, rgb, 0, 0, "test");
    }
}
