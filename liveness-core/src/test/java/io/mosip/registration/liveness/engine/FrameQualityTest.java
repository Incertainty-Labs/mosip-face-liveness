package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FrameQualityTest {

    /** MOSIP's face box, from YuNet (see YuNetDetectorTest). */
    private static final float X = 57.7f, Y = 77.1f, W = 124.8f, H = 170.7f;

    @Test
    @DisplayName("A sharp face scores high, the same face blurred scores low")
    void blurLowersSharpness() throws IOException {
        BufferedImage face = face();

        float sharp = FrameQuality.of(frame(face), X, Y, W, H).sharpness();
        float blurred = FrameQuality.of(frame(gaussianBlur(face, 3)), X, Y, W, H).sharpness();

        assertAll(() -> assertTrue(sharp > 0.6f, "sharp " + sharp),
                () -> assertTrue(blurred < 0.3f, "blurred " + blurred));
    }

    @Test
    @DisplayName("Sharpness barely depends on how large the face is in the frame")
    void sharpnessIsRoughlyScaleInvariant() throws IOException {
        BufferedImage face = face();
        BufferedImage twice = new BufferedImage(face.getWidth() * 2, face.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = twice.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(face, 0, 0, twice.getWidth(), twice.getHeight(), null);
        g.dispose();

        float original = FrameQuality.of(frame(face), X, Y, W, H).sharpness();
        float scaled = FrameQuality.of(frame(twice), 2 * X, 2 * Y, 2 * W, 2 * H).sharpness();

        assertTrue(Math.abs(original - scaled) < 0.15f, "original " + original + " vs x2 " + scaled);
    }

    @Test
    @DisplayName("Brightness is the face box's mean luma: 0.5 for mid grey, lower when darkened")
    void brightnessIsMeanLumaOfTheFace() throws IOException {
        BufferedImage grey = new BufferedImage(240, 320, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = grey.createGraphics();
        g.setColor(new Color(128, 128, 128));
        g.fillRect(0, 0, 240, 320);
        g.dispose();
        BufferedImage face = face();

        float normal = FrameQuality.of(frame(face), X, Y, W, H).brightness();
        float dark = FrameQuality.of(frame(scaleColours(face, 0.4f)), X, Y, W, H).brightness();

        assertAll(() -> assertEquals(128f / 255f, FrameQuality.of(frame(grey), X, Y, W, H).brightness(), 0.002f),
                () -> assertEquals(normal * 0.4f, dark, 0.02f));
    }

    @Test
    @DisplayName("A face box partly outside the frame is measured on the part inside")
    void boxIsClippedToTheFrame() throws IOException {
        FrameQuality q = FrameQuality.of(frame(face()), -50, -50, 200, 200);

        assertAll(() -> assertTrue(q.brightness() > 0 && q.brightness() < 1),
                () -> assertTrue(q.sharpness() >= 0 && q.sharpness() <= 1));
    }

    private static BufferedImage face() throws IOException {
        return ImageIO.read(FrameQualityTest.class.getResourceAsStream("/faces/mosip-mockmds-face.png"));
    }

    private static BufferedImage gaussianBlur(BufferedImage image, float sigma) {
        int radius = (int) Math.ceil(3 * sigma);
        int size = 2 * radius + 1;
        float[] k = new float[size];
        float sum = 0;
        for (int i = 0; i < size; i++) {
            k[i] = (float) Math.exp(-(i - radius) * (i - radius) / (2 * sigma * sigma));
            sum += k[i];
        }
        for (int i = 0; i < size; i++) {
            k[i] /= sum;
        }
        BufferedImage rgb = copy(image);
        BufferedImage h = new ConvolveOp(new Kernel(size, 1, k), ConvolveOp.EDGE_NO_OP, null).filter(rgb, null);
        return new ConvolveOp(new Kernel(1, size, k), ConvolveOp.EDGE_NO_OP, null).filter(h, null);
    }

    private static BufferedImage scaleColours(BufferedImage image, float factor) {
        BufferedImage out = copy(image);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                int p = out.getRGB(x, y);
                int r = Math.round(((p >> 16) & 0xFF) * factor);
                int gr = Math.round(((p >> 8) & 0xFF) * factor);
                int b = Math.round((p & 0xFF) * factor);
                out.setRGB(x, y, (r << 16) | (gr << 8) | b);
            }
        }
        return out;
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }

    private static FaceFrame frame(BufferedImage image) {
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
}
