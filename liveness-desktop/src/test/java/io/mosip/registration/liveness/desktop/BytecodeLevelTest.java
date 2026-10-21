package io.mosip.registration.liveness.desktop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the module's bytecode level. An editor that builds into the same {@code target/classes} at a newer Java
 * level leaves newer classes there, and an incremental Maven build packs them; this test fails the build instead.
 */
class BytecodeLevelTest {

    /** Java 11: the level in this module's {@code maven.compiler.release}. */
    private static final int EXPECTED_MAJOR = 55;
    private static final String ANCHOR = "io/mosip/registration/liveness/desktop/JpegFrames.class";

    @Test
    @DisplayName("Every compiled class of this module is Java 11 bytecode")
    void everyClassHasTheReleaseLevel() throws IOException, URISyntaxException {
        URL anchor = getClass().getClassLoader().getResource(ANCHOR);
        assertTrue(anchor != null && "file".equals(anchor.getProtocol()), "main classes not found as files: " + anchor);
        File anchorFile = new File(anchor.toURI());
        File root = anchorFile;
        for (int i = 0; i < ANCHOR.split("/").length; i++) {
            root = root.getParentFile();
        }
        List<String> wrong = new ArrayList<String>();
        collect(root, root, wrong);

        assertTrue(wrong.isEmpty(), "Classes not at major version " + EXPECTED_MAJOR + " (an editor may have compiled"
                + " into target/classes; run mvn clean install): " + wrong);
    }

    private static void collect(File root, File dir, List<String> wrong) throws IOException {
        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collect(root, entry, wrong);
            } else if (entry.getName().endsWith(".class")) {
                int major = majorVersion(entry);
                if (major != EXPECTED_MAJOR) {
                    wrong.add(root.toURI().relativize(entry.toURI()) + " (" + major + ")");
                }
            }
        }
    }

    private static int majorVersion(File classFile) throws IOException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(classFile))) {
            in.readInt();
            in.readUnsignedShort();
            return in.readUnsignedShort();
        }
    }
}
