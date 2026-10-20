package io.mosip.registration.liveness.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractValueTypesTest {

    @Test
    @DisplayName("PadScore keeps a 0..1 live probability and rejects values outside it")
    void padScoreValidatesProbability() {
        Map<String, Double> cues = new HashMap<String, Double>();
        cues.put("minifasnet_v2", 0.93);
        PadScore score = new PadScore(0.93, false, cues);
        cues.put("tampered", 1.0);

        assertAll(
                () -> assertEquals(0.93, score.liveProbability(), 1e-9),
                () -> assertTrue(!score.veto()),
                () -> assertEquals(Collections.singletonMap("minifasnet_v2", 0.93), score.cues()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PadScore(1.2, false, Collections.<String, Double>emptyMap())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PadScore(-0.1, false, Collections.<String, Double>emptyMap())));
    }

    @Test
    @DisplayName("BindingResult keeps a 0..1 similarity and rejects values outside it")
    void bindingResultValidatesSimilarity() {
        BindingResult bound = new BindingResult(true, 0.82, "", "9f2c");

        assertAll(
                () -> assertTrue(bound.bound()),
                () -> assertEquals(0.82, bound.similarity(), 1e-9),
                () -> assertEquals("9f2c", bound.evidenceDigest()),
                () -> assertThrows(IllegalArgumentException.class, () -> new BindingResult(true, 1.5, "", "")));
    }

    @Test
    @DisplayName("LivenessException carries its error code and detail")
    void livenessExceptionCarriesCode() {
        LivenessException e = new LivenessException(LivenessErrorCode.ENGINE_ERROR, "model hash mismatch");

        assertAll(
                () -> assertSame(LivenessErrorCode.ENGINE_ERROR, e.errorCode()),
                () -> assertTrue(e.getMessage().contains("LIV-013")),
                () -> assertTrue(e.getMessage().contains("model hash mismatch")),
                () -> assertThrows(IllegalArgumentException.class, () -> new LivenessException(null, "x")));
    }
}
