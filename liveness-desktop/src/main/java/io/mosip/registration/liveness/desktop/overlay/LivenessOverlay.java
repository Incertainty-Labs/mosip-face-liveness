package io.mosip.registration.liveness.desktop.overlay;

import io.mosip.registration.liveness.api.FaceFrame;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * The liveness overlay (UI_UX_SPEC §3 and §5, D-1): the live preview with the face oval, its scrim and the progress
 * ring on the left, and on a solid panel on the right the step dots, the status line, the prompt with its direction
 * cue, the time bar, the feedback toast, the guidance list, the attempt counter and the buttons. It only draws an
 * {@link OverlayView}; {@link OverlayModel} decides what to show. Use it on the JavaFX thread.
 */
public final class LivenessOverlay extends HBox {

    private static final double PREVIEW_WIDTH = 480;
    private static final Color RING = Color.web("#1565c0");

    private final Messages messages;
    private final ImageView preview = new ImageView();
    /** The scrim, the oval and the ring, laid over the preview at its size. */
    private final Pane guide = new Pane();
    private final Ellipse oval = new Ellipse();
    private final Arc ringArc = new Arc();
    private final HBox dots = new HBox(6);
    private final Label status = label("liveness-status");
    private final SVGPath cue = new SVGPath();
    private final Label prompt = label("liveness-prompt");
    private final ProgressBar time = new ProgressBar();
    private final Label toast = label("liveness-toast");
    private final VBox guidance = new VBox(4);
    private final Label attempt = label("liveness-attempt");
    private final Map<Action, Button> buttons = new EnumMap<Action, Button>(Action.class);
    private final PauseTransition toastTimer = new PauseTransition(Duration.millis(1500));
    private WritableImage image;
    private float ring = Float.NaN;
    private int shownToast;
    private Consumer<Action> handler = action -> {
    };

