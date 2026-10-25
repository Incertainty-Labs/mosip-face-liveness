package io.mosip.registration.liveness.desktop.overlay;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.MaxRetriesAction;
import io.mosip.registration.liveness.api.PromptLevel;
import io.mosip.registration.liveness.api.SessionState;
import io.mosip.registration.liveness.api.UserPrompt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** What the liveness overlay shows for each engine event (UI_UX_SPEC §4, states S1 to S14). */
class OverlayModelTest {

    private final List<OverlayView> views = new ArrayList<OverlayView>();
    private final OverlayModel model = new OverlayModel(views::add);

    @Test
    @DisplayName("S1 to S4, S7 to S9: device search, position, guidance, passive check with its ring, the neutral hold"
            + " without step dots when it is the only step, capturing without buttons, then success with Next")
    void passiveFlow() {
        model.searching();
        OverlayView s1 = last();
        model.state(SessionState.WAITING_FOR_FACE, new UserPrompt("liveness.status.position", PromptLevel.INFO));
        OverlayView s2 = last();
        model.state(SessionState.WAITING_FOR_FACE,
                new UserPrompt("liveness.guidance.improve_lighting", PromptLevel.GUIDANCE));
        OverlayView s3 = last();
        model.state(SessionState.PASSIVE_CHECK, new UserPrompt("liveness.status.checking", PromptLevel.INFO));
        model.progress(0.5f);
        OverlayView s4 = last();
        model.state(SessionState.NEUTRAL_HOLD, new UserPrompt("liveness.prompt.neutral", PromptLevel.INFO));
        model.challenge(new Challenge(ChallengeType.NEUTRAL_HOLD, 5000), 0, 1);
        model.progress(0.2f);
        OverlayView s7 = last();
        model.capturing();
        OverlayView s8 = last();
        model.succeeded();
        OverlayView s9 = last();

        assertAll(() -> assertView(s1, UiState.DEVICE_SEARCH, "liveness.status.device_search", null, Action.CANCEL),
                () -> assertView(s2, UiState.POSITION_FACE, "liveness.status.position", null, Action.CANCEL),
                () -> assertView(s3, UiState.GUIDANCE, "liveness.status.position",
                        "liveness.guidance.improve_lighting", Action.CANCEL),
                () -> assertEquals(Level.GUIDANCE, s3.level()),
                () -> assertView(s4, UiState.PASSIVE_CHECK, "liveness.status.checking", "liveness.status.no_action",
                        Action.CANCEL),
                () -> assertEquals(0.5f, s4.ring(), 1e-6),
                () -> assertView(s7, UiState.NEUTRAL_HOLD, "liveness.status.checking", "liveness.prompt.neutral",
                        Action.CANCEL),
                () -> assertEquals(0, s7.stepTotal(), "no step dots for the hold alone"),
                () -> assertEquals(0.8f, s7.timeLeft(), 1e-6),
                () -> assertView(s8, UiState.CAPTURING, "liveness.feedback.hold_still", null),
                () -> assertView(s9, UiState.SUCCESS, "liveness.feedback.success", null, Action.NEXT),
                () -> assertEquals(Level.SUCCESS, s9.level()));
    }

