package io.mosip.registration.liveness.engine.onnx;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OnnxFaceAnalyzerTest {

    private static OnnxFaceAnalyzer analyzer;

    @BeforeAll
    static void load() throws Exception {
        analyzer = OnnxFaceAnalyzer.load(new File("../models").toPath(), 0.9);
    }

    @AfterAll
    static void close() {
        analyzer.close();
    }

    @Test
    @DisplayName("The MOSIP face: one face, its box, dense landmarks, near-neutral pose, open eyes, closed mouth, good quality")
    void observesTheMosipFace() throws IOException {
        FaceObservation o = analyzer.analyze(YuNetDetectorTest.frame(YuNetDetectorTest.face()));

        assertAll(() -> assertEquals(1, o.faceCount()),
                () -> assertEquals(58, o.box().x(), 2), () -> assertEquals(77, o.box().y(), 2),
                () -> assertEquals(125, o.box().width(), 2), () -> assertEquals(171, o.box().height(), 2),
                () -> assertEquals(478 * 2, o.landmarks().length),
                () -> assertTrue(Math.abs(o.yawDeg()) < 10, "yaw " + o.yawDeg()),
                () -> assertTrue(o.supportsEyes()),
                () -> assertTrue(o.eyeOpenLeft() > 0.6f && o.eyeOpenRight() > 0.6f,
                        "eyes " + o.eyeOpenLeft() + "/" + o.eyeOpenRight()),
                () -> assertTrue(o.mouthOpen() < 0.2f, "mouthOpen " + o.mouthOpen()),
                () -> assertTrue(o.sharpness() > 0.6f, "sharpness " + o.sharpness()),
                () -> assertTrue(o.brightness() > 0.2f && o.brightness() < 0.8f, "brightness " + o.brightness()));
    }

    @Test
    @DisplayName("Each measure is its part's output: eye openness is 1 - eyeBlink of the same side, smile the mean of"
            + " the two mouthSmile scores, mouth openness jawOpen, pose HeadPose")
    void measuresComeFromTheParts() throws Exception {
        FaceFrame frame = YuNetDetectorTest.frame(YuNetDetectorTest.face());
        File models = new File("../models");
        try (YuNetDetector detector = YuNetDetector.load(new File(models, OnnxFaceAnalyzer.DETECTOR_FILE).toPath());
                FaceMeshLandmarker landmarker = FaceMeshLandmarker.load(
                        new File(models, OnnxFaceAnalyzer.MESH_FILE).toPath());
                BlendshapeScorer scorer = BlendshapeScorer.load(
                        new File(models, OnnxFaceAnalyzer.BLENDSHAPE_FILE).toPath())) {
            FaceMesh mesh = landmarker.landmarks(frame, detector.detect(frame).get(0));
            float[] s = scorer.score(mesh);
            HeadPose pose = HeadPose.of(mesh);

            FaceObservation o = analyzer.analyze(frame);

            assertAll(() -> assertEquals(1f - s[BlendshapeScorer.EYE_BLINK_LEFT], o.eyeOpenLeft(), 1e-6f),
                    () -> assertEquals(1f - s[BlendshapeScorer.EYE_BLINK_RIGHT], o.eyeOpenRight(), 1e-6f),
                    () -> assertEquals((s[BlendshapeScorer.MOUTH_SMILE_LEFT] + s[BlendshapeScorer.MOUTH_SMILE_RIGHT]) / 2f,
                            o.smile(), 1e-6f),
                    () -> assertEquals(s[BlendshapeScorer.JAW_OPEN], o.mouthOpen(), 1e-6f),
                    () -> assertEquals(pose.yawDeg(), o.yawDeg(), 1e-4f),
                    () -> assertEquals(pose.pitchDeg(), o.pitchDeg(), 1e-4f),
                    () -> assertEquals(pose.rollDeg(), o.rollDeg(), 1e-4f),
                    () -> assertEquals(mesh.x(1), o.landmarks()[2], 1e-4f),
                    () -> assertEquals(mesh.y(1), o.landmarks()[3], 1e-4f));
        }
    }

    @Test
    @DisplayName("No face: count 0, no box, no landmarks, every measure unknown (NaN), and no exception")
    void noFaceIsReportedNotThrown() {
        BufferedImage blank = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = blank.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, 320, 240);
        g.dispose();

        FaceObservation o = analyzer.analyze(YuNetDetectorTest.frame(blank));

        assertAll(() -> assertEquals(0, o.faceCount()), () -> assertNull(o.box()),
                () -> assertEquals(0, o.landmarks().length), () -> assertTrue(Float.isNaN(o.yawDeg())),
                () -> assertTrue(!o.supportsEyes()));
    }

    @Test
    @DisplayName("With two faces the count is 2 and the box is the larger face")
    void twoFacesReportTheLarger() throws IOException {
        BufferedImage one = YuNetDetectorTest.face();
        BufferedImage both = new BufferedImage(one.getWidth() * 2, one.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = both.createGraphics();
        g.drawImage(one, 0, 0, null);
        g.drawImage(one, one.getWidth(), one.getHeight() / 4, one.getWidth() * 3 / 4, one.getHeight() * 3 / 4, null);
        g.dispose();

        FaceObservation o = analyzer.analyze(YuNetDetectorTest.frame(both));

        assertAll(() -> assertEquals(2, o.faceCount()),
                () -> assertTrue(o.box().x() < one.getWidth(), "box x " + o.box().x()),
                () -> assertTrue(o.box().width() > 110, "box width " + o.box().width()));
    }
}
