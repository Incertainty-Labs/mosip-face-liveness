package io.mosip.registration.liveness.workbench;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.LivenessMode;
import io.mosip.registration.liveness.api.LivenessPolicy;
import io.mosip.registration.liveness.api.Workflow;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkbenchConfigTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("The bundled demo config turns liveness on for every workflow, without warnings")
    void demo() throws Exception {
        WorkbenchConfig config = WorkbenchConfig.demo();

        for (Workflow w : Workflow.values()) {
            LivenessPolicy policy = config.policy(w, null);
            assertAll(() -> assertTrue(policy.enabled(), w.name()),
                    () -> assertTrue(policy.warnings().isEmpty(), w + " " + policy.warnings()));
        }
    }

    @Test
    @DisplayName("The mode override applies to the chosen workflow only, the exception photo included; no override"
            + " leaves the configured mode")
    void modeOverride() throws Exception {
        WorkbenchConfig config = WorkbenchConfig.demo();

        assertAll(() -> assertEquals(LivenessMode.ACTIVE,
                        config.policy(Workflow.RESIDENT_EXCEPTION_PHOTO, LivenessMode.ACTIVE).mode()),
                () -> assertEquals(LivenessMode.PASSIVE, config.policy(Workflow.RESIDENT_EXCEPTION_PHOTO, null).mode()),
                () -> assertEquals(LivenessMode.HYBRID, config.policy(Workflow.RESIDENT_REGISTRATION, null).mode()),
                () -> assertEquals(LivenessMode.ACTIVE,
                        config.policy(Workflow.RESIDENT_REGISTRATION, LivenessMode.ACTIVE).mode()));
    }

    @Test
    @DisplayName("A properties file is read with the full key names; a value out of range is a warning, not a"
            + " silent default")
    void file() throws Exception {
        Path file = dir.resolve("liveness.properties");
        Files.write(file, Arrays.asList("mosip.registration.face.liveness.enabled=true",
                "mosip.registration.face.liveness.passive.threshold=0.9",
                "mosip.registration.face.liveness.max.retries=99"), StandardCharsets.UTF_8);

        LivenessPolicy policy = WorkbenchConfig.load(file).policy(Workflow.RESIDENT_REGISTRATION, null);

        assertAll(() -> assertEquals(0.9, policy.passiveThreshold(), 1e-9),
                () -> assertFalse(policy.warnings().isEmpty(), "max.retries 99 is out of range"),
                () -> assertTrue(WorkbenchConfig.load(file).source().endsWith("liveness.properties")));
    }
}
