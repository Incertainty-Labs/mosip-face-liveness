package io.mosip.registration.liveness.desktop;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FrameListener;
import io.mosip.registration.liveness.api.FrameSource;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.SourceCapabilities;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The frames of a stream someone else reads: inside the Registration Client, its {@code Streamer} already holds the
 * device's only live stream (a second one would keep the device busy), and hands each JPEG to {@link #offer}. Frames
 * are decoded and delivered on this source's own thread, so the stream is never held up.
 */
public final class PushFrameSource implements FrameSource {

    private static final long FPS_WINDOW_MS = 2_000;

    private final String id;
    private final SourceCapabilities device;
    private final FpsMeter fps = new FpsMeter(FPS_WINDOW_MS);
    /** The newest JPEG not yet decoded; one only, so a slow analysis drops frames instead of falling behind. */
    private final BlockingQueue<byte[]> pending = new ArrayBlockingQueue<byte[]>(1);
    private volatile FrameListener listener;
    private Thread worker;
    private long sequence;

    /**
     * @param id     the source id, for example {@code sbi095:} and the device id
     * @param device device facts from discovery; the measured rate is filled in here
     */
    public PushFrameSource(String id, SourceCapabilities device) {
        this.id = id;
        this.device = device;
    }

    @Override
    public String id() {
        return id;
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
    public synchronized void start(FrameListener listener) {
        if (this.listener != null) {
            throw new IllegalStateException("Already started");
        }
        this.listener = listener;
        worker = new Thread(this::deliver, "push-" + id);
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Hands over one JPEG from the stream; returns at once.
     *
     * @param jpeg the frame as the device sent it
     */
    public void offer(byte[] jpeg) {
        if (listener != null) {
            // One producer, the client's stream thread: what is cleared here is only ever an older frame
            pending.clear();
            pending.offer(jpeg);
        }
    }

    /**
     * The client's stream has ended without being asked to, for example because the device was unplugged: reported as
     * LIV-002 and the end of the stream, after which nothing more is delivered.
     *
     * @param detail what happened, for the audit
     */
    public void streamEnded(String detail) {
        FrameListener l = listener;
        if (l == null) {
            return;
        }
        stop();
        l.onError(LivenessErrorCode.DEVICE_CONNECTION_FAILED, detail);
        l.onEnd();
    }

    @Override
    public synchronized void stop() {
        listener = null;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    private void deliver() {
        try {
            while (true) {
                byte[] jpeg = pending.take();
                long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
                FrameListener l = listener;
                if (l == null) {
                    return;
                }
                FaceFrame frame;
                try {
                    frame = JpegFrames.decode(jpeg, id, ++sequence, now);
                } catch (RuntimeException e) {
                    l.onError(LivenessErrorCode.INVALID_FRAME_DATA, e.getMessage());
                    continue;
                }
                fps.frameAt(now);
                l.onFrame(frame);
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }
}
