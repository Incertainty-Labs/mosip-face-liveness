package io.mosip.registration.liveness.engine.onnx;

import io.mosip.registration.liveness.api.FaceFrame;
import io.mosip.registration.liveness.api.FaceObservation;
import io.mosip.registration.liveness.engine.DetectingAnalyzer;
import java.util.List;

/**
 * The face analyzer for slow devices: YuNet runs on the first frame and then on every Nth, and in between the face is
 * followed with Face Mesh's own landmarks from the previous frame, as MediaPipe tracks. A frame where Face Mesh no
 * longer sees a face (presence below 0.5) runs the detector at once, and so does the frame after one without a face.
 *
 * <p>On a followed frame the box is the mesh's bounding box mapped to a detector-sized box with the offset and scale
 * measured at the last detection, so the quality gate, the guard and the binder see the same box sizes either way.
 * The face count is the last detection's, so a second face is noticed at the next detection. With N = 1 it is the
 * plain {@link OnnxFaceAnalyzer}. One per session; not thread-safe.
 */
public final class TrackingFaceAnalyzer implements DetectingAnalyzer {

    /** Face Mesh presence below which the face counts as lost. */
    static final float MIN_PRESENCE = 0.5f;

    private final OnnxFaceAnalyzer models;
    private final float minScore;
    private final int detectEvery;

    private FaceMesh lastMesh;
    private int lastFaceCount;
    private int sinceDetection;
    private boolean detected;
    /** The detector's box relative to the mesh's bounds at the last detection: x, y offsets and width, height scales. */
    private final float[] relation = new float[4];

    TrackingFaceAnalyzer(OnnxFaceAnalyzer models, float minScore, int detectEvery) {
        this.models = models;
        this.minScore = minScore;
        this.detectEvery = Math.max(1, detectEvery);
    }

    @Override
    public FaceObservation analyze(FaceFrame frame) {
        if (lastMesh != null && sinceDetection < detectEvery) {
            float[] box = toBox(bounds(lastMesh));
            FaceMesh mesh = models.landmarker().landmarks(frame,
                    new FaceDetection(box[0], box[1], box[2], box[3], new float[0], 1f));
            if (mesh.presence() >= MIN_PRESENCE) {
                detected = false;
                sinceDetection++;
                lastMesh = mesh;
                float[] followed = toBox(bounds(mesh));
                return models.observe(frame, mesh, lastFaceCount, followed[0], followed[1], followed[2], followed[3]);
            }
        }
        return detect(frame);
    }

    @Override
    public boolean detected() {
        return detected;
    }

    private FaceObservation detect(FaceFrame frame) {
        detected = true;
        sinceDetection = 1;
        List<FaceDetection> faces = models.detector().detect(frame, minScore);
        if (faces.isEmpty()) {
            lastMesh = null;
            return FaceObservation.builder().build();
        }
        FaceDetection face = OnnxFaceAnalyzer.largest(faces);
        FaceMesh mesh = models.landmarker().landmarks(frame, face);
        float[] b = bounds(mesh);
        float bw = b[2] - b[0];
        float bh = b[3] - b[1];
        relation[0] = (face.x() - b[0]) / bw;
        relation[1] = (face.y() - b[1]) / bh;
        relation[2] = face.width() / bw;
        relation[3] = face.height() / bh;
        lastMesh = mesh;
        lastFaceCount = faces.size();
        return models.observe(frame, mesh, faces.size(), face.x(), face.y(), face.width(), face.height());
    }

    /** @return min x, min y, max x, max y of the mesh */
    private static float[] bounds(FaceMesh mesh) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < mesh.size(); i++) {
            b[0] = Math.min(b[0], mesh.x(i));
            b[1] = Math.min(b[1], mesh.y(i));
            b[2] = Math.max(b[2], mesh.x(i));
            b[3] = Math.max(b[3], mesh.y(i));
        }
        return b;
    }

    /** @return x, y, width, height of the detector-sized box for mesh bounds, by the last detection's relation */
    private float[] toBox(float[] b) {
        float bw = b[2] - b[0];
        float bh = b[3] - b[1];
        return new float[] {b[0] + relation[0] * bw, b[1] + relation[1] * bh, relation[2] * bw, relation[3] * bh};
    }
}
