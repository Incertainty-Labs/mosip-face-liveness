package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.mosip.registration.liveness.api.AnalysedFrame;
import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.Decision;
import io.mosip.registration.liveness.api.EngineInfo;
import io.mosip.registration.liveness.api.FaceAnalyzer;
import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.LivenessCallback;
import io.mosip.registration.liveness.api.LivenessContext;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.LivenessResult;
import io.mosip.registration.liveness.api.LivenessSession;
import io.mosip.registration.liveness.api.PadDetector;
import io.mosip.registration.liveness.api.PadScore;
import io.mosip.registration.liveness.api.Rect;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.UserPrompt;
import io.mosip.registration.liveness.api.Workflow;
import io.mosip.registration.liveness.config.LivenessConfigLoader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The engine's state machine through its public API, with a scripted analyzer, scripted cues and a fixed challenge
 * list, on the caller's thread so every step is deterministic. Frames are 50 ms apart (20 fps).
 */
class HybridLivenessEngineTest {

    static final int W = 64;
    static final int H = 48;
    static final long STEP = 50;

    /** A centred, sharp, well-lit frontal face with eyes open and mouth closed. */
    static final FaceObservation FRONTAL = face(0, 10, 2, 0f, 0f, 0.8f);

    @Test
    @DisplayName("TC-ENG-11: HYBRID with a high passive score goes WAITING_FOR_FACE, PASSIVE_CHECK, NEUTRAL_HOLD,"
            + " PASSED, with no challenges")
    void passivePass() {
        Recorder recorder = new Recorder();
        LivenessSession session = engine(frame -> FRONTAL, 0.95, Collections.<ChallengeType>emptyList())
                .startSession(context(), policy("mode", "HYBRID"), recorder);

        feed(session, 0, 2000);

        LivenessResult r = recorder.result;
        assertAll(() -> assertEquals(Arrays.asList(SessionState.WAITING_FOR_FACE, SessionState.PASSIVE_CHECK,
                        SessionState.NEUTRAL_HOLD, SessionState.PASSED), recorder.states),
                () -> assertEquals(SessionState.PASSED, r.finalState()),
                () -> assertEquals(Decision.LIVE, r.decision()),
                () -> assertFalse(r.activeUsed()),
                () -> assertEquals(0.95, r.passiveScore(), 1e-9),
                () -> assertEquals(1, recorder.results, "onResult exactly once"));
    }

    @Test
    @DisplayName("TC-ENG-12: HYBRID below passive.threshold (0.70) escalates to the challenges; doing them passes"
            + " with activeUsed")
    void escalationPasses() {
        Recorder recorder = new Recorder();
        // Frontal for the passive window, a smile from 400 ms to 1 s, then frontal again for the neutral hold
        LivenessSession session = engine(frame -> frame.timestampMs() >= 400 && frame.timestampMs() < 1000
                ? face(0, 10, 2, 0.8f, 0f, 0.8f) : FRONTAL, 0.70, Collections.singletonList(ChallengeType.SMILE))
                .startSession(context(), policy("mode", "HYBRID"), recorder);

        feed(session, 0, 4000);

        LivenessResult r = recorder.result;
        assertAll(() -> assertEquals(Arrays.asList(SessionState.WAITING_FOR_FACE, SessionState.PASSIVE_CHECK,
                        SessionState.ACTIVE_CHALLENGE, SessionState.NEUTRAL_HOLD, SessionState.PASSED), recorder.states),
                () -> assertEquals(Arrays.asList(ChallengeType.SMILE, ChallengeType.NEUTRAL_HOLD), recorder.challenges),
                () -> assertEquals(Decision.LIVE, r.decision()),
                () -> assertEquals(true, r.activeUsed()),
                () -> assertEquals("SMILE:PASSED,NEUTRAL_HOLD:PASSED", r.challengeSummary()));
    }

