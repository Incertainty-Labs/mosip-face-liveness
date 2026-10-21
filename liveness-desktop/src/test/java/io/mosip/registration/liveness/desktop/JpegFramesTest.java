package io.mosip.registration.liveness.desktop;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JpegFramesTest {

    @Test
    @DisplayName("A JPEG becomes an RGB FaceFrame with the given source, sequence and time")
    void jpegBecomesRgbFrame() throws IOException {
        byte[] jpeg = jpeg(16, 8, Color.RED, Color.BLUE);

        FaceFrame frame = JpegFrames.decode(jpeg, "sbi095:3", 7, 1_000);

        assertAll(() -> assertEquals(16, frame.width()),
                () -> assertEquals(8, frame.height()),
                () -> assertEquals(16 * 8 * 3, frame.rgb().length),
                () -> assertEquals("sbi095:3", frame.sourceId()),
                () -> assertEquals(7, frame.sequence()),
                () -> assertEquals(1_000, frame.timestampMs()),
                () -> assertRgbNear(frame, 2, 4, 255, 0, 0),
                () -> assertRgbNear(frame, 13, 4, 0, 0, 255));
    }

    @Test
    @DisplayName("Bytes that are not a JPEG image are rejected")
    void nonJpegIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> JpegFrames.decode(new byte[] {1, 2, 3}, "sbi095:3", 1, 0));

        assertEquals("Not a decodable image (3 bytes)", error.getMessage());
    }

    /** Left half one colour, right half the other, so channel order and orientation are both checked. */
    private static byte[] jpeg(int width, int height, Color left, Color right) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(left);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(right);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static void assertRgbNear(FaceFrame frame, int x, int y, int r, int g, int b) {
        int i = (y * frame.width() + x) * 3;
        int[] actual = {frame.rgb()[i] & 0xFF, frame.rgb()[i + 1] & 0xFF, frame.rgb()[i + 2] & 0xFF};
        assertTrue(Math.abs(actual[0] - r) < 40 && Math.abs(actual[1] - g) < 40 && Math.abs(actual[2] - b) < 40,
                "pixel (" + x + "," + y + ") was " + actual[0] + "," + actual[1] + "," + actual[2]);
    }
}
