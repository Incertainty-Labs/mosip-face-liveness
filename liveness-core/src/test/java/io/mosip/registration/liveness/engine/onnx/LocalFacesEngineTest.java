package io.mosip.registration.liveness.engine.onnx;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.Decision;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.LivenessCallback;
import io.mosip.registration.liveness.api.LivenessContext;
import io.mosip.registration.liveness.api.LivenessEngine;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.LivenessSession;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.UserPrompt;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import io.mosip.registration.liveness.engine.EngineOptions;
import io.mosip.registration.liveness.engine.LivenessEngines;
import io.mosip.registration.liveness.model.ModelRegistry;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The real engine (real models, its own worker thread) on a team member's own 2 s bursts, kept on the developer's
 * machine only (never in git); skipped when they are missing. Frames are fed at their capture rate, 50 ms apart.
 */
@Tag("local-faces")
class LocalFacesEngineTest {

    private static final File FACES = new File(System.getProperty("liveness.localFaces",
            "../../Mossip-local/faces/retake"));
    private static final File ATTACKS = new File(System.getProperty("liveness.localAttacks",
            "../../Mossip-local/faces/attacks"));
    private static LivenessEngine engine;

    @BeforeAll
    static void load() throws Exception {
        assumeTrue(FACES.isDirectory() && ATTACKS.isDirectory(), "local clips not present");
        engine = LivenessEngines.hybrid(ModelRegistry.load(new File("../models").toPath()), EngineOptions.defaults());
    }

    @Test
    @DisplayName("TC-ENG-11 on a real face: the straight clip passes in HYBRID, passively, on the worker thread")
    void realFacePasses() throws Exception {
        Result result = run(new File(FACES, "straight_burst"));

        assertAll(() -> assertEquals(SessionState.PASSED, result.result.finalState()),
                () -> assertEquals(Decision.LIVE, result.result.decision()),
                () -> assertEquals(false, result.result.activeUsed()),
                () -> assertTrue(result.result.passiveScore() > 0.8, "score " + result.result.passiveScore()));
    }

    @Test
    @DisplayName("A phone photo of the same face never passes")
    void phonePhotoNeverPasses() throws Exception {
        Result result = run(new File(ATTACKS, "phone_photo_30cm_burst"));

        assertTrue(result.result == null || result.result.finalState() != SessionState.PASSED,
                "result " + (result.result == null ? "none" : result.result.finalState()));
    }

    private static Result run(File burst) throws Exception {
        File[] files = burst.listFiles((d, n) -> n.endsWith(".jpg"));
        assumeTrue(files != null && files.length > 0, "clip not present: " + burst);
        Arrays.sort(files);
        Result result = new Result();
        LivenessSession session = engine.startSession(
                LivenessContext.builder().workflow(Workflow.RESIDENT_REGISTRATION).build(), policy(), result);
        for (int i = 0; i < files.length && result.done.getCount() > 0; i++) {
            FaceFrame frame = YuNetDetectorTest.frame(ImageIO.read(files[i]));
            session.onFrame(new FaceFrame(frame.width(), frame.height(), frame.rgb(), i * 50L, i, "clip"));
            // Give the worker time for each frame, as a camera at 20 fps would
            Thread.sleep(60);
        }
        result.done.await(5, TimeUnit.SECONDS);
        session.cancel();
        return result;
    }

    private static LivenessPolicy policy() {
        return LivenessConfigLoader.load(Collections.singletonMap("mosip.registration.face.liveness.enabled", "true"),
                Workflow.RESIDENT_REGISTRATION);
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
            if (r.finalState() != SessionState.CANCELLED) {
                result = r;
            }
            done.countDown();
        }
    }
}