    @ParameterizedTest(name = "{0}={1}, {2}={3}")
    @CsvSource({"mode, PASSIVE, active.enabled, true", "mode, HYBRID, active.enabled, false"})
    @DisplayName("TC-ENG-13: below passive.threshold with no escalation allowed fails with LIV-007, no challenges")
    void terminalBelowThresholdFails(String k1, String v1, String k2, String v2) {
        Recorder recorder = new Recorder();
        LivenessSession session = engine(frame -> FRONTAL, 0.70, Collections.singletonList(ChallengeType.SMILE))
                .startSession(context(), policy(k1, v1, k2, v2), recorder);

        feed(session, 0, 2000);

        assertAll(() -> assertEquals(SessionState.FAILED, recorder.result.finalState()),
                () -> assertEquals(Decision.INCONCLUSIVE, recorder.result.decision()),
                () -> assertEquals(LivenessErrorCode.BELOW_THRESHOLD, recorder.result.errorCode()),
                () -> assertEquals(Collections.emptyList(), recorder.challenges));
    }

    @Test
    @DisplayName("TC-ENG-14: mode ACTIVE runs the challenges even with a high passive score")
    void activeModeAlwaysChallenges() {
        Recorder recorder = new Recorder();
        LivenessSession session = engine(frame -> frame.timestampMs() >= 400 && frame.timestampMs() < 1000
                ? face(0, 10, 2, 0.8f, 0f, 0.8f) : FRONTAL, 0.95, Collections.singletonList(ChallengeType.SMILE))
                .startSession(context(), policy("mode", "ACTIVE"), recorder);

        feed(session, 0, 4000);

        assertAll(() -> assertEquals(Arrays.asList(ChallengeType.SMILE, ChallengeType.NEUTRAL_HOLD), recorder.challenges),
                () -> assertEquals(true, recorder.result.activeUsed()),
                () -> assertEquals(SessionState.PASSED, recorder.result.finalState()));
    }

    @Test
    @DisplayName("A challenge not done within challenge.timeout.ms fails the attempt with LIV-010 and is recorded"
            + " as TIMED_OUT")
    void challengeTimeoutFails() {
        Recorder recorder = new Recorder();
        LivenessSession session = engine(frame -> FRONTAL, 0.70, Collections.singletonList(ChallengeType.SMILE))
                .startSession(context(), policy("mode", "HYBRID", "challenge.timeout.ms", "2000"), recorder);

        feed(session, 0, 6000);

        assertAll(() -> assertEquals(SessionState.FAILED, recorder.result.finalState()),
                () -> assertEquals(Decision.INCONCLUSIVE, recorder.result.decision()),
                () -> assertEquals(LivenessErrorCode.CHALLENGE_TIMEOUT, recorder.result.errorCode()),
                () -> assertEquals("SMILE:TIMED_OUT", recorder.result.challengeSummary()));
    }

    @Test
    @DisplayName("During the challenges the passive median falling below passive.threshold - 0.2 fails with LIV-008,"
            + " attack suspected")
    void passiveVetoDuringChallenges() {
        Recorder recorder = new Recorder();
        // 0.70 for the passive window, then a replay that the cue scores 0.20
        PadDetector dropping = scripted("minifasnet_v2", frame -> frame.timestampMs() < 300 ? 0.70 : 0.20, false);
        LivenessSession session = engine(frame -> FRONTAL, Collections.singletonList(dropping),
                Collections.singletonList(ChallengeType.SMILE)).startSession(context(), policy("mode", "HYBRID"),
                        recorder);

        feed(session, 0, 4000);

        assertAll(() -> assertEquals(SessionState.FAILED, recorder.result.finalState()),
                () -> assertEquals(Decision.ATTACK_SUSPECTED, recorder.result.decision()),
                () -> assertEquals(LivenessErrorCode.PAD_FAILED, recorder.result.errorCode()));
    }

