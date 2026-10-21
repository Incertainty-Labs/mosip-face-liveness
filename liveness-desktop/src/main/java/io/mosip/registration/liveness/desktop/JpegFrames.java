package io.mosip.registration.liveness.desktop;

import io.mosip.registration.liveness.api.FaceFrame;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/** Turns the JPEG frames of an SBI live stream into {@link FaceFrame}s (API.md §4.2). */
public final class JpegFrames {

    private JpegFrames() {
    }

    /**
     * @param jpeg        one JPEG image
     * @param sourceId    the producing {@code FrameSource} id
     * @param sequence    strictly increasing number per source
     * @param timestampMs capture time on the source's clock
     * @return the frame as packed RGB
     * @throws IllegalArgumentException if the bytes are not a decodable image (the caller reports LIV-012)
     */
    public static FaceFrame decode(byte[] jpeg, String sourceId, long sequence, long timestampMs) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(jpeg));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (image == null) {
            throw new IllegalArgumentException("Not a decodable image (" + jpeg.length + " bytes)");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        byte[] rgb = new byte[width * height * 3];
        for (int i = 0, j = 0; i < argb.length; i++, j += 3) {
            rgb[j] = (byte) (argb[i] >> 16);
            rgb[j + 1] = (byte) (argb[i] >> 8);
            rgb[j + 2] = (byte) argb[i];
        }
        return new FaceFrame(width, height, rgb, timestampMs, sequence, sourceId);
    }
}
