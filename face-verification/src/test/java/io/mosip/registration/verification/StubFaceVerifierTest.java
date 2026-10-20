package io.mosip.registration.verification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.verification.VerificationResult.Outcome;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class StubFaceVerifierTest {

    private static final FaceFrame PROBE = new FaceFrame(2, 2, new byte[12], 0L, 0L, "test:probe");
    private static final FaceFrame REFERENCE = new FaceFrame(2, 2, new byte[12], 0L, 0L, "test:reference");

    @Test
    @DisplayName("By default the stub reports a match above the default threshold 0.363, as model 'stub'")
    void defaultIsMatchAboveThreshold() {
        VerificationResult result = FaceVerifiers.stub(Collections.<String, String>emptyMap()).verify(PROBE, REFERENCE);

        assertAll(
                () -> assertEquals(Outcome.MATCH, result.outcome()),
                () -> assertTrue(result.matched()),
                () -> assertEquals(0.363, result.threshold(), 1e-9),
                () -> assertTrue(result.similarity() >= result.threshold()),
                () -> assertEquals("stub", result.modelVersion()));
    }

    @ParameterizedTest(name = "stub.force={0}")
    @EnumSource(Outcome.class)
    @DisplayName("stub.force selects the outcome, naming which image failed, with similarity on the right side")
    void forcedOutcomeIsReported(Outcome outcome) {
        VerificationResult result = FaceVerifiers.stub(force(outcome.name())).verify(PROBE, REFERENCE);

        assertAll(
                () -> assertEquals(outcome, result.outcome()),
                () -> assertEquals(outcome == Outcome.MATCH, result.similarity() >= result.threshold()));
    }

    @Test
    @DisplayName("An unknown stub.force falls back to MATCH")
    void unknownForceMatches() {
        assertEquals(Outcome.MATCH, FaceVerifiers.stub(force("SOMETHING")).verify(PROBE, REFERENCE).outcome());
    }

    @Test
    @DisplayName("Against several references, MATCH if any matches; an empty list is an unusable reference")
    void listOverloadReturnsBestOrUnusable() {
        FaceVerifier verifier = FaceVerifiers.stub(Collections.<String, String>emptyMap());

        assertAll(
                () -> assertEquals(Outcome.MATCH, verifier.verify(PROBE, Arrays.asList(REFERENCE, REFERENCE)).outcome()),
                () -> assertEquals(Outcome.REFERENCE_UNUSABLE,
                        verifier.verify(PROBE, Collections.<FaceFrame>emptyList()).outcome()));
    }

    @ParameterizedTest(name = "cosine {0} gives similarity {1}")
    @CsvSource({"-0.4, 0.0", "0.0, 0.0", "0.363, 0.363", "1.0, 1.0"})
    @DisplayName("Cosine similarity (-1..1) is reported clamped to 0..1")
    void cosineIsClampedToZeroOne(double cosine, double similarity) {
        assertEquals(similarity, VerificationResult.similarityFromCosine(cosine), 1e-9);
    }

    @Test
    @DisplayName("A result needs an outcome, values within 0..1 and a model version")
    void resultValidatesItsValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new VerificationResult(null, 0.5, 0.363, "m")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new VerificationResult(Outcome.MATCH, 1.2, 0.363, "m")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new VerificationResult(Outcome.MATCH, 0.5, -0.1, "m")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new VerificationResult(Outcome.MATCH, 0.5, 0.363, null)));
    }

    private static Map<String, String> force(String value) {
        Map<String, String> config = new HashMap<String, String>();
        config.put("mosip.registration.face.verification.stub.force", value);
        return config;
    }
}