    @Test
    @DisplayName("The static-stream veto ends the session at once with LIV-015, attack suspected")
    void staticStreamVeto() {
        Recorder recorder = new Recorder();
        PadDetector frozen = scripted("static_stream", frame -> 1.0, true);
        LivenessSession session = engine(frame -> FRONTAL,
                Arrays.<PadDetector>asList(new Fixed("minifasnet_v2", 0.95), frozen),
                Collections.<ChallengeType>emptyList()).startSession(context(), policy(), recorder);

        feed(session, 0, 2000);

        assertAll(() -> assertEquals(SessionState.FAILED, recorder.result.finalState()),
                () -> assertEquals(Decision.ATTACK_SUSPECTED, recorder.result.decision()),
                () -> assertEquals(LivenessErrorCode.STATIC_STREAM, recorder.result.errorCode()),
                () -> assertEquals(1, recorder.result.framesAnalysed(), "on the first frame"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"a second face, MULTIPLE_FACES", "the face jumping, CHALLENGE_FAILED"})
    @DisplayName("TC-ENG-10: during the challenges, a second face fails with LIV-005 and a face that jumps with"
            + " LIV-009")
    void guardDuringChallenges(String what, LivenessErrorCode expected) {
        Recorder recorder = new Recorder();
        FaceObservation intruder = expected == LivenessErrorCode.MULTIPLE_FACES
                ? FaceObservation.builder().faces(2, new Rect(16, 4, 32, 40)).pose(0, 10, 2).expression(0f, 0f)
                        .eyesOpen(0.8f, 0.8f).quality(0.8f, 0.5f).build()
                : FaceObservation.builder().faces(1, new Rect(40, 4, 22, 40)).pose(0, 10, 2).expression(0f, 0f)
                        .eyesOpen(0.8f, 0.8f).quality(0.8f, 0.5f).build();
        LivenessSession session = engine(frame -> frame.timestampMs() == 600 ? intruder : FRONTAL, 0.70,
                Collections.singletonList(ChallengeType.SMILE)).startSession(context(), policy("mode", "HYBRID"),
                        recorder);

        feed(session, 0, 2000);

        assertAll(() -> assertEquals(SessionState.FAILED, recorder.result.finalState(), what),
                () -> assertEquals(Decision.INCONCLUSIVE, recorder.result.decision(), what),
                () -> assertEquals(expected, recorder.result.errorCode(), what));
    }

    @Test
    @DisplayName("No usable face for session.timeout.ms fails with LIV-010")
    void sessionTimeout() {
        Recorder recorder = new Recorder();
        LivenessSession session = engine(frame -> FaceObservation.builder().build(), 0.95,
                Collections.<ChallengeType>emptyList())
                .startSession(context(), policy("session.timeout.ms", "10000"), recorder);

        feed(session, 0, 15000);

        assertAll(() -> assertEquals(SessionState.FAILED, recorder.result.finalState()),
                () -> assertEquals(LivenessErrorCode.CHALLENGE_TIMEOUT, recorder.result.errorCode()),
                () -> assertEquals(10000L, recorder.result.durationMs()));
    }

    @Test
    @DisplayName("cancel() reports CANCELLED once; later frames and cancels are ignored")
    void cancelReportsOnce() {
        Recorder recorder = new Recorder();
        LivenessSession session = engine(frame -> FRONTAL, 0.70, Collections.singletonList(ChallengeType.SMILE))
                .startSession(context(), policy("mode", "HYBRID"), recorder);

        feed(session, 0, 500);
        session.cancel();
        session.cancel();
        feed(session, 550, 3000);

        assertAll(() -> assertEquals(SessionState.CANCELLED, recorder.result.finalState()),
                () -> assertEquals(Decision.INCONCLUSIVE, recorder.result.decision()),
                () -> assertEquals(1, recorder.results));
    }

    @Test
    @DisplayName("An analysis failure ends the session with ERROR and LIV-013, never an exception on the frame thread")
    void analysisFailureIsAnError() {
        Recorder recorder = new Recorder();
        LivenessSession session = engine(frame -> {
            throw new IllegalStateException("inference failed");
        }, 0.95, Collections.<ChallengeType>emptyList()).startSession(context(), policy(), recorder);

        feed(session, 0, 500);

        assertAll(() -> assertEquals(SessionState.ERROR, recorder.result.finalState()),
                () -> assertEquals(LivenessErrorCode.ENGINE_ERROR, recorder.result.errorCode()),
                () -> assertEquals(1, recorder.results));
    }

    @Test
    @DisplayName("Losing the face during the passive check goes back to WAITING_FOR_FACE and keeps the frames gathered")
    void faceLostDuringPassiveCheck() {
        Recorder recorder = new Recorder();
        // Two good frames, a gap without a face, then good frames again: the window fills with the third good frame
        LivenessSession session = engine(frame -> frame.timestampMs() >= 100 && frame.timestampMs() < 300
                ? FaceObservation.builder().build() : FRONTAL, 0.95, Collections.<ChallengeType>emptyList())
                .startSession(context(), policy(), recorder);

        feed(session, 0, 300);

        assertEquals(Arrays.asList(SessionState.WAITING_FOR_FACE, SessionState.PASSIVE_CHECK,
                SessionState.WAITING_FOR_FACE, SessionState.PASSIVE_CHECK, SessionState.NEUTRAL_HOLD),
                recorder.states, "three good frames in all, so the passive check ends at 300 ms");
    }

    @Test
    @DisplayName("TC-ENG-17: the evidence digest is SHA-256(nonce, transcript, SHA-256 of each analysed frame); the"
            + " same inputs give the same digest, one changed pixel a different one")
    void evidenceDigest() throws Exception {
        LivenessResult first = runWithFrames(0);
        LivenessResult again = runWithFrames(0);
        LivenessResult changed = runWithFrames(7);

        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        expected.update(first.nonce().getBytes(StandardCharsets.UTF_8));
        expected.update("NEUTRAL_HOLD:PASSED:1050".getBytes(StandardCharsets.UTF_8));
        for (long t = 0; t * STEP <= 1150; t++) {
            expected.update(MessageDigest.getInstance("SHA-256").digest(pixels(t, 0)));
        }
        assertAll(() -> assertEquals(hex(expected.digest()), first.evidenceDigest()),
                () -> assertEquals(first.evidenceDigest(), again.evidenceDigest()),
                () -> assertFalse(first.evidenceDigest().equals(changed.evidenceDigest())));
    }

    @Test
    @DisplayName("TC-ENG-18: frames arriving while the engine is busy replace each other; only the latest is analysed")
    void keepLatest() {
        List<Runnable> queued = new ArrayList<Runnable>();
        List<Long> analysed = new ArrayList<Long>();
        FaceAnalyzer analyzer = frame -> {
            analysed.add(frame.sequence());
            return FRONTAL;
        };
        LivenessSession session = new HybridLivenessEngine(new EngineParts(minScore -> analyzer,
                () -> Collections.<PadDetector>singletonList(new Fixed("minifasnet_v2", 0.95)),
                (policy, source, r) -> sequence(Collections.<ChallengeType>emptyList(), policy), new SecureRandom(),
                queued::add, new EngineInfo("test", Collections.<String, String>emptyMap(), "CPU")))
                .startSession(context(), policy(), new Recorder());

        for (long n = 0; n < 100; n++) {
            session.onFrame(new FaceFrame(W, H, new byte[W * H * 3], n, n, "test"));
        }
        assertEquals(1, queued.size(), "one drain scheduled for 100 frames");
        queued.get(0).run();

        assertEquals(Collections.singletonList(99L), analysed, "only the latest frame");
    }

    @Test
    @DisplayName("recentFrames() keeps the last 5 analysed frames for the capture binder; clear() drops them")
    void recentFramesAndClear() {
        LivenessSession session = engine(frame -> FRONTAL, 0.95, Collections.<ChallengeType>emptyList())
                .startSession(context(), policy(), new Recorder());

        feed(session, 0, 400);

        List<AnalysedFrame> recent = session.recentFrames();
        assertAll(() -> assertEquals(5, recent.size()),
                () -> assertEquals(400L, recent.get(4).frame().timestampMs(), "the latest last"));
        session.clear();
        assertEquals(0, session.recentFrames().size());
    }

    @Test
    @DisplayName("onProgress reports the passive window filling (1/3, 2/3, 3/3), then each challenge's elapsed share"
            + " of challenge.timeout.ms")
    void progress() {
        Recorder recorder = new Recorder();
        LivenessSession session = engine(frame -> FRONTAL, 0.95, Collections.<ChallengeType>emptyList())
                .startSession(context(), policy("challenge.timeout.ms", "2000"), recorder);

        feed(session, 0, 600);

        assertAll(() -> assertEquals(1f / 3, recorder.progress.get(0), 1e-6),
                () -> assertEquals(2f / 3, recorder.progress.get(1), 1e-6),
                () -> assertEquals(1f, recorder.progress.get(2), 1e-6),
                () -> assertEquals(0.25f, recorder.progress.get(recorder.progress.size() - 1), 1e-6,
                        "neutral hold from 100 ms, at 600 ms: 500 of 2000 ms"));
    }

    @Test
    @DisplayName("The buffered frames are dropped by the engine itself on CANCELLED, ERROR and session timeout"
            + " (API.md §3.4); a PASSED or FAILED session keeps them for the binder")
    void framesDroppedWhenNoCaptureFollows() {
        LivenessSession cancelled = engine(frame -> FRONTAL, 0.70, Collections.singletonList(ChallengeType.SMILE))
                .startSession(context(), policy("mode", "HYBRID"), new Recorder());
        feed(cancelled, 0, 300);
        cancelled.cancel();

        LivenessSession timedOut = engine(frame -> FaceObservation.builder().build(), 0.95,
                Collections.<ChallengeType>emptyList()).startSession(context(), policy("session.timeout.ms", "10000"),
                        new Recorder());
        feed(timedOut, 0, 15000);

        int[] calls = {0};
        LivenessSession error = engine(frame -> {
            if (calls[0]++ == 2) {
                throw new IllegalStateException("inference failed");
            }
            return FRONTAL;
        }, 0.95, Collections.<ChallengeType>emptyList()).startSession(context(), policy(), new Recorder());
        feed(error, 0, 500);

        LivenessSession passed = engine(frame -> FRONTAL, 0.95, Collections.<ChallengeType>emptyList())
                .startSession(context(), policy(), new Recorder());
        feed(passed, 0, 2000);

        assertAll(() -> assertEquals(0, cancelled.recentFrames().size(), "cancelled"),
                () -> assertEquals(0, timedOut.recentFrames().size(), "session timeout"),
                () -> assertEquals(0, error.recentFrames().size(), "error"),
                () -> assertEquals(5, passed.recentFrames().size(), "passed: kept for the binder"));
    }

    /** A passive pass over frames whose pixels are {@link #pixels}; {@code tamper} changes one pixel of frame 5. */
    private static LivenessResult runWithFrames(int tamper) {
        Recorder recorder = new Recorder();
        LivenessSession session = engine(frame -> FRONTAL, Collections.<PadDetector>singletonList(
                new Fixed("minifasnet_v2", 0.95)), Collections.<ChallengeType>emptyList(), fixedRandom())
                .startSession(context(), policy(), recorder);
        for (long t = 0; !session.state().isFinal() && t < 100; t++) {
            byte[] rgb = pixels(t, 0);
            if (t == 5 && tamper != 0) {
                rgb[10] = (byte) tamper;
            }
            session.onFrame(new FaceFrame(W, H, rgb, t * STEP, t, "test"));
        }
        return recorder.result;
    }

    private static byte[] pixels(long frame, int seed) {
        byte[] rgb = new byte[W * H * 3];
        for (int i = 0; i < rgb.length; i++) {
            rgb[i] = (byte) (i * 31 + frame * 7 + seed);
        }
        return rgb;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** A random source that always gives the same bytes, so the nonce repeats. */
    static SecureRandom fixedRandom() {
        return new SecureRandom() {
            private static final long serialVersionUID = 1L;

            @Override
            public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, (byte) 42);
            }
        };
    }

