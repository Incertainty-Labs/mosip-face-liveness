package io.mosip.registration.liveness.workbench;

import io.mosip.biometrics.util.ConvertRequestDto;
import io.mosip.biometrics.util.face.FaceDecoder;
import io.mosip.registration.liveness.api.FaceFrame;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Decodes the signed ISO/IEC 19794-5 face into a frame, with MOSIP's biometrics-util, the same library and version the
 * Registration Client uses, so the binder compares what the client would see.
 */
public final class IsoFace {

    private IsoFace() {
    }

    /**
     * @param iso the ISO 19794-5:2011 face record from the signed capture
     * @return its image as an RGB frame
     * @throws IOException if it is not a face record that can be decoded
     */
    public static FaceFrame decode(byte[] iso) throws IOException {
        ConvertRequestDto request = new ConvertRequestDto();
        request.setModality("Face");
        request.setVersion("ISO19794_5_2011");
        request.setInputBytes(iso);
        BufferedImage image;
        try {
            image = FaceDecoder.convertFaceISOToBufferedImage(request);
        } catch (Exception e) {
            throw new IOException("Cannot decode the ISO face: " + e.getMessage(), e);
        }
        if (image == null) {
            throw new IOException("The ISO record holds no face image");
        }
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
        return new FaceFrame(w, h, rgb, System.currentTimeMillis(), 0, "sbi-capture");
    }
}
