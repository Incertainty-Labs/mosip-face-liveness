package io.mosip.registration.liveness.workbench;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.FrameListener;
import io.mosip.registration.liveness.api.FrameSource;
import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessMode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.Rect;
import io.mosip.registration.liveness.api.SourceCapabilities;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import io.mosip.registration.liveness.desktop.overlay.OverlayView;
import io.mosip.registration.liveness.desktop.overlay.UiState;
import io.mosip.registration.liveness.engine.LivenessEngines;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The Workbench's session flow, with the scripted stub engine, a scripted source and a test device. */
class WorkbenchRunTest {

    private static final long PASSED_AT = Instant.parse("2026-10-25T10:15:00Z").toEpochMilli();
    private static TestDevice device;
    private static byte[] iso;

    @BeforeAll
    static void setUp() throws Exception {
        device = TestDevice.create();
        try (InputStream in = WorkbenchRunTest.class.getResourceAsStream("/faces/mosip-mockmds-face.iso")) {
            iso = in.readAllBytes();
        }
    }

    @Test
    @DisplayName("A pass is followed by the device capture; a fresh capture ends in success, with the result card"
            + " and the audit telling what happened, and the stream is stopped")
    void passThenCapture() {
        Ui ui = new Ui();
        Source source = new Source();
        WorkbenchRun run = run("PASS_PASSIVE", ui, nonce -> fresh(nonce));

        run.start(Workflow.RESIDENT_REGISTRATION, policy(), false, source);

        assertAll(() -> assertEquals(UiState.SUCCESS, ui.last().state()),
                () -> assertTrue(ui.card.get().captureBound()),
                () -> assertEquals("PASSED", ui.card.get().finalState()),
                () -> assertTrue(ui.audited("PASSED")), () -> assertTrue(ui.audited("Capture bound")),
                () -> assertTrue(source.stopped));
    }

    @Test
    @DisplayName("A PAD failure shows S11 with the attempt used; a stale capture after a pass is LIV-014 and uses an"
            + " attempt too")
    void failuresUseAttempts() {
        Ui pad = new Ui();
        run("PAD", pad, nonce -> fresh(nonce)).start(Workflow.RESIDENT_REGISTRATION, policy(), false, new Source());
        Ui stale = new Ui();
        WorkbenchRun staleRun = run("PASS_PASSIVE", stale, nonce -> capture("someone-else", "2026-10-25T10:15:05Z"));
        staleRun.start(Workflow.RESIDENT_REGISTRATION, policy(), false, new Source());
        OverlayView firstStale = stale.last();
        staleRun.start(Workflow.RESIDENT_REGISTRATION, policy(), false, new Source());

        assertAll(() -> assertEquals(UiState.PAD_FAILURE, pad.last().state()),
                () -> assertEquals("LIV-008", pad.last().errorCode()), () -> assertEquals(1, pad.last().attempt()),
                () -> assertEquals(UiState.PAD_FAILURE, firstStale.state()),
                () -> assertEquals("LIV-014", firstStale.errorCode()),
                () -> assertEquals(1, firstStale.attempt()),
                () -> assertEquals(2, stale.last().attempt(), "the refused capture used its attempt"),
                () -> assertFalse(stale.card.get().captureBound()));
    }

    @Test
    @DisplayName("After max.retries failures the last one shows S12, and a new start is refused with LIV-011 without"
            + " opening the source; Reset starts the count again")
    void maxRetries() {
        Ui ui = new Ui();
        WorkbenchRun run = run("FAIL_CHALLENGE", ui, nonce -> fresh(nonce));
        for (int i = 0; i < 3; i++) {
            run.start(Workflow.RESIDENT_REGISTRATION, policy(), false, new Source());
        }
        OverlayView third = ui.last();
        Source refused = new Source();
        run.start(Workflow.RESIDENT_REGISTRATION, policy(), false, refused);
        OverlayView limit = ui.last();
        run.resetAttempts();
        run.start(Workflow.RESIDENT_REGISTRATION, policy(), false, new Source());

        assertAll(() -> assertEquals(UiState.MAX_RETRIES, third.state()), () -> assertEquals("LIV-009",
                        third.errorCode()),
                () -> assertEquals(UiState.MAX_RETRIES, limit.state()), () -> assertEquals("LIV-011", limit.errorCode()),
                () -> assertFalse(refused.started, "no new session"),
                () -> assertEquals(1, ui.last().attempt(), "counted again from 1 after Reset"));
    }

