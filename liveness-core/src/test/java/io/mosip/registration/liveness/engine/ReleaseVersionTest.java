package io.mosip.registration.liveness.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReleaseVersionTest {

    @Test
    @DisplayName("The real engine reports the latest release in the changelog, since every result and the packet"
            + " evidence carry its version")
    void versionIsTheLatestRelease() throws Exception {
        String changelog = new String(Files.readAllBytes(Paths.get("../CHANGELOG.md")), StandardCharsets.UTF_8);
        Matcher release = Pattern.compile("(?m)^## \\[(\\d+\\.\\d+\\.\\d+)\\]").matcher(changelog);

        assertTrue(release.find(), "a released version in CHANGELOG.md");
        assertEquals("liveness-core " + release.group(1), LivenessEngines.HYBRID_VERSION);
    }
}