    /** @param messages the texts for the message keys */
    public LivenessOverlay(Messages messages) {
        this.messages = messages;
        getStyleClass().add("liveness-overlay");
        getStylesheets().add(LivenessOverlay.class.getResource("liveness-overlay.css").toExternalForm());

        preview.setId("liveness-preview");
        guide.setId("liveness-guide");
        guide.setMouseTransparent(true);
        oval.setFill(Color.TRANSPARENT);
        oval.setStroke(Color.WHITE);
        oval.setStrokeWidth(3);
        ringArc.setFill(Color.TRANSPARENT);
        ringArc.setStroke(RING);
        ringArc.setStrokeWidth(6);
        ringArc.setType(ArcType.OPEN);
        ringArc.setStartAngle(90);
        layoutGuide(PREVIEW_WIDTH, PREVIEW_WIDTH * 3 / 4);
        preview.setFitWidth(PREVIEW_WIDTH);
        preview.setPreserveRatio(true);
        // A selfie view, as people expect; the frames themselves stay unmirrored for the engine
        preview.setScaleX(-1);
        StackPane previewPane = new StackPane(preview, guide);
        previewPane.getStyleClass().add("liveness-preview-pane");

        dots.setId("liveness-dots");
        dots.setAlignment(Pos.CENTER_LEFT);
        prompt.setWrapText(true);
        status.setWrapText(true);
        cue.setId("liveness-cue");
        cue.getStyleClass().add("liveness-cue");
        HBox promptRow = new HBox(10, cue, prompt);
        promptRow.setAlignment(Pos.CENTER_LEFT);
        time.setId("liveness-time");
        time.getStyleClass().add("liveness-time");
        time.setMaxWidth(Double.MAX_VALUE);
        toast.setVisible(false);
        toastTimer.setOnFinished(e -> toast.setVisible(false));
        guidance.setId("liveness-guidance");
        HBox buttonRow = new HBox(8);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        for (Action action : Action.values()) {
            Button button = new Button(messages.text(action.messageKey()));
            button.setId("liveness-button-" + action.name().toLowerCase(Locale.ROOT));
            button.getStyleClass().add(action == Action.RETRY || action == Action.NEXT ? "liveness-primary"
                    : "liveness-secondary");
            button.setOnAction(e -> handler.accept(action));
            shown(button, false);
            buttons.put(action, button);
            buttonRow.getChildren().add(button);
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox panel = new VBox(12, dots, status, promptRow, time, toast, guidance, attempt, spacer, buttonRow);
        panel.getStyleClass().add("liveness-panel");
        panel.setPadding(new Insets(20));
        panel.setPrefWidth(340);
        HBox.setHgrow(panel, Priority.ALWAYS);
        getChildren().addAll(previewPane, panel);
        show(new OverlayView(new OverlayView.Builder()));
    }

    /** @param handler receives each button press */
    public void onAction(Consumer<Action> handler) {
        this.handler = handler;
    }

    /** @param view what to show now */
    public void show(OverlayView view) {
        getStyleClass().removeIf(c -> c.startsWith("liveness-level-"));
        getStyleClass().add("liveness-level-" + view.level().name().toLowerCase(Locale.ROOT).replace('_', '-'));

        status.setText(view.statusKey() == null ? "" : messages.text(view.statusKey(), view.statusArgs()));
        prompt.setText(view.promptKey() == null ? "" : messages.text(view.promptKey()));
        shown(prompt, view.promptKey() != null);
        cue.setContent(cuePath(view.cue()));
        shown(cue, view.cue() != Cue.NONE);
        showDots(view);
        shown(time, !Float.isNaN(view.timeLeft()));
        if (!Float.isNaN(view.timeLeft())) {
            time.setProgress(view.timeLeft());
        }
        showToast(view);
        guidance.getChildren().clear();
        for (String key : view.guidanceKeys()) {
            Label item = new Label("• " + messages.text(key));
            item.getStyleClass().add("liveness-guidance-item");
            item.setWrapText(true);
            guidance.getChildren().add(item);
        }
        shown(attempt, view.maxAttempts() > 0);
        attempt.setText(view.maxAttempts() > 0
                ? messages.text("liveness.retry.available", view.attempt(), view.maxAttempts()) : "");
        for (Map.Entry<Action, Button> b : buttons.entrySet()) {
            shown(b.getValue(), view.buttons().contains(b.getKey()));
        }
        ring = view.ring();
        drawRing();
    }

    /** @param frame the latest camera frame, shown mirrored in the preview */
    public void showFrame(FaceFrame frame) {
        if (image == null || image.getWidth() != frame.width() || image.getHeight() != frame.height()) {
            image = new WritableImage(frame.width(), frame.height());
            preview.setImage(image);
            layoutGuide(PREVIEW_WIDTH, PREVIEW_WIDTH * frame.height() / frame.width());
        }
        image.getPixelWriter().setPixels(0, 0, frame.width(), frame.height(), PixelFormat.getByteRgbInstance(),
                frame.rgb(), 0, frame.width() * 3);
    }

    private void showDots(OverlayView view) {
        dots.getChildren().clear();
        int current = view.stepTotal() > 0 && view.statusArgs().length > 0 ? (Integer) view.statusArgs()[0] : 0;
        for (int i = 1; i <= view.stepTotal(); i++) {
            Circle dot = new Circle(5);
            dot.getStyleClass().add(i < current ? "liveness-dot-done" : i == current ? "liveness-dot-current"
                    : "liveness-dot-next");
            dots.getChildren().add(dot);
        }
        shown(dots, view.stepTotal() > 0);
    }

    private void showToast(OverlayView view) {
        if (view.toastKey() == null) {
            toastTimer.stop();
            toast.setVisible(false);
            return;
        }
        if (view.toastSequence() != shownToast) {
            shownToast = view.toastSequence();
            toast.setText(messages.text(view.toastKey()));
            toast.setVisible(true);
            toastTimer.playFromStart();
        }
    }

    /**
     * Sizes the guide to the preview: the 40% scrim is the preview minus the oval (60% of its height, centred), with
     * the oval's white outline and the progress ring just outside it.
     */
    private void layoutGuide(double w, double h) {
        guide.setMinSize(w, h);
        guide.setPrefSize(w, h);
        guide.setMaxSize(w, h);
        double ry = h * 0.3;
        double rx = ry * 0.75;
        Shape scrim = Shape.subtract(new Rectangle(w, h), new Ellipse(w / 2, h / 2, rx, ry));
        scrim.setId("liveness-scrim");
        scrim.setFill(Color.color(0, 0, 0, 0.4));
        oval.setCenterX(w / 2);
        oval.setCenterY(h / 2);
        oval.setRadiusX(rx);
        oval.setRadiusY(ry);
        ringArc.setCenterX(w / 2);
        ringArc.setCenterY(h / 2);
        ringArc.setRadiusX(rx + 6);
        ringArc.setRadiusY(ry + 6);
        guide.getChildren().setAll(scrim, oval, ringArc);
        drawRing();
    }

    private void drawRing() {
        boolean visible = !Float.isNaN(ring) && ring > 0;
        ringArc.setVisible(visible);
        ringArc.setLength(visible ? -360 * ring : 0);
    }

    /** Simple outline icons, so no icon font or image file is needed. */
    private static String cuePath(Cue cue) {
        switch (cue) {
            case LEFT:
                return "M 20 4 L 4 16 L 20 28 L 20 20 L 36 20 L 36 12 L 20 12 Z";
            case RIGHT:
                return "M 20 4 L 36 16 L 20 28 L 20 20 L 4 20 L 4 12 L 20 12 Z";
            case UP:
                return "M 4 20 L 16 4 L 28 20 L 20 20 L 20 36 L 12 36 L 12 20 Z";
            case DOWN:
                return "M 4 20 L 16 36 L 28 20 L 20 20 L 20 4 L 12 4 L 12 20 Z";
            case SMILE:
                return "M 4 14 Q 18 32 32 14 L 30 12 Q 18 26 6 12 Z";
            case MOUTH:
                return "M 18 6 C 30 6 32 30 18 30 C 4 30 6 6 18 6 Z M 18 11 C 10 11 10 25 18 25 C 26 25 26 11 18 11 Z";
            case EYES:
                return "M 2 16 Q 10 8 18 16 Q 10 24 2 16 Z M 20 16 Q 28 8 36 16 Q 28 24 20 16 Z";
            default:
                return "";
        }
    }

    private static Label label(String id) {
        Label label = new Label();
        label.setId(id);
        label.getStyleClass().add(id);
        return label;
    }

    private static void shown(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
