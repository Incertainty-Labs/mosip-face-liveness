package io.mosip.registration.verification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FaceVerificationConfigTest {

    @Test
    @DisplayName("No config gives threshold 0.363 and no warnings")
    void defaults() {
        FaceVerificationConfig config = FaceVerificationConfig.load(Collections.<String, String>emptyMap());

        assertAll(
                () -> assertEquals(0.363, config.threshold(), 1e-9),
                () -> assertTrue(config.warnings().isEmpty()));
    }

    @Test
    @DisplayName("A configured threshold is used, and the stub reports it")
    void configuredThresholdIsUsed() {
        Map<String, String> map = threshold("0.5");

        assertAll(
                () -> assertEquals(0.5, FaceVerificationConfig.load(map).threshold(), 1e-9),
                () -> assertEquals(0.5, FaceVerifiers.stub(map).verify(
                        new io.mosip.registration.liveness.api.FaceFrame(1, 1, new byte[3], 0L, 0L, "t"),
                        new io.mosip.registration.liveness.api.FaceFrame(1, 1, new byte[3], 0L, 0L, "t"))
                        .threshold(), 1e-9));
    }

    @ParameterizedTest(name = "threshold \"{0}\"")
    @ValueSource(strings = {"abc", "-0.1", "1.5"})
    @DisplayName("An invalid threshold falls back to 0.363 with a warning naming the key")
    void invalidThresholdFallsBackWithWarning(String value) {
        FaceVerificationConfig config = FaceVerificationConfig.load(threshold(value));

        assertAll(
                () -> assertEquals(0.363, config.threshold(), 1e-9),
                () -> assertEquals(1, config.warnings().size()),
                () -> assertTrue(config.warnings().get(0).contains("mosip.registration.face.verification.threshold")));
    }

    private static Map<String, String> threshold(String value) {
        Map<String, String> map = new HashMap<String, String>();
        map.put("mosip.registration.face.verification.threshold", value);
        return map;
    }
}
