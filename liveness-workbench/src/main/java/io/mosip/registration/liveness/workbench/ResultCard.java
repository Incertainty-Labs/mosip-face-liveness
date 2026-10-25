package io.mosip.registration.liveness.workbench;

import io.mosip.registration.liveness.api.LivenessResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What one attempt ended with, for the Workbench's result card: the engine's result, its diagnostics, and what the
 * capture check found. Never shown to a resident; the Workbench is a demo and test tool. Immutable.
 */
public final class ResultCard {

    private final String workflow;
    private final LivenessResult result;
    private final CaptureCheck.Outcome capture;

    ResultCard(String workflow, LivenessResult result, CaptureCheck.Outcome capture) {
        this.workflow = workflow;
        this.result = result;
        this.capture = capture;
    }

    public String workflow() {
        return workflow;
    }

    /** @return PASSED, FAILED, CANCELLED or ERROR */
    public String finalState() {
        return result.finalState().name();
    }

    /** @return the engine's result */
    public LivenessResult result() {
        return result;
    }

    /** @return the capture check's outcome; null when no capture followed */
    public CaptureCheck.Outcome capture() {
        return capture;
    }

    /** @return whether a capture followed and was bound */
    public boolean captureBound() {
        return capture != null && capture.bound();
    }

    /** @return the evidence digest the packet would carry: the capture's when one was bound, else the session's */
    public String evidenceDigest() {
        return capture != null && capture.bound() ? capture.evidenceDigest() : result.evidenceDigest();
    }

    /** @return the card's lines, label to value, in display order */
    public Map<String, String> lines() {
        Map<String, String> lines = new LinkedHashMap<String, String>();
        lines.put("Workflow", workflow);
        lines.put("Result", result.finalState() + " / " + result.decision()
                + (result.errorCode() == null ? "" : " / " + result.errorCode().code()));
        lines.put("Passive score", String.format("%.3f", result.passiveScore()));
        for (Map.Entry<String, String> d : result.diagnostics().entrySet()) {
            lines.put(d.getKey(), d.getValue());
        }
        lines.put("Challenges", !result.activeUsed() ? "none drawn"
                : result.challenges().isEmpty() ? "drawn, none finished" : result.challengeSummary());
        lines.put("Frames", result.framesAnalysed() + " in " + result.durationMs() + " ms ("
                + String.format("%.1f", result.measuredFps()) + " fps)");
        if (capture != null) {
            lines.put("Capture signature", capture.signatureValid() ? "valid (device trust not checked here)"
                    : "does not verify");
            lines.put("Capture fresh", capture.fresh() ? "yes: nonce echoed, signed in time" : "no");
            lines.put("Scene binding", capture.binding() == null ? "off or not reached"
                    : String.format("%.3f", capture.binding().similarity()) + (capture.binding().bound() ? " bound"
                            : " not bound"));
            lines.put("Capture", capture.bound() ? "bound" : "refused: " + capture.reason());
        }
        lines.put("Evidence digest", evidenceDigest() == null ? "none" : evidenceDigest());
        lines.put("Engine", result.engineVersion() + " " + result.modelVersions());
        return Collections.unmodifiableMap(lines);
    }
}
