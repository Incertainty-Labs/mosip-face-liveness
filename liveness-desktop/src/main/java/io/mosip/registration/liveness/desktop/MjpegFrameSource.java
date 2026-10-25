package io.mosip.registration.liveness.desktop;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FrameListener;
import io.mosip.registration.liveness.api.FrameSource;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessException;
import io.mosip.registration.liveness.api.SourceCapabilities;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Reads an SBI 0.9.5 face live stream ({@code STREAM /stream}) straight from the device service, for example
 * MockMDS, and delivers decoded frames. Used by the Liveness Workbench; inside the Registration Client, frames come
 * from the client's own {@code Streamer} through {@link JpegFrames}.
 *
 * <p>Errors: no device or an SBI error instead of a stream makes {@link #start} throw LIV-001; an unreadable frame
 * is reported as LIV-012 and the stream goes on; a dropped stream is reconnected once, and a second drop is
 * reported as LIV-002 followed by {@code onEnd}. After {@link #stop()} no callbacks arrive.
 */
public final class MjpegFrameSource implements FrameSource {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final long FPS_WINDOW_MS = 2_000;

    private final String host;
    private final int port;
    private final String deviceId;
    private final int deviceSubId;
    private final SourceCapabilities device;
    private final FpsMeter fps = new FpsMeter(FPS_WINDOW_MS);

    private volatile boolean running;
    /** The device is about to end the stream itself, as MockMDS does when it captures. */
    private volatile boolean endExpected;
    private volatile Socket socket;
    private Thread reader;
    private long sequence;

    /**
     * @param host        the SBI host, normally 127.0.0.1
     * @param port        the SBI port (MockMDS starts at 4501)
     * @param deviceId    the face device's id from discovery
     * @param deviceSubId the sub id, 0 for a full face
     * @param device      device facts from discovery and device info; the measured rate is filled in here
     */
    public MjpegFrameSource(String host, int port, String deviceId, int deviceSubId, SourceCapabilities device) {
        this.host = host;
        this.port = port;
        this.deviceId = deviceId;
        this.deviceSubId = deviceSubId;
        this.device = device;
    }

    @Override
    public String id() {
        return "sbi095:" + deviceId;
    }

    @Override
    public SourceCapabilities capabilities() {
        return SourceCapabilities.builder()
                .device(device.vendor(), device.model())
                .certification(device.certification())
                .resolution(device.width(), device.height())
                .fps(device.nominalFps(), fps.fps())
                .devicePad(device.devicePad())
                .formats(device.formats())
                .build();
    }

    @Override
    public synchronized void start(FrameListener listener) throws LivenessException {
        if (running) {
            throw new IllegalStateException("Already started");
        }
        MjpegPartReader parts = openStream();
        running = true;
        endExpected = false;
        reader = new Thread(() -> read(parts, listener), "mjpeg-" + id());
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * The device is about to end the stream, for example because a capture is requested (MockMDS ends its stream when
     * it captures). The next drop then ends the stream quietly, with {@code onEnd} and no reconnect: a reconnect would
     * open a new stream and keep the device busy.
     */
    public void endExpected() {
        endExpected = true;
    }

    @Override
    public synchronized void stop() {
        running = false;
        closeSocket();
        if (reader != null) {
            reader.interrupt();
            reader = null;
        }
    }

    private void read(MjpegPartReader first, FrameListener listener) {
        MjpegPartReader parts = first;
        boolean reconnected = false;
        while (running) {
            byte[] part;
            String dropDetail;
            try {
                part = parts.nextPart();
                dropDetail = part == null ? "stream closed by the device" : null;
            } catch (IOException e) {
                part = null;
                dropDetail = e.toString();
            }
            if (part != null) {
                deliver(part, listener);
                continue;
            }
            if (!running) {
                return;
            }
            if (endExpected) {
                running = false;
                closeSocket();
                listener.onEnd();
                return;
            }
            if (!reconnected) {
                reconnected = true;
                try {
                    parts = openStream();
                    continue;
                } catch (LivenessException e) {
                    dropDetail = dropDetail + "; reconnect failed: " + e.getMessage();
                }
            }
            if (running) {
                listener.onError(LivenessErrorCode.DEVICE_CONNECTION_FAILED, dropDetail);
                listener.onEnd();
            }
            running = false;
            closeSocket();
            return;
        }
    }

    private void deliver(byte[] jpeg, FrameListener listener) {
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        try {
            FaceFrame frame = JpegFrames.decode(jpeg, id(), ++sequence, now);
            fps.frameAt(now);
            if (running) {
                listener.onFrame(frame);
            }
        } catch (RuntimeException e) {
            if (running) {
                listener.onError(LivenessErrorCode.INVALID_FRAME_DATA, e.getMessage());
            }
        }
    }

    /** Connects, sends STREAM and checks that a multipart stream comes back. */
    private MjpegPartReader openStream() throws LivenessException {
        closeSocket();
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            byte[] body = ("{\"deviceId\":\"" + deviceId + "\",\"deviceSubId\":\"" + deviceSubId
                    + "\",\"timeout\":\"0\"}").getBytes(StandardCharsets.US_ASCII);
            OutputStream out = s.getOutputStream();
            out.write(("STREAM /stream HTTP/1.1\r\nHost: " + host + ":" + port
                    + "\r\nContent-Type: application/json\r\nContent-Length: " + body.length + "\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();

            InputStream in = new BufferedInputStream(s.getInputStream());
            String status = readLine(in);
            String contentType = "";
            int contentLength = -1;
            for (String line = readLine(in); line != null && !line.isEmpty(); line = readLine(in)) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("content-type:")) {
                    contentType = lower.substring(13).trim();
                } else if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }
            if (status == null || !status.contains(" 200") || !contentType.startsWith("multipart/")) {
                String answer = readBody(in, contentLength);
                s.close();
                throw new LivenessException(LivenessErrorCode.DEVICE_UNAVAILABLE,
                        "SBI stream refused: " + status + " " + answer);
            }
            socket = s;
            return new MjpegPartReader(in);
        } catch (IOException e) {
            try {
                s.close();
            } catch (IOException ignored) {
                // already failing
            }
            throw new LivenessException(LivenessErrorCode.DEVICE_UNAVAILABLE,
                    "Cannot open the SBI stream at " + host + ":" + port + ": " + e);
        }
    }

    private void closeSocket() {
        Socket s = socket;
        socket = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0 && b != '\n') {
            line.write(b);
        }
        if (b < 0 && line.size() == 0) {
            return null;
        }
        String text = new String(line.toByteArray(), StandardCharsets.US_ASCII);
        return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
    }

    private static String readBody(InputStream in, int contentLength) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int limit = contentLength >= 0 ? contentLength : 4_096;
        int b;
        while (body.size() < limit && (b = in.read()) >= 0) {
            body.write(b);
        }
        return new String(body.toByteArray(), StandardCharsets.UTF_8);
    }
}
