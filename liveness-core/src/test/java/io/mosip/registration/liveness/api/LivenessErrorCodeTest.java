package io.mosip.registration.liveness.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LivenessErrorCodeTest {

    @ParameterizedTest(name = "{1} {0}")
    @DisplayName("Each error code matches API.md §6: code, category, message key and attempt rule")
    @CsvSource({
            // enum,                  code,    category, message key,                          counts: normally, in challenge, when terminal
            "DEVICE_UNAVAILABLE,       LIV-001, DEVICE,   liveness.error.device_unavailable,   false, false, false",
            "DEVICE_CONNECTION_FAILED, LIV-002, DEVICE,   liveness.error.device_connection,    false, false, false",
            "CAMERA_UNAVAILABLE,       LIV-003, DEVICE,   liveness.error.camera_unavailable,   false, false, false",
            "FACE_NOT_DETECTED,        LIV-004, GUIDANCE, liveness.guidance.face_not_found,    false, false, false",
            "MULTIPLE_FACES,           LIV-005, GUIDANCE, liveness.guidance.one_face_only,     false, true,  false",
            "POOR_FACE_QUALITY,        LIV-006, GUIDANCE, liveness.guidance.improve_lighting,  false, false, false",
            "BELOW_THRESHOLD,          LIV-007, LIVENESS, liveness.fail.try_again,             false, false, true",
            "PAD_FAILED,               LIV-008, ATTACK,   liveness.fail.generic,               true,  true,  true",
            "CHALLENGE_FAILED,         LIV-009, LIVENESS, liveness.fail.try_again,             true,  true,  true",
            "CHALLENGE_TIMEOUT,        LIV-010, LIVENESS, liveness.fail.timeout,               true,  true,  true",
            "MAX_RETRIES_EXCEEDED,     LIV-011, POLICY,   liveness.fail.max_retries,           false, false, false",
            "INVALID_FRAME_DATA,       LIV-012, DEVICE,   liveness.error.invalid_frame,        false, false, false",
            "ENGINE_ERROR,             LIV-013, SYSTEM,   liveness.error.engine,               false, false, false",
            "CAPTURE_BINDING_FAILED,   LIV-014, ATTACK,   liveness.fail.generic,               true,  true,  true",
            "STATIC_STREAM,            LIV-015, ATTACK,   liveness.fail.generic,               true,  true,  true",
    })
    void errorCodeMatchesSpec(LivenessErrorCode error, String code, ErrorCategory category, String messageKey,
            boolean countsNormally, boolean countsDuringChallenge, boolean countsWhenTerminal) {
        assertAll(
                () -> assertEquals(code, error.code()),
                () -> assertEquals(category, error.category()),
                () -> assertEquals(messageKey, error.messageKey()),
                () -> assertEquals(countsNormally, error.countsAsAttempt(false, false), "normally"),
                () -> assertEquals(countsDuringChallenge, error.countsAsAttempt(true, false), "during a challenge"),
                () -> assertEquals(countsWhenTerminal, error.countsAsAttempt(false, true), "when terminal"));
    }

    @Test
    @DisplayName("There are exactly the 15 codes of API.md §6")
    void exactlyFifteenCodes() {
        assertEquals(15, LivenessErrorCode.values().length);
    }

    @Test
    @DisplayName("A code string maps back to its error, and an unknown one is rejected")
    void fromCodeRoundTrips() {
        assertAll(
                () -> assertSame(LivenessErrorCode.CHALLENGE_TIMEOUT, LivenessErrorCode.fromCode("LIV-010")),
                () -> assertThrows(IllegalArgumentException.class, () -> LivenessErrorCode.fromCode("LIV-099")));
    }
}
