package io.mosip.registration.liveness.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModelRegistryTest {

    private static final File MODELS = new File("../models");

    @Test
    @DisplayName("TC-ENG-19: the shipped bundle loads; versions come from models.json and every file is available")
    void loadsTheShippedBundle() throws Exception {
        ModelRegistry registry = ModelRegistry.load(MODELS.toPath());

        assertAll(() -> assertEquals("1.0.0", registry.bundleVersion()),
                () -> assertEquals("2023mar", registry.versions().get("yunet")),
                () -> assertEquals("1.0", registry.versions().get("minifasnet_v2")),
                () -> assertEquals(new File(MODELS, "face_detection_yunet_2023mar.onnx").length(),
                        registry.bytes("yunet").length));
    }

    @Test
    @DisplayName("TC-ENG-19: one changed byte in a model file refuses the bundle with LIV-013 (fail closed)")
    void tamperedFileIsRefused() throws Exception {
        Map<String, byte[]> files = shippedFiles();
        files.get("minifasnet_v2_2.7_80x80.onnx")[1000] ^= 1;

        LivenessException e = assertThrows(LivenessException.class, () -> ModelRegistry.load(files, manifest()));

        assertAll(() -> assertEquals(LivenessErrorCode.ENGINE_ERROR, e.errorCode()),
                () -> assertTrue(e.getMessage().contains("minifasnet_v2_2.7_80x80.onnx"), e.getMessage()));
    }

    @Test
    @DisplayName("TC-ENG-19: a missing file, a manifest that is not JSON, or a model without its SHA-256 is refused"
            + " with LIV-013")
    void incompleteBundleIsRefused() throws Exception {
        Map<String, byte[]> missing = shippedFiles();
        missing.remove("face_detection_yunet_2023mar.onnx");
        String noHash = manifest().replaceFirst("\"sha256\": \"[0-9a-f]+\",", "");

        assertAll(
                () -> assertEquals(LivenessErrorCode.ENGINE_ERROR, assertThrows(LivenessException.class,
                        () -> ModelRegistry.load(missing, manifest())).errorCode(), "missing file"),
                () -> assertEquals(LivenessErrorCode.ENGINE_ERROR, assertThrows(LivenessException.class,
                        () -> ModelRegistry.load(shippedFiles(), "{ not json")).errorCode(), "not JSON"),
                () -> assertEquals(LivenessErrorCode.ENGINE_ERROR, assertThrows(LivenessException.class,
                        () -> ModelRegistry.load(shippedFiles(), noHash)).errorCode(), "no SHA-256"));
    }

    private static String manifest() throws IOException {
        return new String(Files.readAllBytes(new File(MODELS, "models.json").toPath()), StandardCharsets.UTF_8);
    }

    private static Map<String, byte[]> shippedFiles() throws IOException {
        Map<String, byte[]> files = new HashMap<String, byte[]>();
        for (File f : MODELS.listFiles((dir, name) -> name.endsWith(".onnx"))) {
            files.put(f.getName(), Files.readAllBytes(f.toPath()));
        }
        return files;
    }
}
