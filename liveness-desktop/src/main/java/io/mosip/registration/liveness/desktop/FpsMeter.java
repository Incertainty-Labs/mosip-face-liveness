package io.mosip.registration.liveness.desktop;

import java.util.ArrayDeque;
import java.util.Deque;

/** Measures a stream's frame rate over a sliding window (2 s for SBI streams). Thread-safe. */
final class FpsMeter {

    private final long windowMs;
    private final Deque<Long> times = new ArrayDeque<Long>();

    FpsMeter(long windowMs) {
        this.windowMs = windowMs;
    }

    /** @param timeMs when a frame arrived */
    synchronized void frameAt(long timeMs) {
        times.addLast(timeMs);
        while (times.size() > 1 && timeMs - times.peekFirst() > windowMs) {
            times.removeFirst();
        }
    }

    /** @return frames per second over the window; 0 until two frames have arrived */
    synchronized double fps() {
        if (times.size() < 2) {
            return 0.0;
        }
        long span = times.peekLast() - times.peekFirst();
        return span <= 0 ? 0.0 : (times.size() - 1) * 1000.0 / span;
    }
}
