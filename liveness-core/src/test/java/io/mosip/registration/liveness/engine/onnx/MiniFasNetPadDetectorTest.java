package io.mosip.registration.liveness.engine.onnx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.Rect;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Parity with the upstream Silent-Face-Anti-Spoofing pipeline ({@code CropImage._get_new_box}, {@code cv2.resize},
 * BGR 0-255, softmax index 1) run in Python on the same image and box. The tolerance, 0.015, covers OpenCV's
 * fixed-point resize: its pixels differ from exact bilinear by at most one grey level, which moved the probability by
 * at most 0.0093 on real face boxes.
 */
class MiniFasNetPadDetectorTest {

    private static MiniFasNetPadDetector v2;
    private static MiniFasNetPadDetector v1se;

    @BeforeAll
    static void load() throws Exception {
        v2 = MiniFasNetPadDetector.load(new File("../models/minifasnet_v2_2.7_80x80.onnx").toPath(), 2.7,
                "minifasnet_v2", "1.0");
        v1se = MiniFasNetPadDetector.load(new File("../models/minifasnet_v1se_4.0_80x80.onnx").toPath(), 4.0,
                "minifasnet_v1se", "1.0");
    }

    @AfterAll
    static void close() {
        v2.close();
        v1se.close();
    }

    @ParameterizedTest(name = "{0}: {9}")
    @CsvSource({
        "mosip-mockmds-face.png,  -1,   0,  56,  71, 128, 172, 0.99222, 0.99697, scale capped by the image",
        "mosip-mockmds-face.png, 520, 200, 576, 271, 128, 172, 0.00032, 0.00060, photo on a canvas: plain crop",
        "mosip-mockmds-face.png,   0, 200,  56, 271, 128, 172, 0.14976, 0.04327, photo at the canvas edge: shifted",
        "minivision-sample-T1.png, -1,  0, 119, 113, 170, 240, 0.99976, 1.00000, upstream real face",
        "minivision-sample-F1.png, -1,  0, 188, 110, 196, 240, 0.01400, 0.43244, upstream spoof 1",
        "minivision-sample-F2.png, -1,  0, 139, 217, 223, 298, 0.00041, 0.00588, upstream spoof 2"})
    @DisplayName("TC-ENG-PAD-01 the real-face probability matches the upstream pipeline on the same box")
    void matchesUpstream(String image, int canvasX, int canvasY, int x, int y, int w, int h, double expectedV2,
            double expectedV1se, String why) throws IOException {
        FaceFrame frame = YuNetDetectorTest.frame(placed(image, canvasX, canvasY));
        FaceObservation face = FaceObservation.builder().faces(1, new Rect(x, y, w, h)).build();

        assertEquals(expectedV2, v2.score(frame, face).liveProbability(), 0.015, "minifasnet_v2, " + why);
        assertEquals(expectedV1se, v1se.score(frame, face).liveProbability(), 0.015, "minifasnet_v1se, " + why);
    }

    /** The image as is ({@code canvasX} -1), or copied at the given offset onto a 1280x720 canvas of grey 128. */
    private static BufferedImage placed(String image, int canvasX, int canvasY) throws IOException {
        BufferedImage src;
        try (InputStream in = MiniFasNetPadDetectorTest.class.getResourceAsStream("/faces/" + image)) {
            src = ImageIO.read(in);
        }
        if (canvasX < 0) {
            return src;
        }
        BufferedImage canvas = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(new Color(128, 128, 128));
        g.fillRect(0, 0, 1280, 720);
        g.drawImage(src, canvasX, canvasY, null);
        g.dispose();
        return canvas;
    }
}
