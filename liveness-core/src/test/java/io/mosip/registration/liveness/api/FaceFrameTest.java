package io.mosip.registration.liveness.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FaceFrameTest {

    @Test
    @DisplayName("A valid frame keeps its size, timestamp, sequence and source")
    void validFrameKeepsItsValues() {
        FaceFrame frame = new FaceFrame(4, 2, new byte[4 * 2 * 3], 1500L, 7L, "webcam:0");

        assertAll(
                () -> assertEquals(4, frame.width()),
                () -> assertEquals(2, frame.height()),
                () -> assertEquals(24, frame.rgb().length),
                () -> assertEquals(1500L, frame.timestampMs()),
                () -> assertEquals(7L, frame.sequence()),
                () -> assertEquals("webcam:0", frame.sourceId()));
    }

    @ParameterizedTest(name = "{0}x{1} with {2} bytes is rejected")
    @CsvSource({"0, 2, 0", "4, 0, 0", "-1, 2, 6", "4, 2, 23", "4, 2, 25"})
    @DisplayName("A non-positive size or an RGB buffer of the wrong length is rejected")
    void invalidSizeOrBufferIsRejected(int width, int height, int bytes) {
        assertThrows(IllegalArgumentException.class,
                () -> new FaceFrame(width, height, new byte[bytes], 0L, 0L, "file:x"));
    }

    @Test
    @DisplayName("A missing buffer or source id is rejected")
    void missingBufferOrSourceIsRejected() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new FaceFrame(1, 1, null, 0L, 0L, "file:x")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new FaceFrame(1, 1, new byte[3], 0L, 0L, null)));
    }
}
