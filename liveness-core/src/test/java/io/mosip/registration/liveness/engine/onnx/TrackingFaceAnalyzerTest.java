package io.mosip.registration.liveness.engine.onnx;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.api.Rect;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Detecting every Nth frame and following the face with Face Mesh in between (MediaPipe's way), for slow phones.
 * MOSIP's face moves 6 px per frame across a 640x480 grey canvas, as a head drifting in front of a camera.
 */
class TrackingFaceAnalyzerTest {

    private static OnnxFaceAnalyzer analyzer;

    @BeforeAll
    static void load() throws Exception {
        analyzer = OnnxFaceAnalyzer.load(new File("../models").toPath(), 0.7);
    }

    @AfterAll
    static void close() {
        analyzer.close();
    }

    @Test
    @DisplayName("Detecting every 3rd frame: detection on frames 0, 3, 6...; in between the tracked observation stays"
            + " close to a full detection (box IoU 0.93 on average and at least 0.85, yaw within 3 degrees)")
    void tracksBetweenDetections() throws IOException {
        TrackingFaceAnalyzer tracking = analyzer.tracking(3);
        List<Boolean> detected = new ArrayList<Boolean>();
        double iouSum = 0;
        int followed = 0;

        for (int n = 0; n < 12; n++) {
            FaceFrame frame = moving(n);
            FaceObservation tracked = tracking.analyze(frame);
            detected.add(tracking.detected());
            FaceObservation full = analyzer.analyze(frame);
            int frameNo = n;
            if (!tracking.detected()) {
                iouSum += iou(tracked.box(), full.box());
                followed++;
            }
            assertAll(() -> assertEquals(1, tracked.faceCount(), "frame " + frameNo),
                    () -> assertTrue(iou(tracked.box(), full.box()) >= 0.85,
                            "frame " + frameNo + " IoU " + iou(tracked.box(), full.box())),
                    () -> assertEquals(full.yawDeg(), tracked.yawDeg(), 3.0, "frame " + frameNo));
        }
        for (int n = 0; n < 12; n++) {
            assertEquals(n % 3 == 0, detected.get(n), "detection on frame " + n);
        }
        // The detector's own box jitters by a few pixels, so the mean is the fair measure; the raw mesh bounds,
        // without the mapping to a detector-sized box, average 0.90 here
        assertTrue(iouSum / followed >= 0.93, "mean IoU on followed frames " + iouSum / followed);
    }

    @Test
    @DisplayName("After a frame without a face, the next frame runs the detector at once")
    void redetectsAfterTheFaceIsLost() throws IOException {
        TrackingFaceAnalyzer tracking = analyzer.tracking(3);

        tracking.analyze(moving(0));
        FaceObservation gone = tracking.analyze(blank());
        boolean goneDetected = tracking.detected();
        FaceObservation back = tracking.analyze(moving(2));

        boolean backDetected = tracking.detected();
        List<Boolean> after = new ArrayList<Boolean>();
        for (int n = 3; n < 6; n++) {
            tracking.analyze(moving(n));
            after.add(tracking.detected());
        }

        assertAll(() -> assertEquals(0, gone.faceCount()), () -> assertTrue(goneDetected, "a lost face is looked for"),
                () -> assertTrue(backDetected, "and found again by the detector"),
                () -> assertEquals(1, back.faceCount()),
                () -> assertEquals(java.util.Arrays.asList(false, false, true), after,
                        "the count starts again from that detection: followed, followed, detected"));
    }

    @Test
    @DisplayName("A face leaving through the frame's edge is detected again as soon as the followed box would cross"
            + " the edge, so no followed frame reports a face partly outside the frame or one the detector lost")
    void faceLeavingTheFrameIsDetectedAgain() throws IOException {
        TrackingFaceAnalyzer tracking = analyzer.tracking(5);
        int followedFrames = 0;

        for (int n = 0; n < 16; n++) {
            FaceFrame frame = sliding(300 + 25 * n);
            FaceObservation tracked = tracking.analyze(frame);
            if (!tracking.detected()) {
                followedFrames++;
                Rect b = tracked.box();
                int frameNo = n;
                assertAll(() -> assertEquals(analyzer.analyze(frame).faceCount(), tracked.faceCount(),
                        "frame " + frameNo + ": the face count a full detection gives"),
                        () -> assertTrue(b.x() >= 0 && b.y() >= 0 && b.x() + b.width() <= frame.width()
                                && b.y() + b.height() <= frame.height(), "frame " + frameNo + ": box " + b.x() + ","
                                        + b.y() + " " + b.width() + "x" + b.height() + " inside the frame"));
            }
        }
        assertTrue(followedFrames >= 4, "the face was followed between detections: " + followedFrames);
    }

    /** MOSIP's 240x320 face photo at {@code x} on a 640x480 grey canvas, leaving through the right edge as x grows. */
    static FaceFrame sliding(int x) throws IOException {
        BufferedImage canvas = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(new Color(128, 128, 128));
        g.fillRect(0, 0, 640, 480);
        g.drawImage(YuNetDetectorTest.face(), x, 80, null);
        g.dispose();
        return YuNetDetectorTest.frame(canvas);
    }

    /** MOSIP's 240x320 face photo at x = 100 + 6n on a 640x480 grey canvas. */
    static FaceFrame moving(int n) throws IOException {
        BufferedImage canvas = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(new Color(128, 128, 128));
        g.fillRect(0, 0, 640, 480);
        g.drawImage(YuNetDetectorTest.face(), 100 + 6 * n, 80, null);
        g.dispose();
        return YuNetDetectorTest.frame(canvas);
    }

    static FaceFrame blank() {
        BufferedImage canvas = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(new Color(128, 128, 128));
        g.fillRect(0, 0, 640, 480);
        g.dispose();
        return YuNetDetectorTest.frame(canvas);
    }

    static double iou(Rect a, Rect b) {
        double w = Math.min(a.x() + a.width(), b.x() + b.width()) - Math.max(a.x(), b.x());
        double h = Math.min(a.y() + a.height(), b.y() + b.height()) - Math.max(a.y(), b.y());
        double inter = w > 0 && h > 0 ? w * h : 0;
        return inter / ((double) a.width() * a.height() + (double) b.width() * b.height() - inter);
    }
}
