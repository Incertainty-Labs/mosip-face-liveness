package io.mosip.registration.liveness.engine.onnx;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtProvider;
import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.LivenessCallback;
import io.mosip.registration.liveness.api.LivenessContext;
import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.LivenessSession;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.UserPrompt;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import io.mosip.registration.liveness.engine.EngineOptions;
import io.mosip.registration.liveness.engine.LivenessEngines;
import io.mosip.registration.liveness.model.ModelRegistry;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The real engine with the options for slow devices: acceleration with a CPU fallback, threads, sparse detection. */
class EngineOptionsTest {

    @Test
    @DisplayName("Asking for XNNPACK uses it where ONNX Runtime has it and CPU otherwise, says which in EngineInfo, and"
            + " the engine (2 threads, detector every 3rd frame) still passes a live face drifting in front of the camera")
    void acceleratedSparseEngineStillPasses() throws Exception {
        LivenessEngine engine = LivenessEngines.hybrid(ModelRegistry.load(new File("../models").toPath()),
                EngineOptions.withProvider("XNNPACK").threads(2).detectEvery(3));
        String expected = OrtEnvironment.getAvailableProviders().contains(OrtProvider.XNNPACK) ? "XNNPACK" : "CPU";

        BufferedImage face = YuNetDetectorTest.face();
        Result result = new Result();
        LivenessSession session = engine.startSession(
                LivenessContext.builder().workflow(Workflow.RESIDENT_REGISTRATION).build(),
                LivenessConfigLoader.load(Collections.singletonMap("mosip.registration.face.liveness.enabled", "true"),
                        Workflow.RESIDENT_REGISTRATION), result);
        for (int n = 0; n < 80 && result.done.getCount() > 0; n++) {
            FaceFrame frame = drifted(face, n % 10 < 5 ? n % 10 : 10 - n % 10);
            session.onFrame(new FaceFrame(frame.width(), frame.height(), frame.rgb(), n * 50L, n, "test"));
            Thread.sleep(30);
        }
        result.done.await(10, TimeUnit.SECONDS);

        assertAll(() -> assertEquals(expected, engine.info().executionProvider()),
                () -> assertTrue(result.result != null, "a result"),
                () -> assertEquals(SessionState.PASSED, result.result.finalState(), result.result.errorCode() + " activeUsed " + result.result.activeUsed() + " score " + result.result.passiveScore()));
    }

    @Test
    @DisplayName("If an accelerator is tried and fails to start (forced here: this PC's ONNX Runtime has no XNNPACK),"
            + " the engine falls back to CPU instead of refusing to start")
    void acceleratorFailureFallsBackToCpu() throws Exception {
        ModelRegistry models = ModelRegistry.load(new File("../models").toPath());
        org.junit.jupiter.api.Assumptions.assumeFalse(
                OrtEnvironment.getAvailableProviders().contains(OrtProvider.XNNPACK), "XNNPACK works here");

        LivenessEngine engine = LivenessEnginesAccess.hybridTrying(models, EngineOptions.defaults(), "XNNPACK");

        assertEquals("CPU", engine.info().executionProvider());
    }

    @Test
    @DisplayName("Options default to the platform's best guess (AUTO), all ONNX Runtime threads, the detector on every"
            + " frame; an unknown provider name means AUTO")
    void defaults() {
        EngineOptions defaults = EngineOptions.defaults();

        assertAll(() -> assertEquals("AUTO", defaults.requestedProvider()), () -> assertEquals(0, defaults.threads()),
                () -> assertEquals(1, defaults.detectEvery()),
                () -> assertEquals("AUTO", EngineOptions.withProvider("TURBO").requestedProvider()));
    }

    /**
     * MOSIP's face frame with its content moved right by {@code px}, the edge repeated: a live face drifting a little
     * (a photo pasted on a canvas would rightly score as a spoof).
     */
    static FaceFrame drifted(BufferedImage src, int px) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                out.setRGB(x, y, src.getRGB(Math.max(0, x - px), y));
            }
        }
        return YuNetDetectorTest.frame(out);
    }

    private static final class Result implements LivenessCallback {
        final CountDownLatch done = new CountDownLatch(1);
        volatile LivenessResult result;

        @Override
        public void onStateChanged(SessionState state, UserPrompt prompt) {
        }

        @Override
        public void onChallenge(Challenge challenge, int index, int total) {
        }

        @Override
        public void onProgress(float fraction) {
        }

        @Override
        public void onResult(LivenessResult r) {
            result = r;
            done.countDown();
        }
    }
}
