package io.mosip.registration.liveness.eval;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FrameListener;
import io.mosip.registration.liveness.api.FrameSource;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessException;
import io.mosip.registration.liveness.api.SourceCapabilities;
import io.mosip.registration.liveness.desktop.JpegFrames;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Plays a recorded clip as a frame source, for tests and the evaluation: a folder of JPEGs in name order, or an MJPEG
 * file (JPEGs back to back, as {@code ffmpeg -c:v mjpeg} writes them). Frames are decoded like a live SBI stream's
 * ({@link JpegFrames}) and stamped at the given rate. Paced, a background thread delivers them in real time; unpaced,
 * {@link #start} delivers them all on the calling thread before it returns.
 */
public final class VideoFileFrameSource implements FrameSource {

    private final Path path;
    private final double fps;
    private final boolean paced;
    private volatile boolean running;

    /**
     * @param path  a folder of {@code .jpg} files or an MJPEG file
     * @param fps   the clip's frame rate; frame n is stamped {@code n * 1000 / fps} ms
     * @param paced whether to deliver in real time on a background thread
     */
    public VideoFileFrameSource(Path path, double fps, boolean paced) {
        this.path = path;
        this.fps = fps;
        this.paced = paced;
    }

    @Override
    public String id() {
        return "file:" + path.getFileName();
    }

    @Override
    public SourceCapabilities capabilities() {
        return SourceCapabilities.builder().device("file", String.valueOf(path.getFileName())).fps(fps, fps).build();
    }

    @Override
    public void start(FrameListener listener) throws LivenessException {
        List<byte[]> jpegs;
        try {
            jpegs = Files.isDirectory(path) ? folder(path) : split(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new LivenessException(LivenessErrorCode.DEVICE_UNAVAILABLE, "Cannot read the clip " + path);
        }
        running = true;
        if (!paced) {
            play(jpegs, listener);
            return;
        }
        Thread player = new Thread(() -> play(jpegs, listener), "clip-" + path.getFileName());
        player.setDaemon(true);
        player.start();
    }

    @Override
    public void stop() {
        running = false;
    }

    private void play(List<byte[]> jpegs, FrameListener listener) {
        long started = System.nanoTime();
        for (int n = 0; n < jpegs.size() && running; n++) {
            long dueMs = Math.round(n * 1000 / fps);
            if (paced) {
                long waitMs = dueMs - (System.nanoTime() - started) / 1_000_000L;
                if (waitMs > 0) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            FaceFrame frame;
            try {
                frame = JpegFrames.decode(jpegs.get(n), id(), n, dueMs);
            } catch (IllegalArgumentException e) {
                listener.onError(LivenessErrorCode.INVALID_FRAME_DATA, "Frame " + n + " is not a readable JPEG");
                continue;
            }
            listener.onFrame(frame);
        }
        listener.onEnd();
    }

    private static List<byte[]> folder(Path dir) throws IOException {
        List<byte[]> jpegs = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".jpg")).sorted()
                    .collect(Collectors.toList())) {
                jpegs.add(Files.readAllBytes(file));
            }
        }
        return jpegs;
    }

    /** Cuts back-to-back JPEGs at each start-of-image marker (FF D8 FF). */
    static List<byte[]> split(byte[] data) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i + 2 < data.length; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD8 && (data[i + 2] & 0xFF) == 0xFF) {
                starts.add(i);
            }
        }
        List<byte[]> jpegs = new ArrayList<>();
        for (int k = 0; k < starts.size(); k++) {
            int end = k + 1 < starts.size() ? starts.get(k + 1) : data.length;
            jpegs.add(Arrays.copyOfRange(data, starts.get(k), end));
        }
        return jpegs;
    }
}