    @Test
    @DisplayName("S5 to S7: each challenge shows Step i of n, its prompt and direction cue, and a time bar; a new step"
            + " says the last one was detected (S6); the neutral hold is the last step")
    void challengeFlow() {
        model.state(SessionState.ACTIVE_CHALLENGE, new UserPrompt("liveness.prompt.turn_left", PromptLevel.INFO));
        model.challenge(new Challenge(ChallengeType.TURN_LEFT, 5000), 0, 3);
        model.progress(0.25f);
        OverlayView first = last();
        model.challenge(new Challenge(ChallengeType.SMILE, 5000), 1, 3);
        OverlayView second = last();
        model.state(SessionState.NEUTRAL_HOLD, new UserPrompt("liveness.prompt.neutral", PromptLevel.INFO));
        model.challenge(new Challenge(ChallengeType.NEUTRAL_HOLD, 5000), 2, 3);
        OverlayView hold = last();

        assertAll(() -> assertView(first, UiState.CHALLENGE, "liveness.progress.step", "liveness.prompt.turn_left",
                        Action.CANCEL),
                () -> assertArrayEquals(new Object[] {1, 3}, first.statusArgs()),
                () -> assertEquals(Cue.LEFT, first.cue()),
                () -> assertEquals(0.75f, first.timeLeft(), 1e-6),
                () -> assertNull(first.toastKey(), "nothing detected yet"),
                () -> assertArrayEquals(new Object[] {2, 3}, second.statusArgs()),
                () -> assertEquals(Cue.SMILE, second.cue()),
                () -> assertEquals("liveness.feedback.detected", second.toastKey()),
                () -> assertEquals(1f, second.timeLeft(), 1e-6, "a new step has its whole time"),
                () -> assertView(hold, UiState.NEUTRAL_HOLD, "liveness.progress.step", "liveness.prompt.neutral",
                        Action.CANCEL),
                () -> assertArrayEquals(new Object[] {3, 3}, hold.statusArgs()),
                () -> assertEquals(Cue.NONE, hold.cue()));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "LIV-007, LIVENESS_FAILURE, liveness.fail.try_again, RETRY CANCEL",
        "LIV-009, LIVENESS_FAILURE, liveness.fail.try_again, RETRY CANCEL",
        "LIV-010, LIVENESS_FAILURE, liveness.fail.timeout, RETRY CANCEL",
        "LIV-005, LIVENESS_FAILURE, liveness.guidance.one_face_only, RETRY CANCEL",
        "LIV-008, PAD_FAILURE, liveness.fail.generic, RETRY CANCEL",
        "LIV-014, PAD_FAILURE, liveness.fail.generic, RETRY CANCEL",
        "LIV-015, PAD_FAILURE, liveness.fail.generic, RETRY CANCEL",
        "LIV-001, DEVICE_ERROR, liveness.error.device_unavailable, RESCAN RETRY CANCEL",
        "LIV-002, DEVICE_ERROR, liveness.error.device_connection, RESCAN RETRY CANCEL",
        "LIV-003, DEVICE_ERROR, liveness.error.camera_unavailable, RESCAN RETRY CANCEL",
        "LIV-012, DEVICE_ERROR, liveness.error.invalid_frame, RESCAN RETRY CANCEL",
        "LIV-013, ENGINE_ERROR, liveness.error.engine, CLOSE"})
    @DisplayName("S10, S11, S13, S14: each error code's state, message and buttons, with the attempt shown after a"
            + " liveness or PAD failure")
    void failures(String code, UiState state, String statusKey, String buttons) {
        model.failed(LivenessErrorCode.fromCode(code), 1, 3, true, MaxRetriesAction.BLOCK);
        OverlayView v = last();

        boolean attemptShown = state == UiState.LIVENESS_FAILURE || state == UiState.PAD_FAILURE;
        assertAll(() -> assertView(v, state, statusKey, null, actions(buttons)),
                () -> assertEquals(code, v.errorCode()),
                () -> assertEquals(attemptShown ? 1 : 0, v.attempt()),
                () -> assertEquals(attemptShown ? 3 : 0, v.maxAttempts()),
                () -> assertEquals(state == UiState.LIVENESS_FAILURE ? OverlayModel.GUIDANCE_LIST
                        : new ArrayList<String>(), v.guidanceKeys()),
                () -> assertEquals(state == UiState.DEVICE_ERROR ? Level.DEVICE_ERROR : Level.FAILURE, v.level()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MaxRetriesAction.class)
    @DisplayName("S12: when no attempt is left, or the limit was already reached (LIV-011), the max-retries message"
            + " shows the actions on.max.retries allows")
    void maxRetries(MaxRetriesAction onMax) {
        model.failed(LivenessErrorCode.CHALLENGE_FAILED, 3, 3, false, onMax);
        OverlayView lastAttempt = last();
        model.failed(LivenessErrorCode.MAX_RETRIES_EXCEEDED, 3, 3, false, onMax);
        OverlayView limit = last();

        EnumSet<Action> expected = onMax == MaxRetriesAction.BLOCK ? EnumSet.of(Action.CLOSE)
                : onMax == MaxRetriesAction.EXCEPTION_FLOW ? EnumSet.of(Action.MARK_EXCEPTION, Action.CLOSE)
                        : EnumSet.of(Action.REQUEST_OVERRIDE, Action.CLOSE);
        assertAll(() -> assertView(lastAttempt, UiState.MAX_RETRIES, "liveness.fail.max_retries", null,
                        expected.toArray(new Action[0])),
                () -> assertEquals("LIV-009", lastAttempt.errorCode(), "the failure that used the last attempt"),
                () -> assertView(limit, UiState.MAX_RETRIES, "liveness.fail.max_retries", null,
                        expected.toArray(new Action[0])));
    }

    @Test
    @DisplayName("A cancel goes back to idle, and every change reaches the listener as a new view")
    void cancelAndListener() {
        model.searching();
        model.state(SessionState.PASSIVE_CHECK, new UserPrompt("liveness.status.checking", PromptLevel.INFO));
        model.cancelled();

        assertAll(() -> assertEquals(3, views.size()), () -> assertEquals(UiState.IDLE, last().state()),
                () -> assertTrue(last().buttons().isEmpty()));
    }

    private OverlayView last() {
        return views.get(views.size() - 1);
    }

    private static Action[] actions(String names) {
        List<Action> out = new ArrayList<Action>();
        for (String n : names.trim().split(" ")) {
            out.add(Action.valueOf(n));
        }
        return out.toArray(new Action[0]);
    }

    private static void assertView(OverlayView v, UiState state, String statusKey, String promptKey,
            Action... buttons) {
        assertAll(() -> assertEquals(state, v.state()), () -> assertEquals(statusKey, v.statusKey()),
                () -> assertEquals(promptKey, v.promptKey()),
                () -> assertEquals(buttons.length == 0 ? EnumSet.noneOf(Action.class)
                        : EnumSet.copyOf(Arrays.asList(buttons)), v.buttons()));
    }
}
