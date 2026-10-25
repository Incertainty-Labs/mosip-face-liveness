package io.mosip.registration.liveness.workbench;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.Rect;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import io.mosip.registration.liveness.engine.LivenessEngines;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Workbench window, end to end with the stub engine. Needs a display; skipped on a headless machine. */
class WorkbenchWindowTest {

    private static TestDevice device;
    private static byte[] iso;

    @TempDir
    Path recordings;

    @BeforeAll
    static void setUp() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        } catch (Throwable noDisplay) {
            Assumptions.abort("no JavaFX display: " + noDisplay);
        }
        Assumptions.assumeTrue(started.await(10, TimeUnit.SECONDS), "JavaFX did not start");
        device = TestDevice.create();
        try (InputStream in = WorkbenchWindowTest.class.getResourceAsStream("/faces/mosip-mockmds-face.iso")) {
            iso = in.readAllBytes();
        }
    }

    @Test
    @DisplayName("The window offers the seven workflows, the mode override and both sources; Start runs a check that"
            + " ends with the overlay's success, a bound capture on the result card and the audit lines")
    void startToSuccess() throws Exception {
        LivenessEngine engine = LivenessEngines.stub(Collections.singletonMap(
                LivenessConfigLoader.PREFIX + "stub.force", "PASS_PASSIVE"));
        FaceObservation face = FaceObservation.builder().faces(1, new Rect(60, 80, 120, 140)).build();
        WorkbenchWindow.Sources sources = choice -> new WorkbenchWindow.Opened(new WorkbenchRunTest.Source(),
                nonce -> fresh(nonce), "scripted test source");

        WorkbenchWindow window = onFx(() -> {
            try {
                WorkbenchWindow w = new WorkbenchWindow(engine, frame -> face, sources, WorkbenchConfigWithBinderOff(),
                        recordings, Runnable::run);
                new javafx.scene.Scene(w, 1400, 900);
                ((Button) w.lookup("#workbench-start")).fire();
                return w;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        onFx(() -> {
            window.applyCss();
            window.layout();
            return null;
        });

        String audit = onFx(() -> ((TextArea) window.lookup("#workbench-audit")).getText());
        assertAll(() -> assertEquals(7, onFx(() -> ((ChoiceBox<?>) window.lookup("#workbench-workflow")).getItems()
                        .size())),
                () -> assertEquals(4, onFx(() -> ((ChoiceBox<?>) window.lookup("#workbench-mode")).getItems().size())),
                () -> assertEquals(2, onFx(() -> ((ChoiceBox<?>) window.lookup("#workbench-source")).getItems()
                        .size())),
                () -> assertEquals("Good, face captured successfully",
                        onFx(() -> ((Label) window.lookup("#liveness-status")).getText())),
                () -> assertTrue(audit.contains("Capture bound"), audit),
                () -> assertTrue(onFx(() -> ((Label) window.lookup("#workbench-card-capture")).getText())
                        .startsWith("bound")));
    }

    private static WorkbenchConfig WorkbenchConfigWithBinderOff() throws IOException {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("workbench", ".properties");
        java.nio.file.Files.write(file, java.util.Arrays.asList(LivenessConfigLoader.PREFIX + "enabled=true",
                LivenessConfigLoader.PREFIX + "bind.capture.enabled=false"));
        return WorkbenchConfig.load(file);
    }

    private static SignedCapture fresh(String nonce) throws IOException {
        try {
            return SignedCapture.parse(device.captureReply(iso, nonce,
                    java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now()
                            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS))));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static <T> T onFx(Supplier<T> work) throws Exception {
        AtomicReference<T> out = new AtomicReference<T>();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                out.set(work.get());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "the FX thread answered");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return out.get();
    }
}
