package io.mosip.registration.liveness.engine.onnx;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class YuNetDetectorTest {

    private static final File MODEL = new File("../models/face_detection_yunet_2023mar.onnx");
    private static YuNetDetector detector;

    @BeforeAll
    static void loadModel() throws Exception {
        detector = YuNetDetector.load(MODEL.toPath());
    }

    @AfterAll
    static void close() {
        detector.close();
    }

    @Test
    @DisplayName("Finds the one face with box, five points and score matching OpenCV's FaceDetectorYN")
    void matchesOpenCvOnTheMosipFace() throws IOException {
        List<FaceDetection> faces = detector.detect(frame(face()));

        assertEquals(1, faces.size());
        FaceDetection face = faces.get(0);
        // Reference: OpenCV 4.10 FaceDetectorYN, same model, image scaled x2 and padded to 640x640 (23 Oct 2026)
        float[] expectedBox = {57.7f, 77.1f, 124.8f, 170.7f};
        float[] expectedPoints = {92.3f, 135.2f, 149.1f, 139.4f, 118.1f, 170.2f, 90.8f, 194.0f, 142.0f, 197.5f};
        assertAll(() -> assertNear(expectedBox[0], face.x(), 1.5f, "x"),
                () -> assertNear(expectedBox[1], face.y(), 1.5f, "y"),
                () -> assertNear(expectedBox[2], face.width(), 1.5f, "width"),
                () -> assertNear(expectedBox[3], face.height(), 1.5f, "height"),
                () -> assertNear(0.949f, face.score(), 0.01f, "score"),
                () -> {
                    for (int i = 0; i < 10; i++) {
                        assertNear(expectedPoints[i], face.landmarks()[i], 1.5f, "landmark " + i);
                    }
                });
    }

    @Test
    @DisplayName("The minimum score decides what counts as a face (MOSIP face scores 0.949)")
    void minimumScoreIsApplied() throws IOException {
        assertAll(() -> assertEquals(1, detector.detect(frame(face()), 0.90f).size()),
                () -> assertEquals(0, detector.detect(frame(face()), 0.96f).size()));
    }

    @Test
    @DisplayName("A frame without a face gives no detections")
    void blankFrameHasNoFace() {
        BufferedImage blank = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = blank.createGraphics();
        g.setColor(new Color(128, 128, 128));
        g.fillRect(0, 0, 320, 240);
        g.dispose();

        assertTrue(detector.detect(frame(blank)).isEmpty());
    }

    @Test
    @DisplayName("Two faces side by side are both found, largest-score first")
    void twoFacesAreBothFound() throws IOException {
        BufferedImage one = face();
        BufferedImage two = new BufferedImage(one.getWidth() * 2, one.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = two.createGraphics();
        g.drawImage(one, 0, 0, null);
        g.drawImage(one, one.getWidth(), 0, null);
        g.dispose();

        List<FaceDetection> faces = detector.detect(frame(two));

        assertAll(() -> assertEquals(2, faces.size()),
                () -> assertTrue(faces.get(0).score() >= faces.get(1).score()));
    }

    static BufferedImage face() throws IOException {
        return ImageIO.read(YuNetDetectorTest.class.getResourceAsStream("/faces/mosip-mockmds-face.png"));
    }

    /** Packs an image into an RGB FaceFrame, the way the frame sources do. */
    static FaceFrame frame(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        byte[] rgb = new byte[w * h * 3];
        for (int y = 0, i = 0; y < h; y++) {
            for (int x = 0; x < w; x++, i += 3) {
                int p = image.getRGB(x, y);
                rgb[i] = (byte) (p >> 16);
                rgb[i + 1] = (byte) (p >> 8);
                rgb[i + 2] = (byte) p;
            }
        }
        return new FaceFrame(w, h, rgb, 0, 1, "test");
    }

    private static void assertNear(float expected, float actual, float tolerance, String what) {
        assertTrue(Math.abs(expected - actual) <= tolerance, what + ": expected " + expected + " but was " + actual);
    }
}
