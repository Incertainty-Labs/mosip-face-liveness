package io.mosip.registration.liveness.engine.onnx;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** How the requested threads are split between ONNX Runtime and the accelerator. */
class SessionsTest {

    @Test
    @DisplayName("With XNNPACK the threads go to XNNPACK's pool, and ONNX Runtime's own pool is one thread that does"
            + " not spin, as ONNX Runtime advises, so the two pools do not compete for the cores")
    void xnnpackGetsTheThreads() {
        Sessions.Threads threads = Sessions.threads("XNNPACK", 4, 8);

        assertAll(() -> assertEquals(4, threads.xnnpack),
                () -> assertEquals(1, threads.ort),
                () -> assertFalse(threads.spinning));
    }

    @Test
    @DisplayName("With XNNPACK and no thread count, XNNPACK uses every core, not its own default of one thread")
    void xnnpackDefaultsToEveryCore() {
        Sessions.Threads threads = Sessions.threads("XNNPACK", 0, 8);

        assertAll(() -> assertEquals(8, threads.xnnpack),
                () -> assertEquals(1, threads.ort),
                () -> assertFalse(threads.spinning));
    }

    @Test
    @DisplayName("On CPU and NNAPI the threads are ONNX Runtime's own; no thread count keeps its default")
    void cpuAndNnapiKeepOnnxRuntimeThreads() {
        Sessions.Threads cpu = Sessions.threads("CPU", 4, 8);
        Sessions.Threads nnapi = Sessions.threads("NNAPI", 2, 8);
        Sessions.Threads defaults = Sessions.threads("CPU", 0, 8);

        assertAll(() -> assertEquals(4, cpu.ort),
                () -> assertEquals(0, cpu.xnnpack),
                () -> assertTrue(cpu.spinning),
                () -> assertEquals(2, nnapi.ort),
                () -> assertEquals(0, nnapi.xnnpack),
                () -> assertEquals(0, defaults.ort));
    }
}
