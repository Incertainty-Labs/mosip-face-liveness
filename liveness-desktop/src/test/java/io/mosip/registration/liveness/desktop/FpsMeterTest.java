package io.mosip.registration.liveness.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FpsMeterTest {

    @Test
    @DisplayName("No rate until two frames have arrived")
    void noRateBeforeTwoFrames() {
        FpsMeter meter = new FpsMeter(2_000);
        assertEquals(0.0, meter.fps(), 1e-9);

        meter.frameAt(1_000);
        assertEquals(0.0, meter.fps(), 1e-9);
    }

    @Test
    @DisplayName("Rate is frame intervals over the time they span")
    void rateFromIntervals() {
        FpsMeter meter = new FpsMeter(2_000);
        for (int i = 0; i <= 20; i++) {
            meter.frameAt(1_000 + i * 50L);
        }

        assertEquals(20.0, meter.fps(), 1e-9);
    }

    @Test
    @DisplayName("Only frames within the last two seconds count, so a slowdown shows quickly")
    void oldFramesDropOut() {
        FpsMeter meter = new FpsMeter(2_000);
        for (int i = 0; i <= 40; i++) {
            meter.frameAt(i * 25L); // 40 fps for 1 s
        }
        for (int i = 1; i <= 30; i++) {
            meter.frameAt(1_000 + i * 100L); // then 10 fps for 3 s
        }

        assertEquals(10.0, meter.fps(), 1e-9);
    }
}
