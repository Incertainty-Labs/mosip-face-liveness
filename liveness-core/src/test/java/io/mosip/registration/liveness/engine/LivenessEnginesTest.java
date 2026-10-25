package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceAnalyzer;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessException;
import io.mosip.registration.liveness.api.Rect;
import io.mosip.registration.liveness.engine.onnx.MiniFasNetPadDetector;
import io.mosip.registration.liveness.engine.onnx.OnnxFaceAnalyzer;
import io.mosip.registration.liveness.model.ModelRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LivenessEnginesTest {

    private static final FaceFrame FRAME = new FaceFrame(64, 48, new byte[64 * 48 * 3], 0, 0, "test");

    @Test
    @DisplayName("When a model fails to load, the models already loaded are closed (LIV-013), so a failed accelerator"
            + " leaves no native sessions behind before the engine is built again on CPU")
    void failedLoadClosesWhatWasLoaded() throws Exception {
        List<AutoCloseable> loaded = new ArrayList<AutoCloseable>();

        assertThrows(LivenessException.class,
                () -> LivenessEngines.build(withBrokenModel("minifasnet_v1se"), EngineOptions.defaults(), "CPU",
                        loaded));

        FaceObservation face = FaceObservation.builder().faces(1, new Rect(16, 8, 32, 32)).build();
        assertAll(() -> assertEquals(2, loaded.size(), "the face analyzer and minifasnet_v2 had loaded"),
                () -> assertThrows(IllegalStateException.class, () -> ((FaceAnalyzer) loaded.get(0)).analyze(FRAME),
                        "the face analyzer is closed"),
                () -> assertThrows(IllegalStateException.class,
                        () -> ((MiniFasNetPadDetector) loaded.get(1)).score(FRAME, face), "minifasnet_v2 is closed"));
    }

    @Test
    @DisplayName("With detectEvery above 1 each session gets its own tracker, since a tracker holds the session's"
            + " face; with 1 it gets the plain analyzer, as in v0.2.0")
    void eachSessionGetsItsOwnTracker() throws Exception {
        try (OnnxFaceAnalyzer models = OnnxFaceAnalyzer.load(new File("../models").toPath(), 0.7)) {
            FaceAnalyzer first = LivenessEngines.perSession(models, 0.7, 3);
            FaceAnalyzer second = LivenessEngines.perSession(models, 0.7, 3);
            FaceAnalyzer plain = LivenessEngines.perSession(models, 0.7, 1);

            assertAll(() -> assertTrue(first instanceof DetectingAnalyzer, "a tracker"),
                    () -> assertNotSame(first, second, "one per session"),
                    () -> assertFalse(plain instanceof DetectingAnalyzer, "detector on every frame"));
        }
    }

    /** The bundle with one model's bytes replaced by text that is not ONNX, its manifest hash updated to match. */
    private static ModelRegistry withBrokenModel(String name) throws Exception {
        Path dir = new File("../models").toPath();
        String manifest = new String(Files.readAllBytes(dir.resolve("models.json")), StandardCharsets.UTF_8);
        Map<String, byte[]> files = new HashMap<String, byte[]>();
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path p : (Iterable<Path>) paths::iterator) {
                if (p.toString().endsWith(".onnx")) {
                    files.put(p.getFileName().toString(), Files.readAllBytes(p));
                }
            }
        }
        Matcher entry = Pattern.compile("\"name\": \"" + name + "\",\\s*\"file\": \"([^\"]+)\",[^}]*?\"sha256\":"
                + " \"([0-9a-f]+)\"").matcher(manifest);
        assertTrue(entry.find(), "the manifest entry for " + name);
        byte[] broken = "not a model".getBytes(StandardCharsets.UTF_8);
        files.put(entry.group(1), broken);
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(broken)) {
            hex.append(String.format("%02x", b));
        }
        return ModelRegistry.load(files, manifest.replace(entry.group(2), hex.toString()));
    }
}
