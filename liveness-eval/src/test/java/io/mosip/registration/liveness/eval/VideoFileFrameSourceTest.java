package io.mosip.registration.liveness.eval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FrameListener;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VideoFileFrameSourceTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("A folder of JPEGs plays in name order, timestamps at the given rate, then ends")
    void jpegFolder() throws Exception {
        Files.write(dir.resolve("002.jpg"), jpeg(30, 20));
        Files.write(dir.resolve("000.jpg"), jpeg(10, 20));
        Files.write(dir.resolve("001.jpg"), jpeg(20, 20));
        Recorder recorder = new Recorder();

        new VideoFileFrameSource(dir, 20, false).start(recorder);

        assertAll(() -> assertEquals(3, recorder.frames.size()),
                () -> assertEquals(10, recorder.frames.get(0).width(), "000.jpg first"),
                () -> assertEquals(30, recorder.frames.get(2).width(), "002.jpg last"),
                () -> assertEquals(100L, recorder.frames.get(2).timestampMs(), "frame 2 at 20 fps"),
                () -> assertEquals(2L, recorder.frames.get(2).sequence()),
                () -> assertTrue(recorder.ended));
    }

    @Test
    @DisplayName("An MJPEG file (JPEGs back to back, as ffmpeg writes them) splits into its frames")
    void mjpegFile() throws Exception {
        ByteArrayOutputStream mjpeg = new ByteArrayOutputStream();
        mjpeg.write(jpeg(10, 20));
        mjpeg.write(jpeg(20, 20));
        mjpeg.write(jpeg(30, 20));
        Path file = dir.resolve("clip.mjpeg");
        Files.write(file, mjpeg.toByteArray());
        Recorder recorder = new Recorder();

        new VideoFileFrameSource(file, 10, false).start(recorder);

        assertAll(() -> assertEquals(3, recorder.frames.size()),
                () -> assertEquals(20, recorder.frames.get(1).width()),
                () -> assertEquals(200L, recorder.frames.get(2).timestampMs(), "frame 2 at 10 fps"),
                () -> assertTrue(recorder.ended));
    }

    @Test
    @DisplayName("A path that does not exist refuses to start with LIV-001")
    void missingPath() {
        LivenessException e = assertThrows(LivenessException.class,
                () -> new VideoFileFrameSource(dir.resolve("nope"), 20, false).start(new Recorder()));

        assertEquals(LivenessErrorCode.DEVICE_UNAVAILABLE, e.errorCode());
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    private static final class Recorder implements FrameListener {
        final List<FaceFrame> frames = new ArrayList<FaceFrame>();
        boolean ended;

        @Override
        public void onFrame(FaceFrame frame) {
            frames.add(frame);
        }

        @Override
        public void onError(LivenessErrorCode code, String detail) {
        }

        @Override
        public void onEnd() {
            ended = true;
        }
    }
}
