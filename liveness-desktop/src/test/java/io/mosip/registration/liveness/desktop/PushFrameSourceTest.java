package io.mosip.registration.liveness.desktop;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FrameListener;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.SourceCapabilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The frame source the Registration Client's own stream feeds, one JPEG at a time. */
class PushFrameSourceTest {

    private final PushFrameSource source = new PushFrameSource("sbi095:3", SourceCapabilities.builder()
            .device("MOSIP", "MockMDS face").build());
    private final Events events = new Events();

    @AfterEach
    void stop() {
        source.stop();
    }

    @Test
    @DisplayName("Each JPEG the client's stream hands over arrives as a decoded frame, numbered in order, with the"
            + " source's id")
    void jpegsBecomeFrames() throws Exception {
        source.start(events);

        source.offer(jpeg(32));
        FaceFrame first = events.frame();
        source.offer(jpeg(48));
        FaceFrame second = events.frame();

        assertAll(() -> assertEquals(32, first.width()), () -> assertEquals(48, second.width()),
                () -> assertEquals(1, first.sequence()), () -> assertEquals(2, second.sequence()),
                () -> assertEquals("sbi095:3", second.sourceId()));
    }

    @Test
    @DisplayName("While a frame is still being analysed only the newest JPEG waits; older ones are dropped, so the"
            + " analysis never falls behind the stream")
    void keepsOnlyTheNewestWhileBusy() throws Exception {
        CountDownLatch inFirst = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        source.start(new Events() {
            @Override
            public void onFrame(FaceFrame frame) {
                super.onFrame(frame);
                if (frame.sequence() == 1) {
                    inFirst.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }.into(events));

        source.offer(jpeg(16));
        assertTrue(inFirst.await(5, TimeUnit.SECONDS));
        source.offer(jpeg(20));
        source.offer(jpeg(24));
        source.offer(jpeg(28));
        release.countDown();

        FaceFrame first = events.frame();
        FaceFrame next = events.frame();
        assertAll(() -> assertEquals(16, first.width()), () -> assertEquals(28, next.width(), "the newest"),
                () -> assertNull(events.received.poll(300, TimeUnit.MILLISECONDS), "the older two dropped"));
    }

    @Test
    @DisplayName("An unreadable frame is reported as LIV-012 and the stream goes on")
    void unreadableFrame() throws Exception {
        source.start(events);

        source.offer(new byte[] {1, 2, 3});
        Object error = events.next();
        source.offer(jpeg(32));

        assertAll(() -> assertEquals(LivenessErrorCode.INVALID_FRAME_DATA, error),
                () -> assertEquals(32, events.frame().width()));
    }

    @Test
    @DisplayName("When the client's stream drops, the session hears LIV-002 and the end of the stream, and nothing"
            + " after that")
    void streamDrop() throws Exception {
        source.start(events);

        source.streamEnded("stream closed by the device");
        Object error = events.next();
        Object end = events.next();
        source.offer(jpeg(32));

        assertAll(() -> assertEquals(LivenessErrorCode.DEVICE_CONNECTION_FAILED, error),
                () -> assertEquals("end", end),
                () -> assertNull(events.received.poll(300, TimeUnit.MILLISECONDS), "nothing after the end"));
    }

    static byte[] jpeg(int width) throws IOException {
        BufferedImage image = new BufferedImage(width, 24, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    /** What the source delivered, in order. */
    static class Events implements FrameListener {
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();

        /** Records into another instance's queue, for a subclass that only adds behaviour. */
        Events into(Events other) {
            received = other.received;
            return this;
        }

        @Override
        public void onFrame(FaceFrame frame) {
            received.add(frame);
        }

        @Override
        public void onError(LivenessErrorCode code, String detail) {
            received.add(code);
        }

        @Override
        public void onEnd() {
            received.add("end");
        }

        Object next() throws InterruptedException {
            Object event = received.poll(5, TimeUnit.SECONDS);
            assertNotNull(event, "an event within 5 s");
            return event;
        }

        FaceFrame frame() throws InterruptedException {
            return (FaceFrame) next();
        }
    }
}