    @Test
    @DisplayName("A device that fails at the capture is a device error (LIV-002), which uses no attempt")
    void deviceFailureUsesNoAttempt() {
        Ui ui = new Ui();
        WorkbenchRun run = run("PASS_PASSIVE", ui, nonce -> {
            throw new IOException("MockMDS is gone");
        });
        run.start(Workflow.RESIDENT_REGISTRATION, policy(), false, new Source());
        OverlayView device = ui.last();

        assertAll(() -> assertEquals(UiState.DEVICE_ERROR, device.state()),
                () -> assertEquals("LIV-002", device.errorCode()),
                () -> assertTrue(ui.audited("MockMDS is gone")));
    }

    @Test
    @DisplayName("Close or Next after a result returns the overlay to idle")
    void clear() {
        Ui ui = new Ui();
        WorkbenchRun run = run("PASS_PASSIVE", ui, nonce -> fresh(nonce));
        run.start(Workflow.RESIDENT_REGISTRATION, policy(), false, new Source());

        run.clear();

        assertEquals(UiState.IDLE, ui.last().state());
    }

    @Test
    @DisplayName("A recorded clip has no device to capture from: a pass ends in success with the capture skipped, and"
            + " the audit says so")
    void clipHasNoCapture() {
        Ui ui = new Ui();

        run("PASS_PASSIVE", ui, nonce -> null).start(Workflow.RESIDENT_REGISTRATION, policy(), false, new Source());

        assertAll(() -> assertEquals(UiState.SUCCESS, ui.last().state()),
                () -> assertTrue(ui.audited("no device capture")),
                () -> assertEquals(null, ui.card.get().capture()));
    }

    @Test
    @DisplayName("The audit records the workflow, mode, whether the mode was overridden, and the engine's states")
    void audit() {
        Ui ui = new Ui();
        LivenessPolicy active = WorkbenchConfigTestSupport.policy(LivenessMode.ACTIVE);

        run("PASS_ACTIVE", ui, nonce -> fresh(nonce)).start(Workflow.RESIDENT_REGISTRATION, active, true,
                new Source());

        assertAll(() -> assertTrue(ui.audited("RESIDENT_REGISTRATION")), () -> assertTrue(ui.audited("ACTIVE")),
                () -> assertTrue(ui.audited("mode overridden")), () -> assertTrue(ui.audited("ACTIVE_CHALLENGE")),
                () -> assertEquals(UiState.SUCCESS, ui.last().state()));
    }

    private static WorkbenchRun run(String scenario, Ui ui, WorkbenchRun.Capturer capturer) {
        Map<String, String> config = new HashMap<String, String>();
        config.put(LivenessConfigLoader.PREFIX + "stub.force", scenario);
        LivenessEngine engine = LivenessEngines.stub(config);
        FaceObservation face = FaceObservation.builder().faces(1, new Rect(60, 80, 120, 140)).build();
        return new WorkbenchRun(engine, new CaptureCheck(frame -> face), capturer, ui, () -> PASSED_AT,
                Runnable::run);
    }

    private static LivenessPolicy policy() {
        Map<String, String> config = new HashMap<String, String>();
        config.put(LivenessConfigLoader.PREFIX + "enabled", "true");
        // The stub keeps no analysed frames, so the scene check is covered in CaptureCheckTest
        config.put(LivenessConfigLoader.PREFIX + "bind.capture.enabled", "false");
        return LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);
    }

    private static SignedCapture fresh(String nonce) throws IOException {
        return capture(nonce, "2026-10-25T10:15:05Z");
    }

    private static SignedCapture capture(String nonce, String timestamp) throws IOException {
        try {
            return SignedCapture.parse(device.captureReply(iso, nonce, timestamp));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /** Feeds 400 blank frames 50 ms apart on the caller's thread, until stopped. */
    static final class Source implements FrameSource {
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
        public void start(FrameListener listener) {
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

    /** Records what the Workbench shows. */
    static final class Ui implements WorkbenchRun.Ui {
        final List<OverlayView> views = Collections.synchronizedList(new ArrayList<OverlayView>());
        final List<String> audit = Collections.synchronizedList(new ArrayList<String>());
        final AtomicReference<ResultCard> card = new AtomicReference<ResultCard>();

        @Override
        public void overlay(OverlayView view) {
            views.add(view);
        }

        @Override
        public void frame(FaceFrame frame) {
        }

        @Override
        public void audit(String line) {
            audit.add(line);
        }

        @Override
        public void result(ResultCard result) {
            card.set(result);
        }

        OverlayView last() {
            return views.get(views.size() - 1);
        }

        boolean audited(String text) {
            synchronized (audit) {
                for (String line : audit) {
                    if (line.contains(text)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
