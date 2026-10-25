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
     * @param threads  intra-op threads; 0 for ONNX Runtime's default
     * @return options for one model's session
     * @throws OrtException if ONNX Runtime refuses the options
     */
    public static OrtSession.SessionOptions options(String provider, int threads) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        if (threads > 0) {
            options.setIntraOpNumThreads(threads);
        }
        if ("XNNPACK".equals(provider)) {
            options.addXnnpack(threads > 0
                    ? Collections.singletonMap("intra_op_num_threads", String.valueOf(threads))
                    : Collections.<String, String>emptyMap());
        } else if ("NNAPI".equals(provider)) {
            options.addNnapi();
        }
        return options;
    }
}
