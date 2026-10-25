package io.mosip.registration.liveness.desktop.overlay;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private final Messages messages = Messages.english();

    @Test
    @DisplayName("Every key the engine or the overlay can show has English text: error codes, challenge prompts,"
            + " buttons, the guidance list and the overlay's own status lines")
    void everyKeyHasText() {
        List<String> keys = new ArrayList<String>(Arrays.asList("liveness.status.device_search",
                "liveness.status.position", "liveness.status.checking", "liveness.status.no_action",
                "liveness.progress.step", "liveness.feedback.detected", "liveness.feedback.hold_still",
                "liveness.feedback.success", "liveness.retry.available"));
        for (LivenessErrorCode code : LivenessErrorCode.values()) {
            keys.add(code.messageKey());
        }
        for (ChallengeType type : ChallengeType.values()) {
            keys.add(type.promptKey());
        }
        for (Action action : Action.values()) {
            keys.add(action.messageKey());
        }
        keys.addAll(OverlayModel.GUIDANCE_LIST);

        List<String> missing = new ArrayList<String>();
        for (String key : keys) {
            if (!messages.has(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "no English text for " + missing);
    }

    @Test
    @DisplayName("Arguments fill the text (Step 2 of 3), and a key without text shows as itself so it is noticed")
    void formatsAndShowsMissingKeys() {
        assertAll(() -> assertEquals("Step 2 of 3", messages.text("liveness.progress.step", 2, 3)),
                () -> assertEquals("Checking face liveness…", messages.text("liveness.status.checking")),
                () -> assertEquals("liveness.no.such.key", messages.text("liveness.no.such.key")));
    }
}
