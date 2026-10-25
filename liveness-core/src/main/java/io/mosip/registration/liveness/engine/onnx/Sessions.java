package io.mosip.registration.liveness.engine.onnx;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtProvider;
import ai.onnxruntime.OrtSession;
import java.util.Collections;
import java.util.EnumSet;

/** ONNX Runtime session options for the engine's models: the execution provider, if this platform has it, and threads. */
public final class Sessions {

    private Sessions() {
    }

    /**
     * @param requested AUTO, CPU, XNNPACK or NNAPI
     * @return the provider that will be used: the requested accelerator if ONNX Runtime has it here, otherwise CPU
     */
    public static String effectiveProvider(String requested) {
        EnumSet<OrtProvider> available = OrtEnvironment.getAvailableProviders();
        if ("XNNPACK".equals(requested) && available.contains(OrtProvider.XNNPACK)) {
            return "XNNPACK";
        }
        if ("NNAPI".equals(requested) && available.contains(OrtProvider.NNAPI)) {
            return "NNAPI";
        }
        return "CPU";
    }

    /**
     * @param provider the provider from {@link #effectiveProvider}
     * @param threads  inference threads; 0 for every core (XNNPACK) or ONNX Runtime's default
     * @return options for one model's session
     * @throws OrtException if ONNX Runtime refuses the options
     */
    public static OrtSession.SessionOptions options(String provider, int threads) throws OrtException {
        Threads plan = threads(provider, threads, Runtime.getRuntime().availableProcessors());
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        if (plan.ort > 0) {
            options.setIntraOpNumThreads(plan.ort);
        }
        if (!plan.spinning) {
            options.addConfigEntry("session.intra_op.allow_spinning", "0");
        }
        if ("XNNPACK".equals(provider)) {
            options.addXnnpack(Collections.singletonMap("intra_op_num_threads", String.valueOf(plan.xnnpack)));
        } else if ("NNAPI".equals(provider)) {
            options.addNnapi();
        }
        return options;
    }

    /**
     * Splits the threads between ONNX Runtime and the accelerator. XNNPACK has its own pool, so ONNX Runtime's is
     * one thread that does not spin, as ONNX Runtime's XNNPACK guide advises; otherwise the two pools compete.
     */
    static Threads threads(String provider, int threads, int cores) {
        if ("XNNPACK".equals(provider)) {
            return new Threads(1, threads > 0 ? threads : cores, false);
        }
        return new Threads(threads, 0, true);
    }

    /** Thread counts for one session; 0 is ONNX Runtime's default for {@code ort} and unused for {@code xnnpack}. */
    static final class Threads {
        final int ort;
        final int xnnpack;
        final boolean spinning;

        Threads(int ort, int xnnpack, boolean spinning) {
            this.ort = ort;
            this.xnnpack = xnnpack;
            this.spinning = spinning;
        }
    }
}
