package io.mosip.registration.liveness.desktop;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FrameListener;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessException;
import io.mosip.registration.liveness.api.SourceCapabilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MjpegFrameSourceTest {

    private static final SourceCapabilities DEVICE = SourceCapabilities.builder()
            .device("MOSIP", "FACE01").certification("L0").resolution(640, 480).fps(30, 0).build();

    private final List<ServerSocket> servers = new ArrayList<ServerSocket>();
    private MjpegFrameSource source;

    @AfterEach
    void tearDown() throws IOException {
        if (source != null) {
            source.stop();
        }
        for (ServerSocket server : servers) {
            server.close();
        }
    }

    @Test
    @DisplayName("Sends the SBI STREAM request and delivers decoded frames in order")
    void deliversFramesInOrder() throws Exception {
        FakeSbi sbi = new FakeSbi(connection -> {
            writeHeader(connection);
            for (int i = 0; i < 3; i++) {
                writePart(connection, jpeg(8 + i));
            }
            holdOpen(connection);
        });
        RecordingListener listener = new RecordingListener();
        source = new MjpegFrameSource("127.0.0.1", sbi.port(), "3", 0, DEVICE);

        source.start(listener);

        List<FaceFrame> frames = listener.frames(3);
        String request = sbi.requests.poll(5, TimeUnit.SECONDS);
        assertAll(() -> assertEquals("sbi095:3", source.id()),
                () -> assertEquals(8, frames.get(0).width()),
                () -> assertEquals(10, frames.get(2).width()),
                () -> assertEquals(1, frames.get(0).sequence()),
                () -> assertEquals(3, frames.get(2).sequence()),
                () -> assertEquals("sbi095:3", frames.get(0).sourceId()),
                () -> assertTrue(request.startsWith("STREAM /stream HTTP/1.1"), request),
                () -> assertTrue(request.contains("\"deviceId\":\"3\""), request),
                () -> assertTrue(request.contains("\"deviceSubId\":\"0\""), request));
    }

    @Test
    @DisplayName("Capabilities keep the device facts and add the measured frame rate")
    void capabilitiesCarryMeasuredFps() throws Exception {
        FakeSbi sbi = new FakeSbi(connection -> {
            writeHeader(connection);
            for (int i = 0; i < 6; i++) {
                writePart(connection, jpeg(8));
                pause(50);
            }
            holdOpen(connection);
        });
        RecordingListener listener = new RecordingListener();
        source = new MjpegFrameSource("127.0.0.1", sbi.port(), "3", 0, DEVICE);
        source.start(listener);
        listener.frames(6);

        SourceCapabilities caps = source.capabilities();

        assertAll(() -> assertEquals("FACE01", caps.model()), () -> assertEquals("L0", caps.certification()),
                () -> assertEquals(30.0, caps.nominalFps(), 1e-9),
                () -> assertTrue(caps.measuredFps() > 10 && caps.measuredFps() < 30, "measured " + caps.measuredFps()));
    }

    @Test
    @DisplayName("Nothing listening on the port: start fails with LIV-001")
    void noDeviceIsLiv001() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }
        source = new MjpegFrameSource("127.0.0.1", freePort, "3", 0, DEVICE);

        LivenessException error = assertThrows(LivenessException.class, () -> source.start(new RecordingListener()));

        assertEquals(LivenessErrorCode.DEVICE_UNAVAILABLE, error.errorCode());
    }

    @Test
    @DisplayName("The device answers with an SBI error instead of a stream: start fails with LIV-001")
    void sbiErrorIsLiv001() throws IOException {
        FakeSbi sbi = new FakeSbi(connection -> {
            byte[] body = "{\"errorCode\":\"605\",\"errorInfo\":\"Device is not connected\"}"
                    .getBytes(StandardCharsets.US_ASCII);
            write(connection, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + body.length
                    + "\r\n\r\n");
            connection.getOutputStream().write(body);
            connection.close();
        });
        source = new MjpegFrameSource("127.0.0.1", sbi.port(), "3", 0, DEVICE);

        LivenessException error = assertThrows(LivenessException.class, () -> source.start(new RecordingListener()));

        assertAll(() -> assertEquals(LivenessErrorCode.DEVICE_UNAVAILABLE, error.errorCode()),
                () -> assertTrue(error.getMessage().contains("605"), error.getMessage()));
    }

    @Test
    @DisplayName("An unreadable frame is reported as LIV-012 and the stream goes on")
    void badFrameIsLiv012AndStreamContinues() throws Exception {
        FakeSbi sbi = new FakeSbi(connection -> {
            writeHeader(connection);
            writePart(connection, new byte[] {1, 2, 3});
            writePart(connection, jpeg(12));
            holdOpen(connection);
        });
        RecordingListener listener = new RecordingListener();
        source = new MjpegFrameSource("127.0.0.1", sbi.port(), "3", 0, DEVICE);

        source.start(listener);

        assertAll(() -> assertEquals(12, listener.frames(1).get(0).width()),
                () -> assertEquals(Collections.singletonList(LivenessErrorCode.INVALID_FRAME_DATA), listener.errors()));
    }

    @Test
    @DisplayName("A dropped stream is reconnected once; a second drop is LIV-002 and the end")
    void dropReconnectsOnceThenLiv002() throws Exception {
        FakeSbi sbi = new FakeSbi(connection -> {
            writeHeader(connection);
            writePart(connection, jpeg(8));
            connection.close();
        });
        RecordingListener listener = new RecordingListener();
        source = new MjpegFrameSource("127.0.0.1", sbi.port(), "3", 0, DEVICE);

        source.start(listener);

        assertTrue(listener.ended.await(5, TimeUnit.SECONDS), "onEnd was not called");
        assertAll(() -> assertEquals(2, listener.frameCount()),
                () -> assertEquals(Collections.singletonList(LivenessErrorCode.DEVICE_CONNECTION_FAILED),
                        listener.errors()),
                () -> assertEquals(2, sbi.connections()));
    }

    @Test
    @DisplayName("Once the end is expected (MockMDS ends its stream when it captures), a drop ends the stream quietly:"
            + " no reconnect, which would keep the device busy, and no error")
    void expectedEndDoesNotReconnect() throws Exception {
        FakeSbi sbi = new FakeSbi(connection -> {
            writeHeader(connection);
            writePart(connection, jpeg(8));
            pause(300);
            connection.close();
        });
        RecordingListener listener = new RecordingListener();
        source = new MjpegFrameSource("127.0.0.1", sbi.port(), "3", 0, DEVICE);
        source.start(listener);
        listener.frames(1);

        source.endExpected();

        assertTrue(listener.ended.await(5, TimeUnit.SECONDS), "onEnd was not called");
        assertAll(() -> assertEquals(1, sbi.connections(), "no reconnect"),
                () -> assertTrue(listener.errors().isEmpty(), "errors: " + listener.errors()));
    }

    @Test
    @DisplayName("After stop no more callbacks arrive")
    void stopEndsDelivery() throws Exception {
        FakeSbi sbi = new FakeSbi(connection -> {
            writeHeader(connection);
            while (true) {
                writePart(connection, jpeg(8));
                pause(20);
            }
        });
        RecordingListener listener = new RecordingListener();
        source = new MjpegFrameSource("127.0.0.1", sbi.port(), "3", 0, DEVICE);
        source.start(listener);
        listener.frames(2);

        source.stop();
        int afterStop = listener.frameCount();
        pause(300);

        assertAll(() -> assertTrue(listener.frameCount() <= afterStop + 1, "frames kept coming after stop"),
                () -> assertTrue(listener.errors().isEmpty(), "errors after stop: " + listener.errors()),
                () -> assertEquals(1, listener.ended.getCount(), "a stop is not an end of stream"));
    }

    // --- fake SBI device ---------------------------------------------------------------------------------------

    private interface Handler {
        void handle(Socket connection) throws Exception;
    }

    private final class FakeSbi {
        final BlockingQueue<String> requests = new LinkedBlockingQueue<String>();
        private final ServerSocket server;
        private volatile int connections;

        FakeSbi(Handler handler) throws IOException {
            server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
            servers.add(server);
            Thread accept = new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket connection = server.accept();
                        connections++;
                        requests.add(readRequest(connection.getInputStream()));
                        Thread worker = new Thread(() -> {
                            try {
                                handler.handle(connection);
                            } catch (Exception ignored) {
                                // the client went away
                            }
                        });
                        worker.setDaemon(true);
                        worker.start();
                    } catch (IOException e) {
                        return;
                    }
                }
            });
            accept.setDaemon(true);
            accept.start();
        }

        int port() {
            return server.getLocalPort();
        }

        int connections() {
            return connections;
        }
    }

    /** Reads the request line, headers and a Content-Length body. */
    private static String readRequest(InputStream in) throws IOException {
        StringBuilder head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n")) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            head.append((char) b);
        }
        int length = 0;
        for (String line : head.toString().split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                length = Integer.parseInt(line.substring(15).trim());
            }
        }
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n < 0) {
                break;
            }
            read += n;
        }
        return head + new String(body, 0, read, StandardCharsets.US_ASCII);
    }

    private static void writeHeader(Socket connection) throws IOException {
        write(connection, "HTTP/1.0 200 OK\r\nConnection: close\r\n"
                + "Content-Type: multipart/x-mixed-replace; boundary=--BoundaryString\r\n\r\n");
    }

    private static void writePart(Socket connection, byte[] jpeg) throws IOException {
        OutputStream out = connection.getOutputStream();
        out.write(("--BoundaryString\r\nContent-Type:image/jpeg\r\nContent-Length:" + jpeg.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(jpeg);
        out.write("\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static void write(Socket connection, String text) throws IOException {
        connection.getOutputStream().write(text.getBytes(StandardCharsets.US_ASCII));
        connection.getOutputStream().flush();
    }

    private static void holdOpen(Socket connection) throws IOException {
        while (connection.getInputStream().read() >= 0) {
            // keep the stream open until the client closes it
        }
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static byte[] jpeg(int width) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, 4, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    // --- listener ----------------------------------------------------------------------------------------------

    private static final class RecordingListener implements FrameListener {
        private final BlockingQueue<FaceFrame> frames = new LinkedBlockingQueue<FaceFrame>();
        private final List<FaceFrame> seen = Collections.synchronizedList(new ArrayList<FaceFrame>());
        private final List<LivenessErrorCode> errors = Collections.synchronizedList(new ArrayList<LivenessErrorCode>());
        final java.util.concurrent.CountDownLatch ended = new java.util.concurrent.CountDownLatch(1);

        @Override
        public void onFrame(FaceFrame frame) {
            seen.add(frame);
            frames.add(frame);
        }

        @Override
        public void onError(LivenessErrorCode code, String detail) {
            errors.add(code);
        }

        @Override
        public void onEnd() {
            ended.countDown();
        }

        List<FaceFrame> frames(int count) throws InterruptedException {
            List<FaceFrame> result = new ArrayList<FaceFrame>();
            for (int i = 0; i < count; i++) {
                FaceFrame frame = frames.poll(5, TimeUnit.SECONDS);
                if (frame == null) {
                    throw new AssertionError("only " + i + " of " + count + " frames arrived");
                }
                result.add(frame);
            }
            return result;
        }

        int frameCount() {
            return seen.size();
        }

        List<LivenessErrorCode> errors() {
            synchronized (errors) {
                return new ArrayList<LivenessErrorCode>(errors);
            }
        }
    }
}
