package io.mosip.registration.liveness.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Checks the shipped model bundle in {@code models/}: manifest, integrity, licences and ONNX Runtime loading. */
class ModelBundleTest {

    private static final File MODEL_DIR = new File("../models");
    private static final Set<String> OSI_LICENCES = new HashSet<String>(Arrays.asList("MIT", "Apache-2.0"));

    static Stream<JsonNode> models() throws Exception {
        JsonNode manifest = new ObjectMapper().readTree(new File(MODEL_DIR, "models.json"));
        List<JsonNode> entries = new ArrayList<JsonNode>();
        manifest.get("models").forEach(entries::add);
        return entries.stream();
    }

    @Test
    @DisplayName("The manifest lists the detector, landmarks, blendshapes, matcher and both PAD models")
    void manifestListsTheShippedModels() throws Exception {
        Set<String> names = new HashSet<String>();
        models().forEach(m -> names.add(m.get("name").asText()));

        assertEquals(new HashSet<String>(Arrays.asList("yunet", "facemesh_v2", "blendshape_v2", "sface",
                "minifasnet_v2", "minifasnet_v1se")), names);
    }

    @Test
    @DisplayName("The licence texts the bundled models require are shipped with them")
    void licenceTextsAreShipped() {
        assertAll(
                () -> assertTrue(new File(MODEL_DIR, "licenses/Apache-2.0.txt").isFile(), "Apache-2.0"),
                () -> assertTrue(new File(MODEL_DIR, "licenses/MIT-YuNet.txt").isFile(), "MIT for YuNet"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("models")
    @DisplayName("Each model file exists, matches its SHA-256 and has an OSI licence and a source")
    void modelFileMatchesManifest(JsonNode model) throws Exception {
        File file = new File(MODEL_DIR, model.get("file").asText());
        assertTrue(file.isFile(), "missing " + file);

        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }

        assertAll(
                () -> assertEquals(model.get("sha256").asText(), hex.toString(), "sha256"),
                () -> assertTrue(OSI_LICENCES.contains(model.get("license").asText()), "license"),
                () -> assertTrue(model.get("source").asText().startsWith("https://"), "source"),
                () -> assertFalse(model.get("version").asText().isEmpty(), "version"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("models")
    @DisplayName("Each model loads in ONNX Runtime with the input shape the manifest declares")
    void modelLoadsWithDeclaredInput(JsonNode model) throws Exception {
        long[] declared = new long[model.get("input").size()];
        for (int i = 0; i < declared.length; i++) {
            declared[i] = model.get("input").get(i).asLong();
        }

        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (OrtSession session = env.createSession(
                new File(MODEL_DIR, model.get("file").asText()).getPath(), new OrtSession.SessionOptions())) {
            NodeInfo input = session.getInputInfo().values().iterator().next();
            assertArrayEquals(declared, ((TensorInfo) input.getInfo()).getShape());
        }
    }
}
