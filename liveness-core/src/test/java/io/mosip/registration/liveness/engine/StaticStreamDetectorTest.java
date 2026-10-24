package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.Rect;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaticStreamDetectorTest {

    private static final FaceObservation FACE = FaceObservation.builder().faces(1, new Rect(56, 71, 128, 172)).build();
    private static byte[] still;
    private static int width;
    private static int height;

    @BeforeAll
    static void load() throws IOException {
        BufferedImage image;
        try (InputStream in = StaticStreamDetectorTest.class.getResourceAsStream("/faces/mosip-mockmds-face.png")) {
            image = ImageIO.read(in);
        }
        width = image.getWidth();
        height = image.getHeight();
        still = new byte[width * height * 3];
        for (int y = 0, i = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                still[i++] = (byte) (rgb >> 16);
                still[i++] = (byte) (rgb >> 8);
                still[i++] = (byte) rgb;
            }
        }
    }

    @Test
    @DisplayName("TC-ENG-23 the same image streamed with a face vetoes once it has been still for 1 s, not before")
    void frozenStreamVetoesAfterOneSecond() {
        StaticStreamDetector detector = new StaticStreamDetector();

        for (int n = 0; n < 10; n++) {
            assertFalse(detector.score(frame(n, n * 100L), FACE).veto(), "still for " + n * 100 + " ms");
        }
        assertTrue(detector.score(frame(10, 1000L), FACE).veto(), "still for 1000 ms");
    }

    @Test
    @DisplayName("TC-ENG-23 a live camera's sensor noise never vetoes, even with the scene still for 3 s")
    void sensorNoiseIsLive() {
        StaticStreamDetector detector = new StaticStreamDetector();
        Random noise = new Random(7);

        for (int n = 0; n <= 30; n++) {
            assertFalse(detector.score(noisy(frame(n, n * 100L), noise), FACE).veto(), "frame " + n);
        }
    }

    @Test
    @DisplayName("TC-ENG-23 frames without a face do not count: a covered lens gets guidance, not an attack verdict")
    void framesWithoutAFaceRestartTheCount() {
        StaticStreamDetector detector = new StaticStreamDetector();
        FaceObservation noFace = FaceObservation.builder().build();

        for (int n = 0; n <= 20; n++) {
            assertFalse(detector.score(frame(n, n * 100L), noFace).veto(), "no face, frame " + n);
        }
        for (int n = 21; n < 31; n++) {
            assertFalse(detector.score(frame(n, n * 100L), FACE).veto(), "face back for " + (n - 21) * 100 + " ms");
        }
        assertTrue(detector.score(frame(31, 3100L), FACE).veto(), "face back and still for 1000 ms");
    }

    @Test
    @DisplayName("TC-ENG-23 at a low frame rate it also needs five still frames, not just one second")
    void slowStreamNeedsFiveFrames() {
        StaticStreamDetector detector = new StaticStreamDetector();

        for (int n = 0; n < 4; n++) {
            assertFalse(detector.score(frame(n, n * 1000L / 3), FACE).veto(), "frame " + (n + 1) + " at 3 fps");
        }
        assertTrue(detector.score(frame(4, 1333L), FACE).veto(), "frame 5 at 3 fps");
    }

    @Test
    @DisplayName("TC-ENG-23 a frame delivered again (same sequence) is not another still frame")
    void repeatedFrameIsNotCounted() {
        StaticStreamDetector detector = new StaticStreamDetector();

        for (int n = 0; n < 4; n++) {
            FaceFrame frame = frame(n, n * 400L);
            for (int repeat = 0; repeat < 3; repeat++) {
                assertFalse(detector.score(frame, FACE).veto(), "frame " + (n + 1) + ", delivery " + (repeat + 1));
            }
        }
    }

    private static FaceFrame frame(long sequence, long timestampMs) {
        return new FaceFrame(width, height, still.clone(), timestampMs, sequence, "test");
    }

    /**
     * The frame with Gaussian sensor noise of sigma 2 grey levels per channel. On 64x48 grey that gives frame-to-frame
     * differences of about 0.4, at the low end of the 0.53 to 2.8 measured on a real webcam.
     */
    private static FaceFrame noisy(FaceFrame frame, Random noise) {
        byte[] rgb = frame.rgb();
        for (int i = 0; i < rgb.length; i++) {
            int v = (rgb[i] & 0xFF) + (int) Math.round(noise.nextGaussian() * 2);
            rgb[i] = (byte) Math.max(0, Math.min(255, v));
        }
        return frame;
    }
}
