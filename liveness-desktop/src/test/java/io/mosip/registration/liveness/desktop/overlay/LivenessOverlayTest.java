package io.mosip.registration.liveness.desktop.overlay;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.MaxRetriesAction;
import io.mosip.registration.liveness.api.PromptLevel;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.UserPrompt;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.geometry.Bounds;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The JavaFX overlay draws what the model decides. Needs a display, so it is skipped on a headless machine. */
class LivenessOverlayTest {

    @BeforeAll
    static void toolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        } catch (Throwable noDisplay) {
            // Headless build machines: no display, or no native JavaFX for this platform
            Assumptions.abort("no JavaFX display: " + noDisplay);
        }
        Assumptions.assumeTrue(started.await(10, TimeUnit.SECONDS), "JavaFX did not start");
    }

    @Test
    @DisplayName("A challenge shows its step, prompt, time bar and toast, with only Cancel offered")
    void challenge() throws Exception {
        List<OverlayView> views = new ArrayList<OverlayView>();
        OverlayModel model = new OverlayModel(views::add);
        model.state(SessionState.ACTIVE_CHALLENGE, new UserPrompt("liveness.prompt.turn_left", PromptLevel.INFO));
        model.challenge(new Challenge(ChallengeType.TURN_LEFT, 5000), 1, 3);
        model.challenge(new Challenge(ChallengeType.TURN_RIGHT, 5000), 2, 3);
        model.progress(0.25f);

        LivenessOverlay overlay = onFx(() -> {
            LivenessOverlay o = new LivenessOverlay(Messages.english());
            o.show(views.get(views.size() - 1));
            return o;
        });

        assertAll(() -> assertEquals("Step 2 of 3", text(overlay, "#liveness-status")),
                () -> assertEquals("Please turn your head to the right", text(overlay, "#liveness-prompt")),
                () -> assertEquals(0.75, ((ProgressBar) overlay.lookup("#liveness-time")).getProgress(), 1e-6),
                () -> assertTrue(overlay.lookup("#liveness-time").isVisible()),
                () -> assertEquals("Action detected", text(overlay, "#liveness-toast")),
                () -> assertTrue(overlay.lookup("#liveness-toast").isVisible()),
                () -> assertTrue(button(overlay, Action.CANCEL).isVisible()),
                () -> assertFalse(button(overlay, Action.RETRY).isVisible()),
                () -> assertFalse(overlay.lookup("#liveness-attempt").isVisible()));
    }

    @Test
    @DisplayName("A liveness failure shows its message, the guidance list, the attempt counter, Retry and Cancel, in"
            + " the failure style")
    void failure() throws Exception {
        List<OverlayView> views = new ArrayList<OverlayView>();
        OverlayModel model = new OverlayModel(views::add);
        model.failed(LivenessErrorCode.CHALLENGE_TIMEOUT, 1, 3, true, MaxRetriesAction.BLOCK);

        LivenessOverlay overlay = onFx(() -> {
            LivenessOverlay o = new LivenessOverlay(Messages.english());
            o.show(views.get(0));
            return o;
        });

        assertAll(() -> assertEquals("Time ran out. Please follow the prompted action and try again.",
                        text(overlay, "#liveness-status")),
                () -> assertEquals("Attempt 1 of 3. Select Retry to try again.", text(overlay, "#liveness-attempt")),
                () -> assertEquals(OverlayModel.GUIDANCE_LIST.size(),
                        overlay.lookupAll(".liveness-guidance-item").size()),
                () -> assertTrue(button(overlay, Action.RETRY).isVisible()),
                () -> assertTrue(button(overlay, Action.CANCEL).isVisible()),
                () -> assertEquals("Retry", button(overlay, Action.RETRY).getText()),
                () -> assertFalse(overlay.lookup("#liveness-time").isVisible(), "no time bar after the attempt"),
                () -> assertTrue(overlay.getStyleClass().contains("liveness-level-failure")));
    }

    @Test
    @DisplayName("A frame appears in the preview, mirrored as a selfie view, at its own size")
    void preview() throws Exception {
        byte[] rgb = new byte[64 * 48 * 3];
        LivenessOverlay overlay = onFx(() -> {
            LivenessOverlay o = new LivenessOverlay(Messages.english());
            o.showFrame(new FaceFrame(64, 48, rgb, 0, 0, "test"));
            return o;
        });

        ImageView preview = (ImageView) overlay.lookup("#liveness-preview");
        assertAll(() -> assertEquals(64, preview.getImage().getWidth(), 1e-9),
                () -> assertEquals(48, preview.getImage().getHeight(), 1e-9),
                () -> assertEquals(-1, preview.getScaleX(), 1e-9));
    }

    @Test
    @DisplayName("Outside the face oval the preview is dimmed by a 40% scrim; inside it is clear; long messages wrap"
            + " instead of being cut")
    void scrimAndWrapping() throws Exception {
        byte[] rgb = new byte[240 * 320 * 3];
        LivenessOverlay overlay = onFx(() -> {
            LivenessOverlay o = new LivenessOverlay(Messages.english());
            o.showFrame(new FaceFrame(240, 320, rgb, 0, 0, "test"));
            return o;
        });

        Shape scrim = (Shape) overlay.lookup("#liveness-scrim");
        Bounds area = scrim.getBoundsInLocal();
        assertAll(() -> assertEquals(480, area.getWidth(), 1, "the whole preview, at the frame's shape"),
                () -> assertEquals(640, area.getHeight(), 1),
                () -> assertTrue(scrim.contains(5, 5), "dimmed outside the oval"),
                () -> assertFalse(scrim.contains(240, 320), "clear inside"),
                () -> assertEquals(0.4, ((Color) scrim.getFill()).getOpacity(), 1e-6),
                () -> assertTrue(((Label) overlay.lookup("#liveness-status")).isWrapText()));
    }

    @Test
    @DisplayName("A button press reaches the handler as its action")
    void actions() throws Exception {
        List<Action> pressed = new ArrayList<Action>();
        onFx(() -> {
            LivenessOverlay o = new LivenessOverlay(Messages.english());
            o.onAction(pressed::add);
            button(o, Action.RETRY).fire();
            return o;
        });

        assertEquals(1, pressed.size());
        assertEquals(Action.RETRY, pressed.get(0));
    }

    private static Button button(Node overlay, Action action) {
        return (Button) overlay.lookup("#liveness-button-" + action.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String text(Node overlay, String selector) {
        return ((Label) overlay.lookup(selector)).getText();
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
        assertTrue(done.await(10, TimeUnit.SECONDS), "the FX thread answered");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return out.get();
    }
}
