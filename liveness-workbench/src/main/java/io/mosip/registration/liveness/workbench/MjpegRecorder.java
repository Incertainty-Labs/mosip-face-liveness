package io.mosip.registration.liveness.workbench;

import io.mosip.registration.liveness.api.FaceFrame;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Records frames as back-to-back JPEGs (MJPEG), the format MockMDS's clip profiles and the evaluation's clip reader
 * play. Face recordings are personal data: keep them outside git, in {@code D:\Mossip-local\recordings}.
 */
public final class MjpegRecorder implements AutoCloseable {

    private static final float QUALITY = 0.92f;

    private final OutputStream out;

    /**
     * @param file the clip to write; replaced if it exists
     * @throws IOException if it cannot be created
     */
    public MjpegRecorder(Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        this.out = Files.newOutputStream(file);
    }

    /**
     * @param frame the next frame
     * @throws IOException if it cannot be written
     */
    public synchronized void write(FaceFrame frame) throws IOException {
        BufferedImage image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_RGB);
        byte[] rgb = frame.rgb();
        for (int y = 0, i = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++, i += 3) {
                image.setRGB(x, y, (rgb[i] & 255) << 16 | (rgb[i + 1] & 255) << 8 | (rgb[i + 2] & 255));
            }
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(QUALITY);
        java.io.ByteArrayOutputStream jpeg = new java.io.ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(jpeg)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        out.write(jpeg.toByteArray());
    }

    @Override
    public synchronized void close() throws IOException {
        out.close();
    }
}
