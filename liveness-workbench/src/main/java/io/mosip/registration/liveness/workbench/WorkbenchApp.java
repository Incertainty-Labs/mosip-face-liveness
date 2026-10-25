package io.mosip.registration.liveness.workbench;

import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import io.mosip.registration.liveness.engine.EngineOptions;
import io.mosip.registration.liveness.engine.LivenessEngines;
import io.mosip.registration.liveness.engine.onnx.OnnxFaceAnalyzer;
import io.mosip.registration.liveness.model.ModelRegistry;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * The Liveness Workbench app. System properties: {@code workbench.models} (the model bundle folder, default
 * {@code models}), {@code workbench.config} (a properties file; default the bundled demo config) and
 * {@code workbench.recordings} (where Record writes; default {@code ~/mosip-liveness-recordings}, outside git).
 */
public final class WorkbenchApp extends Application {

    @Override
    public void start(Stage stage) {
        Label loading = new Label("Checking and loading the models...");
        StackPane root = new StackPane(loading);
        root.setAlignment(Pos.CENTER);
        stage.setTitle("MOSIP Face Liveness Workbench");
        stage.setScene(new Scene(root, 1500, 960));
        stage.show();
        ExecutorService background = Executors.newCachedThreadPool(task -> {
            Thread t = new Thread(task, "workbench");
            t.setDaemon(true);
            return t;
        });
        background.execute(() -> {
            try {
                Path models = Paths.get(System.getProperty("workbench.models", "models"));
                ModelRegistry registry = ModelRegistry.load(models);
                LivenessEngine engine = LivenessEngines.hybrid(registry, EngineOptions.defaults());
                OnnxFaceAnalyzer analyzer = OnnxFaceAnalyzer.load(registry,
                        LivenessConfigLoader.DEFAULT_DETECTOR_MIN_SCORE);
                String configFile = System.getProperty("workbench.config");
                WorkbenchConfig config = configFile == null ? WorkbenchConfig.demo()
                        : WorkbenchConfig.load(Paths.get(configFile));
                Path recordings = Paths.get(System.getProperty("workbench.recordings",
                        System.getProperty("user.home") + "/mosip-liveness-recordings"));
                Platform.runLater(() -> stage.getScene().setRoot(new WorkbenchWindow(engine, analyzer,
                        WorkbenchWindow.liveSources(), config, recordings, background)));
            } catch (Exception e) {
                Platform.runLater(() -> loading.setText("The Workbench cannot start: " + e.getMessage()));
            }
        });
    }

    /** @param args passed to JavaFX */
    public static void main(String[] args) {
        launch(args);
    }
}
