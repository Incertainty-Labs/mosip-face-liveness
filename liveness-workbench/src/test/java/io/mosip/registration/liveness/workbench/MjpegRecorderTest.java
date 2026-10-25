package io.mosip.registration.liveness.workbench;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FrameListener;
import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.eval.VideoFileFrameSource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MjpegRecorderTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("A recording plays back through the clip reader frame for frame, at the same size and close in"
            + " pixels, so MockMDS clip profiles and the evaluation can use it")
    void playsBack() throws Exception {
        FaceFrame face;
        try (InputStream in = MjpegRecorderTest.class.getResourceAsStream("/faces/mosip-mockmds-face.iso")) {
            face = IsoFace.decode(in.readAllBytes());
        }
        Path clip = dir.resolve("bonafide.mjpeg");

        try (MjpegRecorder recorder = new MjpegRecorder(clip)) {
            for (int i = 0; i < 3; i++) {
                recorder.write(face);
            }
        }
        List<FaceFrame> played = new ArrayList<FaceFrame>();
        new VideoFileFrameSource(clip, 20, false).start(new FrameListener() {
            @Override
            public void onFrame(FaceFrame frame) {
                played.add(frame);
            }

            @Override
            public void onError(LivenessErrorCode code, String detail) {
                throw new AssertionError(code + " " + detail);
            }

            @Override
            public void onEnd() {
            }
        });

        assertAll(() -> assertEquals(3, played.size()), () -> assertEquals(face.width(), played.get(0).width()),
                () -> assertEquals(face.height(), played.get(0).height()),
                () -> assertTrue(meanDifference(face, played.get(2)) < 4.0,
                        "mean difference " + meanDifference(face, played.get(2))),
                () -> assertTrue(Files.size(clip) > 0));
    }

    private static double meanDifference(FaceFrame a, FaceFrame b) {
        long sum = 0;
        for (int i = 0; i < a.rgb().length; i++) {
            sum += Math.abs((a.rgb()[i] & 255) - (b.rgb()[i] & 255));
        }
        return (double) sum / a.rgb().length;
    }
}
