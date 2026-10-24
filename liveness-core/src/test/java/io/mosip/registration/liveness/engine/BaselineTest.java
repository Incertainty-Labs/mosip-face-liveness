package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.Rect;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaselineTest {

    @Test
    @DisplayName("The baseline is each measure's median over the frames with one face; unmeasured values are skipped")
    void medianOfEachMeasure() {
        Baseline b = Baseline.of(Arrays.asList(
                face(-4, 11, 3, 0.00f, 0.00f, 0.80f, 0.82f),
                face(-3, 12, 4, 0.02f, 0.01f, Float.NaN, Float.NaN),
                face(-5, 10, 2, 0.01f, 0.02f, 0.84f, 0.80f),
                FaceObservation.builder().build(),
                face(30, -20, 9, 0.90f, 0.50f, 0.10f, 0.10f)));

        assertAll(() -> assertEquals(-3.5, b.yawDeg(), 1e-6, "median of -4, -3, -5, 30"),
                () -> assertEquals(10.5, b.pitchDeg(), 1e-6, "median of 11, 12, 10, -20"),
                () -> assertEquals(3.5, b.rollDeg(), 1e-6),
                () -> assertEquals(0.015, b.smile(), 1e-6),
                () -> assertEquals(0.015, b.mouthOpen(), 1e-6),
                () -> assertEquals(0.80, b.eyeOpenLeft(), 1e-6, "median of 0.80, 0.84, 0.10; the NaN is skipped"),
                () -> assertEquals(0.80, b.eyeOpenRight(), 1e-6));
    }

    @Test
    @DisplayName("Without a frame with one face, every measure is unknown (NaN)")
    void noFaceGivesNaN() {
        Baseline b = Baseline.of(Collections.singletonList(FaceObservation.builder().build()));

        assertAll(() -> assertTrue(Double.isNaN(b.yawDeg())), () -> assertTrue(Double.isNaN(b.smile())),
                () -> assertTrue(Double.isNaN(b.eyeOpenLeft())));
    }

    private static FaceObservation face(float yaw, float pitch, float roll, float smile, float mouth, float eyeLeft,
            float eyeRight) {
        return FaceObservation.builder().faces(1, new Rect(10, 10, 100, 120)).pose(yaw, pitch, roll)
                .expression(smile, mouth).eyesOpen(eyeLeft, eyeRight).build();
    }
}
