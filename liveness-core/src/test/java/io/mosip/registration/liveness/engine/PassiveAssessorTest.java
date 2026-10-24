package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.PadDetector;
import io.mosip.registration.liveness.api.PadScore;
import io.mosip.registration.liveness.api.Rect;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PassiveAssessorTest {

    private static final int W = 64;
    private static final int H = 48;
    /** A centred face half the frame wide, sharp and well lit: passes the quality gate. */
    private static final FaceObservation GOOD = FaceObservation.builder().faces(1, new Rect(16, 4, 32, 40))
            .quality(0.7f, 0.5f).build();

    @Test
    @DisplayName("Each frame's score is the weighted mean of the cues; the window score is unknown until it is full")
    void weightedMeanOncePassiveWindowIsFull() {
        PassiveAssessor assessor = new PassiveAssessor(
                Arrays.<PadDetector>asList(new Fixed("minifasnet_v2", 0.9), new Fixed("minifasnet_v1se", 0.5)),
                policy("minifasnet_v2:0.75,minifasnet_v1se:0.25", 8));

        for (int n = 0; n < 7; n++) {
            assertEquals(0.8, assessor.add(frame(n), GOOD), 1e-9, "frame " + n);
            assertTrue(Double.isNaN(assessor.score()), "window not full after " + (n + 1) + " frames");
        }
        assertEquals(0.8, assessor.add(frame(7), GOOD), 1e-9);
        assertEquals(0.8, assessor.score(), 1e-9, "window full after 8 frames");
    }

    @Test
    @DisplayName("The window score is the median of the last passive.min.frames frames: one odd frame does not move it")
    void medianOverTheLastFrames() {
        Scripted cue = new Scripted("minifasnet_v2", 0.9, 0.1, 0.9, 0.2, 0.3);
        PassiveAssessor assessor = new PassiveAssessor(Arrays.<PadDetector>asList(cue),
                policy("minifasnet_v2:1.0", 3));

        assessor.add(frame(0), GOOD);
        assessor.add(frame(1), GOOD);
        assessor.add(frame(2), GOOD);
        assertEquals(0.9, assessor.score(), 1e-9, "0.9, 0.1, 0.9: the 0.1 is outvoted");
        assessor.add(frame(3), GOOD);
        assertEquals(0.2, assessor.score(), 1e-9, "0.1, 0.9, 0.2: the first 0.9 has left the window");
        assessor.add(frame(4), GOOD);
        assertEquals(0.3, assessor.score(), 1e-9, "0.9, 0.2, 0.3");
    }

    @Test
    @DisplayName("Frames the quality gate rejects are skipped, not scored, and do not restart the window")
    void framesFailingTheQualityGateAreSkipped() {
        Scripted cue = new Scripted("minifasnet_v2", 0.9, 0.8, 0.7);
        PassiveAssessor assessor = new PassiveAssessor(Arrays.<PadDetector>asList(cue),
                policy("minifasnet_v2:1.0", 3));
        FaceObservation blurred = FaceObservation.builder().faces(1, new Rect(16, 4, 32, 40)).quality(0.1f, 0.5f)
                .build();
        FaceObservation noFace = FaceObservation.builder().build();

        assessor.add(frame(0), GOOD);
        assertTrue(Double.isNaN(assessor.add(frame(1), blurred)), "blurred frame not scored");
        assessor.add(frame(2), GOOD);
        assertTrue(Double.isNaN(assessor.add(frame(3), noFace)), "frame without a face not scored");
        assessor.add(frame(4), GOOD);

        assertEquals(3, cue.calls, "the cue ran on the three good frames only");
        assertEquals(0.8, assessor.score(), 1e-9, "window of the three good frames");
    }

    @Test
    @DisplayName("The weights are renormalised over the cues actually loaded")
    void weightsAreRenormalisedOverLoadedCues() {
        PassiveAssessor assessor = new PassiveAssessor(Arrays.<PadDetector>asList(new Fixed("minifasnet_v2", 0.9)),
                policy("minifasnet_v2:0.6,minifasnet_v1se:0.4", 3));

        assertEquals(0.9, assessor.add(frame(0), GOOD), 1e-9, "minifasnet_v2 alone carries all the weight");
    }

    @Test
    @DisplayName("Without a weighted cue there is no passive score, so the assessor refuses to start")
    void refusesWithoutAWeightedCue() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new PassiveAssessor(Arrays.<PadDetector>asList(new Fixed("static_stream", 1.0)),
                        policy("minifasnet_v2:1.0", 3)));

        assertTrue(e.getMessage().contains("minifasnet_v2"), e.getMessage());
    }

    @Test
    @DisplayName("A cue without a weight only vetoes: it sees every frame, faceless ones too, and its veto stays")
    void unweightedCueVetoesAndTheVetoStays() {
        VetoOnCall frozen = new VetoOnCall("static_stream", 3);
        PassiveAssessor assessor = new PassiveAssessor(
                Arrays.<PadDetector>asList(new Fixed("minifasnet_v2", 0.9), frozen), policy("minifasnet_v2:1.0", 3));

        assessor.add(frame(0), GOOD);
        assessor.add(frame(1), FaceObservation.builder().build());
        assertNull(assessor.veto(), "no veto yet");
        assessor.add(frame(2), GOOD);
        assertEquals("static_stream", assessor.veto());
        assessor.add(frame(3), GOOD);

        assertEquals(4, frozen.calls, "ran on all four frames, the faceless one included");
        assertEquals("static_stream", assessor.veto(), "the veto stays");
    }

    @Test
    @DisplayName("cues() gives each weighted model's own median over the window, for diagnostics and audit")
    void cuesAreEachModelsMedian() {
        PassiveAssessor assessor = new PassiveAssessor(Arrays.<PadDetector>asList(
                new Scripted("minifasnet_v2", 0.9, 0.1, 0.8, 0.7),
                new Scripted("minifasnet_v1se", 0.2, 0.3, 0.4, 0.6)),
                policy("minifasnet_v2:0.5,minifasnet_v1se:0.5", 3));

        assertEquals(Collections.emptyMap(), assessor.cues(), "nothing scored yet");
        for (int n = 0; n < 4; n++) {
            assessor.add(frame(n), GOOD);
        }

        Map<String, Double> cues = assessor.cues();
        assertEquals(2, cues.size());
        assertEquals(0.7, cues.get("minifasnet_v2"), 1e-9, "median of 0.1, 0.8, 0.7");
        assertEquals(0.4, cues.get("minifasnet_v1se"), 1e-9, "median of 0.3, 0.4, 0.6");
    }

    private static LivenessPolicy policy(String weights, int minFrames) {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness.pad.weights", weights);
        config.put("mosip.registration.face.liveness.passive.min.frames", String.valueOf(minFrames));
        LivenessPolicy policy = LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);
        assertEquals(Collections.emptyList(), policy.warnings());
        return policy;
    }

    private static FaceFrame frame(long n) {
        return new FaceFrame(W, H, new byte[W * H * 3], n * 50, n, "test");
    }

    /** A cue that gives the scripted live probabilities in turn, one per scored frame. */
    private static final class Scripted implements PadDetector {
        private final String name;
        private final double[] live;
        int calls;

        Scripted(String name, double... live) {
            this.name = name;
            this.live = live;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String modelVersion() {
            return "test";
        }

        @Override
        public PadScore score(FaceFrame frame, FaceObservation observation) {
            double p = live[calls++];
            return new PadScore(p, false, Collections.singletonMap(name, p));
        }
    }

    /** A veto-only cue that vetoes on exactly one call, by number from 1. */
    private static final class VetoOnCall implements PadDetector {
        private final String name;
        private final int vetoCall;
        int calls;

        VetoOnCall(String name, int vetoCall) {
            this.name = name;
            this.vetoCall = vetoCall;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String modelVersion() {
            return "test";
        }

        @Override
        public PadScore score(FaceFrame frame, FaceObservation observation) {
            boolean veto = ++calls == vetoCall;
            return new PadScore(veto ? 0.0 : 1.0, veto, Collections.<String, Double>emptyMap());
        }
    }

    /** A cue that always gives the same live probability. */
    private static final class Fixed implements PadDetector {
        private final String name;
        private final double live;

        Fixed(String name, double live) {
            this.name = name;
            this.live = live;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String modelVersion() {
            return "test";
        }

        @Override
        public PadScore score(FaceFrame frame, FaceObservation observation) {
            return new PadScore(live, false, Collections.singletonMap(name, live));
        }
    }
}
