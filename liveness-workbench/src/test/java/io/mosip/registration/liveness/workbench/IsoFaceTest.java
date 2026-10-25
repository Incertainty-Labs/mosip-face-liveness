package io.mosip.registration.liveness.workbench;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Decoding the signed ISO/IEC 19794-5 face with MOSIP's own biometrics-util. */
class IsoFaceTest {

    @Test
    @DisplayName("MockMDS's own ISO face record decodes to the 240x320 face it holds, the same pixels as the PNG"
            + " made from it")
    void decodesMockMdsRecord() throws Exception {
        byte[] iso;
        try (InputStream in = IsoFaceTest.class.getResourceAsStream("/faces/mosip-mockmds-face.iso")) {
            iso = in.readAllBytes();
        }
        BufferedImage expected = ImageIO.read(
                new File("../liveness-core/src/test/resources/faces/mosip-mockmds-face.png"));

        FaceFrame frame = IsoFace.decode(iso);

        assertAll(() -> assertEquals(240, frame.width()), () -> assertEquals(320, frame.height()),
                () -> assertTrue(meanDifference(expected, frame) < 2.0,
                        "mean difference " + meanDifference(expected, frame)));
    }

    @Test
    @DisplayName("Bytes that are not an ISO face record are an IOException (the Workbench reports LIV-012)")
    void notAnIsoRecord() {
        assertThrows(IOException.class, () -> IsoFace.decode(new byte[] {1, 2, 3, 4}));
    }

    private static double meanDifference(BufferedImage a, FaceFrame b) {
        byte[] rgb = b.rgb();
        long sum = 0;
        for (int y = 0, i = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getRGB(x, y);
                sum += Math.abs(((p >> 16) & 255) - (rgb[i++] & 255));
                sum += Math.abs(((p >> 8) & 255) - (rgb[i++] & 255));
                sum += Math.abs((p & 255) - (rgb[i++] & 255));
            }
        }
        return sum / (3.0 * a.getWidth() * a.getHeight());
    }
}
