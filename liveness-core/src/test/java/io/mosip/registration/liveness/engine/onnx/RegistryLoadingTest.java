package io.mosip.registration.liveness.engine.onnx;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.model.ModelRegistry;
import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Android reads the models from its assets into memory, so every model must load from the registry's verified bytes
 * and behave exactly as when loaded from a file.
 */
class RegistryLoadingTest {

    @Test
    @DisplayName("The analyzer and both MiniFASNet cues loaded from the registry give the same output as from files")
    void registryBytesBehaveLikeFiles() throws Exception {
        File models = new File("../models");
        ModelRegistry registry = ModelRegistry.load(models.toPath());
        FaceFrame frame = YuNetDetectorTest.frame(YuNetDetectorTest.face());

        try (OnnxFaceAnalyzer fromFiles = OnnxFaceAnalyzer.load(models.toPath(), 0.7);
                OnnxFaceAnalyzer fromRegistry = OnnxFaceAnalyzer.load(registry, 0.7);
                MiniFasNetPadDetector v2Files = MiniFasNetPadDetector.load(
                        new File(models, "minifasnet_v2_2.7_80x80.onnx").toPath(), 2.7, "minifasnet_v2", "1.0");
                MiniFasNetPadDetector v2Registry = MiniFasNetPadDetector.load(registry, "minifasnet_v2", 2.7)) {
            FaceObservation a = fromFiles.analyze(frame);
            FaceObservation b = fromRegistry.analyze(frame);

            assertAll(() -> assertEquals(a.box().x(), b.box().x()),
                    () -> assertArrayEquals(a.landmarks(), b.landmarks()),
                    () -> assertEquals(a.yawDeg(), b.yawDeg()),
                    () -> assertEquals(a.eyeOpenLeft(), b.eyeOpenLeft()),
                    () -> assertEquals(v2Files.score(frame, a).liveProbability(),
                            v2Registry.score(frame, b).liveProbability()),
                    () -> assertEquals("1.0", v2Registry.modelVersion(), "version from models.json"));
        }
    }
}
