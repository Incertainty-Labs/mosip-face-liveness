package io.mosip.registration.liveness.workbench;

import io.mosip.registration.liveness.api.FaceAnalyzer;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FrameSource;
import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessMode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.SourceCapabilities;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.desktop.MjpegFrameSource;
import io.mosip.registration.liveness.desktop.overlay.Action;
import io.mosip.registration.liveness.desktop.overlay.LivenessOverlay;
import io.mosip.registration.liveness.desktop.overlay.Messages;
import io.mosip.registration.liveness.eval.VideoFileFrameSource;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * The Liveness Workbench window: pick a source (MockMDS's stream, with its profile, or a recorded clip), a workflow and
 * an optional mode override, then Start. The overlay shows the check as the Desktop client would; the result card and
 * the audit pane show what happened, including the signed capture's checks. Use it on the JavaFX thread.
 */
public final class WorkbenchWindow extends BorderPane {

    /** Where the frames come from. */
    public static final class SourceChoice {
        final boolean mockMds;
        final String host;
        final int port;
        final String profile;
        final Path clip;

        SourceChoice(boolean mockMds, String host, int port, String profile, Path clip) {
            this.mockMds = mockMds;
            this.host = host;
            this.port = port;
            this.profile = profile;
            this.clip = clip;
        }
    }

    /** An opened source: its frames, how to capture from its device, and what to call it in the audit. */
    public static final class Opened {
        final FrameSource frames;
        final WorkbenchRun.Capturer capturer;
        final String description;

        public Opened(FrameSource frames, WorkbenchRun.Capturer capturer, String description) {
            this.frames = frames;
            this.capturer = capturer;
            this.description = description;
        }
    }

    /** Opens a source; off the JavaFX thread, since MockMDS is asked over the network. */
    public interface Sources {
        Opened open(SourceChoice choice) throws IOException;
    }

    private static final String MOCK_MDS = "MockMDS stream";
    private static final String CLIP = "Recorded clip";
    private static final String POLICY_MODE = "Policy default";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Sources sources;
    private final Path recordings;
    private final Executor background;
    private final LivenessOverlay overlay = new LivenessOverlay(Messages.english());
    private final ChoiceBox<String> source = new ChoiceBox<String>(FXCollections.observableArrayList(MOCK_MDS, CLIP));
    private final TextField device = new TextField("127.0.0.1:4501");
    private final ComboBox<String> profile = new ComboBox<String>(FXCollections.observableArrayList("webcam",
            "static", "bonafide", "print", "screen", "replay"));
    private final Label clipLabel = new Label("no clip");
    private final ChoiceBox<Workflow> workflow = new ChoiceBox<Workflow>(
            FXCollections.observableArrayList(Workflow.values()));
    private final ChoiceBox<String> mode = new ChoiceBox<String>(FXCollections.observableArrayList(POLICY_MODE,
            LivenessMode.PASSIVE.name(), LivenessMode.HYBRID.name(), LivenessMode.ACTIVE.name()));
    private final Label configLabel = new Label();
    private final CheckBox record = new CheckBox("Record");
    private final TextArea audit = new TextArea();
    private final GridPane card = new GridPane();
    private final WorkbenchRun run;
    private final AtomicReference<FaceFrame> latestFrame = new AtomicReference<FaceFrame>();
    private final AtomicBoolean framePending = new AtomicBoolean();
    private final AtomicReference<MjpegRecorder> recorder = new AtomicReference<MjpegRecorder>();
    private volatile WorkbenchRun.Capturer capturer = nonce -> null;
    private WorkbenchConfig config;
    private Path configFile;
    private Path clip;

    /**
     * @param engine     the liveness engine
     * @param analyzer   the face analyzer for the scene binder
     * @param sources    opens the chosen source
     * @param config     the liveness config to start with
     * @param recordings where Record writes clips; keep it outside git
     * @param background runs the source opening and the capture, off the JavaFX thread
     */
    public WorkbenchWindow(LivenessEngine engine, FaceAnalyzer analyzer, Sources sources, WorkbenchConfig config,
            Path recordings, Executor background) {
        this.sources = sources;
        this.recordings = recordings;
        this.background = background;
        this.config = config;
        this.run = new WorkbenchRun(engine, new CaptureCheck(analyzer), nonce -> capturer.capture(nonce), new Ui(),
                System::currentTimeMillis, background);
        setPadding(new Insets(8));
        setTop(controls());
        setCenter(overlay);
        setRight(cardPane());
        setBottom(auditPane());
        overlay.onAction(this::act);
        configLabel.setText(config.source());
        log("Ready. Engine " + engine.info().engineVersion() + " on " + engine.info().executionProvider()
                + "; config " + config.source());
    }

    private FlowPane controls() {
        source.setId("workbench-source");
        source.setValue(MOCK_MDS);
        device.setPrefColumnCount(12);
        profile.setEditable(true);
        profile.setValue("webcam");
        Button chooseClip = new Button("Clip...");
        chooseClip.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MJPEG clip", "*.mjpeg", "*.mjpg"));
            java.io.File f = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (f != null) {
                clip = f.toPath();
                clipLabel.setText(f.getName());
                source.setValue(CLIP);
            }
        });
        workflow.setId("workbench-workflow");
        workflow.setValue(Workflow.RESIDENT_REGISTRATION);
        mode.setId("workbench-mode");
        mode.setValue(POLICY_MODE);
        Button loadConfig = new Button("Config...");
        loadConfig.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Properties", "*.properties"));
            java.io.File f = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (f != null) {
                configFile = f.toPath();
                reloadConfig();
            }
        });
        Button reload = new Button("Reload");
        reload.setOnAction(e -> reloadConfig());
        Button start = new Button("Start");
        start.setId("workbench-start");
        start.setDefaultButton(true);
        start.setOnAction(e -> start());
        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> run.cancel());
        Button reset = new Button("Reset attempts");
        reset.setOnAction(e -> run.resetAttempts());
        FlowPane bar = new FlowPane(8, 6, new Label("Source"), source, device, new Label("Profile"), profile,
                chooseClip, clipLabel, new Separator(), new Label("Workflow"), workflow, new Label("Mode"), mode,
                new Separator(), loadConfig, reload, configLabel, new Separator(), record, start, cancel, reset);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 8, 0));
        return bar;
    }

    private ScrollPane cardPane() {
        card.setHgap(10);
        card.setVgap(4);
        card.setPadding(new Insets(8));
        Label title = new Label("Result");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        VBox box = new VBox(6, title, card);
        box.setPadding(new Insets(0, 0, 0, 8));
        ScrollPane pane = new ScrollPane(box);
        pane.setPrefWidth(380);
        pane.setFitToWidth(true);
        return pane;
    }

    private TextArea auditPane() {
        audit.setId("workbench-audit");
        audit.setEditable(false);
        audit.setPrefRowCount(9);
        audit.setStyle("-fx-font-family: monospace;");
        BorderPane.setMargin(audit, new Insets(8, 0, 0, 0));
        return audit;
    }

    private void reloadConfig() {
        try {
            config = configFile == null ? WorkbenchConfig.demo() : WorkbenchConfig.load(configFile);
            configLabel.setText(config.source());
            log("Config loaded: " + config.source());
        } catch (IOException e) {
            log("Config not loaded: " + e.getMessage());
        }
    }

    private void start() {
        Workflow chosen = workflow.getValue();
        LivenessMode override = POLICY_MODE.equals(mode.getValue()) ? null : LivenessMode.valueOf(mode.getValue());
        LivenessPolicy policy = config.policy(chosen, override);
        SourceChoice choice = choice();
        boolean recording = record.isSelected();
        card.getChildren().clear();
        background.execute(() -> {
            Opened opened;
            try {
                opened = sources.open(choice);
            } catch (IOException e) {
                log("Source not opened: " + e.getMessage());
                return;
            }
            capturer = opened.capturer;
            if (recording) {
                startRecording(chosen, choice);
            }
            log("Source: " + opened.description);
            run.start(chosen, policy, override != null, opened.frames);
        });
    }

    private SourceChoice choice() {
        String[] hostPort = device.getText().trim().split(":");
        int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1].trim()) : 4501;
        return new SourceChoice(MOCK_MDS.equals(source.getValue()), hostPort[0].trim(), port,
                profile.getValue() == null ? "" : profile.getValue().trim(), clip);
    }

    private void startRecording(Workflow chosen, SourceChoice choice) {
        String label = choice.mockMds ? choice.profile : String.valueOf(choice.clip.getFileName());
        Path file = recordings.resolve(chosen.name().toLowerCase(java.util.Locale.ROOT) + "-" + label.replaceAll(
                "[^A-Za-z0-9_.-]", "_") + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern(
                        "yyyyMMdd-HHmmss")) + ".mjpeg");
        try {
            recorder.set(new MjpegRecorder(file));
            log("Recording to " + file);
        } catch (IOException e) {
            log("Not recording: " + e.getMessage());
        }
    }

    private void stopRecording() {
        MjpegRecorder r = recorder.getAndSet(null);
        if (r != null) {
            try {
                r.close();
                log("Recording saved");
            } catch (IOException e) {
                log("Recording not closed: " + e.getMessage());
            }
        }
    }

    private void act(Action action) {
        switch (action) {
            case RETRY:
            case RESCAN:
                start();
                break;
            case CANCEL:
                run.cancel();
                break;
            case REQUEST_OVERRIDE:
                log("Supervisor override (simulated in the Workbench): one more attempt");
                run.resetAttempts();
                run.clear();
                break;
            case MARK_EXCEPTION:
                log("Face marked as a biometric exception (simulated in the Workbench)");
                run.clear();
                break;
            default:
                run.clear();
                break;
        }
    }

    private void showCard(ResultCard result) {
        card.getChildren().clear();
        int row = 0;
        for (Map.Entry<String, String> line : result.lines().entrySet()) {
            Label key = new Label(line.getKey());
            key.setStyle("-fx-text-fill: #5f6368;");
            key.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            Label value = new Label(line.getValue());
            value.setWrapText(true);
            value.setMaxWidth(240);
            if ("Capture".equals(line.getKey())) {
                value.setId("workbench-card-capture");
            }
            card.add(key, 0, row);
            card.add(value, 1, row++);
        }
    }

    private void log(String line) {
        String stamped = LocalTime.now().format(TIME) + "  " + line + "\n";
        // Also to standard output, so a run can be logged to a file
        System.out.print(stamped);
        Platform.runLater(() -> audit.appendText(stamped));
    }

    /** The run's output, from any thread, onto the JavaFX thread. */
    private final class Ui implements WorkbenchRun.Ui {
        @Override
        public void overlay(io.mosip.registration.liveness.desktop.overlay.OverlayView view) {
            Platform.runLater(() -> overlay.show(view));
        }

        @Override
        public void frame(FaceFrame frame) {
            MjpegRecorder r = recorder.get();
            if (r != null) {
                try {
                    r.write(frame);
                } catch (IOException e) {
                    log("Recording failed: " + e.getMessage());
                    recorder.set(null);
                }
            }
            // Show only the latest frame, dropping the rest while the JavaFX thread is busy
            latestFrame.set(frame);
            if (framePending.compareAndSet(false, true)) {
                Platform.runLater(() -> {
                    framePending.set(false);
                    FaceFrame f = latestFrame.get();
                    if (f != null) {
                        overlay.showFrame(f);
                    }
                });
            }
        }

        @Override
        public void audit(String line) {
            log(line);
        }

        @Override
        public void result(ResultCard result) {
            stopRecording();
            Platform.runLater(() -> showCard(result));
        }
    }

    /** @return the sources the Workbench app uses: MockMDS over SBI 0.9.5, and recorded clips */
    public static Sources liveSources() {
        return choice -> {
            if (!choice.mockMds) {
                if (choice.clip == null) {
                    throw new IOException("Choose a clip first");
                }
                return new Opened(new VideoFileFrameSource(choice.clip, 20, true), nonce -> null,
                        "clip " + choice.clip.getFileName());
            }
            SbiClient sbi = new SbiClient(choice.host, choice.port);
            List<SbiClient.Device> faces = sbi.discover("Face");
            if (faces.isEmpty()) {
                throw new IOException("No face device at " + choice.host + ":" + choice.port);
            }
            SbiClient.Device face = faces.get(0);
            if (!choice.profile.isEmpty()) {
                sbi.setProfile(choice.profile);
            }
            SourceCapabilities caps = SourceCapabilities.builder().device("MOSIP", "MockMDS face")
                    .certification(face.certification()).fps(20, 0)
                    .formats(new java.util.HashSet<String>(Arrays.asList("image/jpeg"))).build();
            MjpegFrameSource frames = new MjpegFrameSource(choice.host, choice.port, face.deviceId(), 0, caps);
            String deviceId = face.deviceId();
            return new Opened(frames, nonce -> {
                // MockMDS ends its stream when it captures: that end must not be taken for a drop and reconnected
                frames.endExpected();
                return SignedCapture.parse(sbi.capture(deviceId, nonce, 10000));
            },
                    "MockMDS " + choice.host + ":" + choice.port + ", face device " + deviceId + " ("
                            + face.status() + ", " + face.certification() + "), profile "
                            + (choice.profile.isEmpty() ? "unchanged" : choice.profile));
        };
    }
}
