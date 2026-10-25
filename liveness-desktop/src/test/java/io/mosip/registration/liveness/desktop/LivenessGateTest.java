package io.mosip.registration.liveness.desktop;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FrameListener;
import io.mosip.registration.liveness.api.FrameSource;
import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessException;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.SourceCapabilities;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import io.mosip.registration.liveness.desktop.overlay.OverlayView;
import io.mosip.registration.liveness.desktop.overlay.UiState;
import io.mosip.registration.liveness.engine.LivenessEngines;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The liveness check the Registration Client runs before its face capture, with the scripted stub engine. */
class LivenessGateTest {

    @Test
    @DisplayName("A pass lets the client capture: it is told the check passed, the overlay shows capturing, and the"
            + " gate stops listening to the stream")
    void passLetsTheClientCapture() {
        Client client = new Client();
        Source frames = new Source();
        LivenessGate gate = gate("PASS_PASSIVE", client);

        gate.start(frames);

        assertAll(() -> assertEquals(SessionState.PASSED, client.passed.finalState()),
                () -> assertNull(client.ended), () -> assertEquals(UiState.CAPTURING, client.last().state()),
                () -> assertTrue(frames.stopped));
    }

    @Test
    @DisplayName("A PAD failure is shown with the attempt it used and allows Scan again; after max.retries failures"
            + " the last one shows S12, no attempt is left, and a start is refused with LIV-011 without starting the"
            + " stream")
    void failuresUseAttempts() {
        Client client = new Client();
        LivenessGate gate = gate("PAD", client);

        gate.start(new Source());
        OverlayView first = client.last();
        boolean firstRetry = client.retryAllowed;
        gate.start(new Source());
        gate.start(new Source());
        OverlayView third = client.last();
        boolean thirdRetry = client.retryAllowed;
        Source refused = new Source();
        gate.start(refused);

        assertAll(() -> assertEquals(UiState.PAD_FAILURE, first.state()), () -> assertEquals(1, first.attempt()),
                () -> assertEquals("LIV-008", first.errorCode()), () -> assertTrue(firstRetry),
                () -> assertEquals(UiState.MAX_RETRIES, third.state()), () -> assertFalse(thirdRetry),
                () -> assertFalse(gate.attemptLeft()), () -> assertFalse(refused.started, "no stream"),
                () -> assertEquals("LIV-011", client.last().errorCode()));
    }

    @Test
    @DisplayName("A stream that will not start (LIV-001) or drops during the check (LIV-002) is a device error: shown"
            + " with Retry, and it uses no attempt")
    void deviceErrorsUseNoAttempt() {
        Client client = new Client();
        LivenessGate gate = gate("PAD", client);

        gate.start(new Source() {
            @Override
            public void start(FrameListener listener) throws LivenessException {
                throw new LivenessException(LivenessErrorCode.DEVICE_UNAVAILABLE, "no stream");
            }
        });
        OverlayView notStarted = client.last();
        boolean notStartedRetry = client.retryAllowed;
        gate.start(new Source() {
            @Override
            public void start(FrameListener listener) {
                listener.onError(LivenessErrorCode.DEVICE_CONNECTION_FAILED, "unplugged");
            }
        });
        OverlayView dropped = client.last();
        gate.start(new Source());

        assertAll(() -> assertEquals(UiState.DEVICE_ERROR, notStarted.state()),
                () -> assertEquals("LIV-001", notStarted.errorCode()), () -> assertTrue(notStartedRetry),
                () -> assertEquals(UiState.DEVICE_ERROR, dropped.state()),
                () -> assertEquals("LIV-002", dropped.errorCode()),
                () -> assertEquals(1, client.last().attempt(), "the PAD failure after them is attempt 1"));
    }

    @Test
    @DisplayName("Cancel, when the operator closes the liveness window, ends the check without using an attempt and"
            + " stops the stream")
    void cancelUsesNoAttempt() {
        Client client = new Client();
        LivenessGate gate = gate("PAD", client);
        Source silent = new Source() {
            @Override
            public void start(FrameListener listener) {
                started = true;
            }
        };

        gate.start(silent);
        gate.cancel();
        gate.start(new Source());

        assertAll(() -> assertTrue(silent.stopped), () -> assertEquals(UiState.PAD_FAILURE, client.last().state()),
                () -> assertEquals(1, client.last().attempt(), "the PAD failure after the cancel is attempt 1"));
    }

    @Test
    @DisplayName("An engine fault is shown as an engine error with Retry and uses no attempt")
    void engineErrorUsesNoAttempt() {
        Client client = new Client();
        LivenessGate gate = gate("ENGINE_ERROR", client);

        for (int i = 0; i < 3; i++) {
            gate.start(new Source());
        }

        assertAll(() -> assertEquals(UiState.ENGINE_ERROR, client.last().state()),
                () -> assertTrue(client.retryAllowed),
                () -> assertTrue(gate.attemptLeft(), "three faults, no attempt used"));
    }

    private static LivenessGate gate(String scenario, Client client) {
        Map<String, String> config = new HashMap<String, String>();
        config.put(LivenessConfigLoader.PREFIX + "stub.force", scenario);
        LivenessEngine engine = LivenessEngines.stub(config);
        return new LivenessGate(engine, policy(), Workflow.RESIDENT_REGISTRATION, 0, client);
    }

    private static LivenessPolicy policy() {
        Map<String, String> config = new HashMap<String, String>();
        config.put(LivenessConfigLoader.PREFIX + "enabled", "true");
        return LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);
    }

    /** Feeds 400 blank frames 50 ms apart on the caller's thread, until stopped. */
    static class Source implements FrameSource {
        volatile boolean started;
        volatile boolean stopped;

        @Override
        public String id() {
            return "test";
        }

        @Override
        public SourceCapabilities capabilities() {
            return SourceCapabilities.builder().build();
        }

        @Override
        public void start(FrameListener listener) throws LivenessException {
            started = true;
            for (int n = 0; n < 400 && !stopped; n++) {
                listener.onFrame(new FaceFrame(64, 48, new byte[64 * 48 * 3], n * 50L, n, "test"));
            }
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    /** What the client is told. */
    static final class Client implements LivenessGate.Listener {
        final List<OverlayView> views = Collections.synchronizedList(new ArrayList<OverlayView>());
        volatile LivenessResult passed;
        volatile LivenessResult ended;
        volatile boolean retryAllowed;

        @Override
        public void view(OverlayView view) {
            views.add(view);
        }

        @Override
        public void frame(FaceFrame frame) {
        }

        @Override
        public void passed(LivenessResult result) {
            passed = result;
        }

        @Override
        public void ended(LivenessResult result, boolean retryAllowed) {
            ended = result;
            this.retryAllowed = retryAllowed;
        }

        OverlayView last() {
            return views.get(views.size() - 1);
        }
    }
}
