package io.mosip.registration.liveness.engine.onnx;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HeadPoseTest {

    private static final File LOCAL = new File(System.getProperty("liveness.localFaces", "../../Mossip-local/faces/retake"));
    private static YuNetDetector detector;
    private static FaceMeshLandmarker landmarker;
    private static FaceMesh mosip;

    @BeforeAll
    static void load() throws Exception {
        detector = YuNetDetector.load(new File("../models/face_detection_yunet_2023mar.onnx").toPath());
        landmarker = FaceMeshLandmarker.load(new File("../models/facemesh_v2_256.onnx").toPath());
        mosip = mesh(YuNetDetectorTest.frame(YuNetDetectorTest.face()));
    }

    @AfterAll
    static void close() {
        detector.close();
        landmarker.close();
    }

    @ParameterizedTest(name = "yaw {0}, pitch {1}, roll {2}")
    @CsvSource({"20,0,0", "-20,0,0", "0,20,0", "0,-20,0", "0,0,20", "0,0,-20", "15,-10,5"})
    @DisplayName("Rotating a real face mesh by known angles changes the pose by those angles")
    void recoversKnownRotations(float yaw, float pitch, float roll) {
        HeadPose before = HeadPose.of(mosip);
        HeadPose after = HeadPose.of(rotate(mosip, yaw, pitch, roll));

        // 3 degrees: the MOSIP face is a few degrees off neutral, and rotating a tilted head about the camera's
        // axes couples the angles a little (20 deg of yaw at ~6 deg baseline pitch moves roll by ~2 deg).
        assertAll(() -> assertNear(yaw, after.yawDeg() - before.yawDeg(), 3f, "yaw"),
                () -> assertNear(pitch, after.pitchDeg() - before.pitchDeg(), 3f, "pitch"),
                () -> assertNear(roll, after.rollDeg() - before.rollDeg(), 3f, "roll"));
    }

    @Test
    @DisplayName("MOSIP's frontal face is close to neutral")
    void frontalFaceIsNearNeutral() {
        HeadPose pose = HeadPose.of(mosip);

        assertAll(() -> assertTrue(Math.abs(pose.yawDeg()) < 10, "yaw " + pose.yawDeg()),
                () -> assertTrue(Math.abs(pose.pitchDeg()) < 15, "pitch " + pose.pitchDeg()),
                () -> assertTrue(Math.abs(pose.rollDeg()) < 10, "roll " + pose.rollDeg()));
    }

    @Test
    @Tag("local-faces")
    @DisplayName("On a team member's shots: own left is positive yaw, own right negative, looking up positive pitch")
    void signsOnRealTurns() throws IOException {
        assumeTrue(new File(LOCAL, "straight.jpg").isFile(), "local face shots not present: " + LOCAL);
        HeadPose straight = HeadPose.of(mesh(local("straight")));
        HeadPose left = HeadPose.of(mesh(local("turn_left")));
        HeadPose right = HeadPose.of(mesh(local("turn_right")));
        HeadPose up = HeadPose.of(mesh(local("look_up")));

        assertAll(() -> assertTrue(Math.abs(straight.yawDeg()) < 10, "straight yaw " + straight.yawDeg()),
                () -> assertTrue(left.yawDeg() > 20, "left yaw " + left.yawDeg()),
                () -> assertTrue(right.yawDeg() < -20, "right yaw " + right.yawDeg()),
                () -> assertTrue(up.pitchDeg() > straight.pitchDeg() + 20,
                        "up pitch " + up.pitchDeg() + " vs straight " + straight.pitchDeg()));
    }

    private static FaceFrame local(String pose) throws IOException {
        return YuNetDetectorTest.frame(ImageIO.read(new File(LOCAL, pose + ".jpg")));
    }

    private static FaceMesh mesh(FaceFrame frame) {
        return landmarker.landmarks(frame, detector.detect(frame).get(0));
    }

    /**
     * Rotates the mesh about its centroid: yaw about the vertical axis (positive moves the nose, which is nearer the
     * camera, to image right, as when the person turns to their own left), then pitch about the horizontal axis
     * (positive moves the nose up), then roll in the image plane.
     */
    private static FaceMesh rotate(FaceMesh m, float yawDeg, float pitchDeg, float rollDeg) {
        double cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < m.size(); i++) {
            cx += m.x(i);
            cy += m.y(i);
            cz += m.z(i);
        }
        cx /= m.size();
        cy /= m.size();
        cz /= m.size();
        double a = Math.toRadians(yawDeg);
        double b = Math.toRadians(pitchDeg);
        double c = Math.toRadians(rollDeg);
        float[] xyz = new float[m.size() * 3];
        for (int i = 0; i < m.size(); i++) {
            double x = m.x(i) - cx;
            double y = m.y(i) - cy;
            double z = m.z(i) - cz;
            double x1 = x * Math.cos(a) - z * Math.sin(a);
            double z1 = x * Math.sin(a) + z * Math.cos(a);
            double y2 = y * Math.cos(b) + z1 * Math.sin(b);
            double z2 = -y * Math.sin(b) + z1 * Math.cos(b);
            double x3 = x1 * Math.cos(c) - y2 * Math.sin(c);
            double y3 = x1 * Math.sin(c) + y2 * Math.cos(c);
            xyz[i * 3] = (float) (x3 + cx);
            xyz[i * 3 + 1] = (float) (y3 + cy);
            xyz[i * 3 + 2] = (float) (z2 + cz);
        }
        return new FaceMesh(xyz, m.presence());
    }

    private static void assertNear(float expected, float actual, float tolerance, String what) {
        assertTrue(Math.abs(expected - actual) <= tolerance, what + ": expected " + expected + " but was " + actual);
    }
}
