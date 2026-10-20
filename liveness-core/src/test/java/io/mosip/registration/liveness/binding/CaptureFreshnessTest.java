package io.mosip.registration.liveness.binding;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.LivenessErrorCode;
import java.security.SecureRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CaptureFreshnessTest {

    private static final String NONCE = "3f9c2a7e5b1d4c8f9a0b6e2d7c1f4a3b";
    private static final long PASSED_AT = 1_761_000_000_000L;
    private static final long WINDOW = 60_000L;

    @Test
    @DisplayName("A capture that echoes the nonce and is signed within the window is fresh")
    void echoedNonceWithinWindowIsFresh() {
        CaptureFreshness.Check check = CaptureFreshness.verify(NONCE, NONCE, PASSED_AT + 2_000L, PASSED_AT, WINDOW);

        assertAll(
                () -> assertTrue(check.fresh()),
                () -> assertNull(check.errorCode()),
                () -> assertEquals("", check.reason()));
    }

    @Test
    @DisplayName("The echoed transaction id is compared without regard to case, as the Desktop client already does")
    void echoComparisonIgnoresCase() {
        assertTrue(CaptureFreshness.verify(NONCE, NONCE.toUpperCase(), PASSED_AT, PASSED_AT, WINDOW).fresh());
    }

    @ParameterizedTest(name = "signed {0} ms from the reference is {1}")
    @CsvSource({"60000, true", "-60000, true", "60001, false", "-60001, false"})
    @DisplayName("The signed timestamp must be within the window on either side, to absorb clock skew")
    void windowIsInclusiveOnBothSides(long offsetMs, boolean fresh) {
        assertEquals(fresh, CaptureFreshness.verify(NONCE, NONCE, PASSED_AT + offsetMs, PASSED_AT, WINDOW).fresh());
    }

    @ParameterizedTest(name = "transaction id \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"a1b2c3d4-0000-4000-8000-000000000000", "3f9c2a7e5b1d4c8f9a0b6e2d7c1f4a3c"})
    @DisplayName("A capture that does not echo the session nonce fails as a binding failure")
    void wrongOrMissingEchoFails(String signedTransactionId) {
        CaptureFreshness.Check check = CaptureFreshness.verify(NONCE, signedTransactionId, PASSED_AT, PASSED_AT, WINDOW);

        assertAll(
                () -> assertFalse(check.fresh()),
                () -> assertEquals(LivenessErrorCode.CAPTURE_BINDING_FAILED, check.errorCode()),
                () -> assertTrue(check.reason().contains("transactionId")));
    }

    @Test
    @DisplayName("A stale capture fails as a binding failure and says why")
    void staleCaptureFails() {
        CaptureFreshness.Check check = CaptureFreshness.verify(NONCE, NONCE, PASSED_AT + 120_000L, PASSED_AT, WINDOW);

        assertAll(
                () -> assertFalse(check.fresh()),
                () -> assertEquals(LivenessErrorCode.CAPTURE_BINDING_FAILED, check.errorCode()),
                () -> assertTrue(check.reason().contains("timestamp")));
    }

    @Test
    @DisplayName("A missing nonce or a non-positive window is a programming error")
    void invalidArgumentsAreRejected() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureFreshness.verify(null, NONCE, PASSED_AT, PASSED_AT, WINDOW)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CaptureFreshness.verify(NONCE, NONCE, PASSED_AT, PASSED_AT, 0L)));
    }

    @Test
    @DisplayName("newNonce() gives 32 lowercase hex characters (a valid SBI transactionId), different every time")
    void newNonceIsValidTransactionId() {
        SecureRandom random = new SecureRandom();
        String first = CaptureFreshness.newNonce(random);

        assertAll(
                () -> assertTrue(first.matches("[0-9a-f]{32}"), first),
                () -> assertNotEquals(first, CaptureFreshness.newNonce(random)));
    }
}