    // ----- helpers -----

    /** A cue whose live probability depends on the frame; {@code veto} makes it veto on every frame. */
    static PadDetector scripted(String name, java.util.function.ToDoubleFunction<FaceFrame> live, boolean veto) {
        return new PadDetector() {
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
                return new PadScore(live.applyAsDouble(frame), veto, Collections.<String, Double>emptyMap());
            }
        };
    }

    static HybridLivenessEngine engine(java.util.function.Function<FaceFrame, FaceObservation> analysis,
            double live, List<ChallengeType> challenges) {
        return engine(analysis, Collections.<PadDetector>singletonList(new Fixed("minifasnet_v2", live)), challenges);
    }

    static HybridLivenessEngine engine(java.util.function.Function<FaceFrame, FaceObservation> analysis,
            List<PadDetector> cues, List<ChallengeType> challenges) {
        return engine(analysis, cues, challenges, new SecureRandom());
    }

    static HybridLivenessEngine engine(java.util.function.Function<FaceFrame, FaceObservation> analysis,
            List<PadDetector> cues, List<ChallengeType> challenges, SecureRandom random) {
        FaceAnalyzer analyzer = analysis::apply;
        return new HybridLivenessEngine(new EngineParts(
                minScore -> analyzer,
                () -> cues,
                (policy, source, r) -> sequence(challenges, policy),
                random,
                Runnable::run,
                new EngineInfo("test", Collections.<String, String>emptyMap(), "CPU")));
    }

    static List<Challenge> sequence(List<ChallengeType> types, LivenessPolicy policy) {
        List<Challenge> list = new ArrayList<Challenge>();
        for (ChallengeType t : types) {
            list.add(new Challenge(t, policy.challengeTimeoutMs()));
        }
        list.add(new Challenge(ChallengeType.NEUTRAL_HOLD, policy.challengeTimeoutMs()));
        return list;
    }

    static LivenessContext context() {
        return LivenessContext.builder().workflow(Workflow.RESIDENT_REGISTRATION).build();
    }

    /** The default policy with liveness on, 3 passive frames and the given extra key/value pairs. */
    static LivenessPolicy policy(String... keyValues) {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.liveness.enabled", "true");
        config.put("mosip.registration.face.liveness.passive.min.frames", "3");
        config.put("mosip.registration.face.liveness.pad.weights", "minifasnet_v2:1.0");
        for (int i = 0; i < keyValues.length; i += 2) {
            config.put("mosip.registration.face.liveness." + keyValues[i], keyValues[i + 1]);
        }
        return LivenessConfigLoader.load(config, Workflow.RESIDENT_REGISTRATION);
    }

    /** Feeds a frame every 50 ms from {@code fromMs} up to {@code toMs}, stopping once a result arrived. */
    static void feed(LivenessSession session, long fromMs, long toMs) {
        for (long t = fromMs; t <= toMs && !session.state().isFinal(); t += STEP) {
            session.onFrame(new FaceFrame(W, H, new byte[W * H * 3], t, t / STEP, "test"));
        }
    }

    static FaceObservation face(float yaw, float pitch, float roll, float smile, float mouth, float eyes) {
        return FaceObservation.builder().faces(1, new Rect(16, 4, 32, 40)).pose(yaw, pitch, roll)
                .expression(smile, mouth).eyesOpen(eyes, eyes).quality(0.8f, 0.5f).build();
    }

    /** Records what the session reported. */
    static final class Recorder implements LivenessCallback {
        final List<SessionState> states = new ArrayList<SessionState>();
        final List<UserPrompt> prompts = new ArrayList<UserPrompt>();
        final List<ChallengeType> challenges = new ArrayList<ChallengeType>();
        final List<Float> progress = new ArrayList<Float>();
        LivenessResult result;
        int results;

        @Override
        public void onStateChanged(SessionState state, UserPrompt prompt) {
            if (states.isEmpty() || states.get(states.size() - 1) != state) {
                states.add(state);
            }
            prompts.add(prompt);
        }

        @Override
        public void onChallenge(Challenge challenge, int index, int total) {
            challenges.add(challenge.type());
        }

        @Override
        public void onProgress(float fraction) {
            progress.add(fraction);
        }

        @Override
        public void onResult(LivenessResult r) {
            result = r;
            results++;
            if (states.isEmpty() || states.get(states.size() - 1) != r.finalState()) {
                states.add(r.finalState());
            }
        }
    }

    /** A cue that always gives the same live probability. */
    static final class Fixed implements PadDetector {
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
