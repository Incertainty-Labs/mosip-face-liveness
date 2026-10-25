# Changelog

All notable changes to this project are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/). While the version is `0.x`, a contract change needs agreement from both maintainers and an entry here.

## [Unreleased]

## [0.2.5] - 2026-10-25

The same code as 0.2.3 and 0.2.4, neither of which JitPack could build. Use 0.2.5.

### Fixed

- JitPack build: 0.2.4's new check that JDK 21 is active stopped the build although JDK 21 was installed. In that step, plain `java` is not the JDK SDKMAN set up, while Maven is. The check is removed; the JDK install from 0.2.4 stays.

## [0.2.4] - 2026-10-25

The same code as 0.2.3, which JitPack could not build. Use 0.2.4.

### Fixed

- JitPack build: SDKMAN stopped serving JDK `21.0.12+1.1-tem`, so the build went on with the image's JDK 8 and failed in `liveness-desktop`. `jitpack.yml` installs `21.0.12-tem`, with Zulu as the fallback, and stops at once if JDK 21 is not active. CI was not affected.

## [0.2.3] - 2026-10-25

What the Desktop Registration Client needs to check liveness before its face capture, and a Workbench fix.

### Added

- `liveness-desktop`: `PushFrameSource`, the frames of a stream someone else reads. Inside the Registration Client, the client's `Streamer` holds the device's only live stream (a second one would keep the device busy) and hands each JPEG to `offer`. Frames are decoded and delivered on the source's own thread. While one is still being analysed, only the newest waits, so the analysis never falls behind the stream. An unreadable frame is LIV-012 and the stream goes on; `streamEnded` reports a dropped stream as LIV-002 and the end.
- `liveness-desktop`: `LivenessGate`, the check the client runs before it asks the device for the face. The client's stream feeds a session and the overlay follows it; only a pass lets the client capture. Attempts are counted for one face field with `AttemptTracker`:
  - a PAD or challenge failure uses an attempt and allows Scan again while attempts are left;
  - the last one shows S12 with the actions `on.max.retries` allows, and a further start shows LIV-011 without starting the stream;
  - a device error (LIV-001, LIV-002) and a cancel use none.

### Fixed

- `liveness-workbench`: a refused Start, with a check already running or no attempt left (LIV-011), no longer opens anything. The window opened the source before `WorkbenchRun` refused, which set the MockMDS profile, started an empty recording and wrote a "Start ... attempt 4 of 3" line. During a running check it also replaced that check's device capturer and recording, leaving the old recording unclosed. `WorkbenchRun.start` now takes an `Opener`, called only once the check may run. A source that fails to open, including a device that answers with something unreadable, is audited, uses no attempt and frees the next Start.

## [0.2.2] - 2026-10-25

Findings from running the real engine on the phone and on the Liveness Workbench with a webcam: the step counter follows its contract, the neutral hold fits real faces, and per-frame diagnostics for a panel. Also the Desktop overlay and the Liveness Workbench.

### Added

- `liveness-core`: `LivenessCallback.onFrameAnalysed(observation, scores)`, a default method (agreed by both maintainers): after each analysed frame, the face observation (no pixels) and each PAD model's live probability on that frame, empty where the PAD models did not run. For a diagnostics panel only; an exception thrown there is ignored, so diagnostics never change a result. Existing callbacks compile and run unchanged. `PassiveAssessor.lastScores()` gives the scores.
- `liveness-desktop`: `MjpegFrameSource.endExpected()`: the device is about to end the stream itself, as MockMDS does when it captures, so the next drop ends the stream quietly instead of reconnecting. A reconnect opened a second stream that kept MockMDS's face device busy until it was restarted.
- `liveness-core`: the real engine fills `LivenessResult.diagnostics()`, which was always empty: `pad.<model>` is each PAD model's median over the passive window, to 3 decimals, and `pad.veto` the cue that vetoed (for example `static_stream`), if one did. For audit and a diagnostics panel only, never for the user.
- `liveness-desktop`: the liveness overlay's rules without a UI toolkit (package `overlay`). `OverlayModel` turns a session's events into an `OverlayView` for each UI state of UI_UX_SPEC section 4: device search, position, guidance, the passive check with its ring, each challenge with Step i of n, its direction cue, time bar and an "Action detected" toast, the neutral hold, capturing and success; liveness, PAD, device and engine failures by error code with the attempt counter and the PS04 guidance list; and the max-retries screen with the actions `on.max.retries` allows. `Messages` gives the English text of every key (API.md section 7, plus `liveness.button.cancel` and `liveness.button.next`). The Workbench and the Desktop client share both.
- `liveness-desktop`: `LivenessOverlay`, the JavaFX view of an `OverlayView` (UI_UX_SPEC section 5, D-1): the preview, mirrored as a selfie view, with a 40% scrim outside the face oval and a progress ring; on a solid panel the step dots, status line, prompt with its direction cue, time bar, 1.5 s feedback toast, guidance list, attempt counter and buttons, in the colours of each state group. It compiles against JavaFX 11, as provided by the Registration Client's own runtime, and ships no JavaFX. Its tests need a display and are skipped on a headless build machine.
- `liveness-workbench` (new module, never part of the Registration Client): the device side of the Liveness Workbench. `SbiClient` makes the SBI 0.9.5 calls to a device service such as MockMDS: discovery, MockMDS's profile switch (`POST /admin/profile`) and the registration capture (`RCAPTURE /capture`, one full face, the session nonce as `transactionId`). It writes requests by hand, because MockMDS reads the body length from `Content-Length:` at a fixed offset. `SignedCapture` reads the signed reply: the ISO image, the echoed `transactionId` and the signed time, and whether the RS256 signature checks against the certificate in the JWT header (device trust stays the client's check). `IsoFace` decodes the ISO 19794-5 face with MOSIP's biometrics-util 1.2.0.2, the Registration Client's own decoder.
- `liveness-workbench`: `CaptureCheck`, what the Desktop client does with the signed capture after a pass, in order: the signature must verify, the device must have signed the session nonce back within `bind.freshness.max.age.ms` of the pass, the ISO image must decode, and with `bind.capture.enabled` it must show the scene the session saw (`GeometricCaptureBinder`); the first that fails decides (LIV-014, or LIV-012 for an undecodable image). `MjpegRecorder` records frames as back-to-back JPEGs that MockMDS clip profiles and the evaluation's clip reader play. `WorkbenchConfig` reads the clients' liveness keys from a properties file (bundled demo: liveness on), resolved by `LivenessConfigLoader` with its warnings, and applies the Workbench's mode override as the workflow's own key. A malformed signature now counts as one that does not verify.
- `liveness-workbench`: `WorkbenchRun`, one check at a time as the Desktop client would run it: the source's frames go to a liveness session and the overlay; a pass is followed by the device capture and the `CaptureCheck` (a recorded clip has no device, so its pass ends without one); attempts are counted per workflow with the clients' `AttemptTracker`, a refused capture uses one (LIV-014), a device failure does not (LIV-002), and a start with none left shows LIV-011 without opening the source. Every step is written to the audit pane, and `ResultCard` holds what the attempt ended with: the result, its PAD diagnostics, the capture's signature, freshness and scene binding, and the final evidence digest.
- `liveness-workbench`: the Workbench window and app. Pick a source (MockMDS's stream with its profile, or a recorded clip), one of the seven workflows and an optional mode override, then Start: the overlay shows the check as the Desktop client would, the result card shows the result, its PAD scores and the capture's checks, and the audit pane (also standard output) every step. Record writes the stream as an MJPEG clip outside git; the overlay's Retry, Cancel and Close work, and the max-retries actions are simulated. `workbench.cmd` builds the modules and starts it on a JDK with JavaFX, with the repository's model bundle.

### Fixed

- `liveness-core`: the real engine broke `LivenessCallback.onChallenge`'s contract, which the stub kept: it passed a 0-based index, counted the final neutral hold in the total, and announced the hold as a challenge, so clients showed "Step 0 of 3" and the hold as a step (found on the Android phone). It now passes the 1-based index among the random challenges and their count, and the hold is announced by its state alone. The Desktop overlay (`OverlayModel`), built against the old behaviour, follows the contract too: the hold keeps the step line and reports the last challenge as done.
- `liveness-workbench`: one check at a time (a second Start while one runs opened a second stream and left the first one open, keeping MockMDS busy); the stream stays open during the capture, since MockMDS refuses a capture once its stream has stopped (error 700), and its end there is expected; every attempt stops its own stream.

### Changed

- `liveness-core`: the neutral hold that ends every challenge sequence allows 10° from the baseline instead of 5°. On the Workbench a real face never passed it after challenges: people settled 4 to 7° from where they started (pitch, mostly after LOOK_UP), and the short passive window can catch a head already moving, which shifts the baseline. Replayed frame by frame, eyes and mouth were well inside their limits; only the pose missed. A head still 12° turned or 15° up, or an open mouth, still fails.

## [0.2.1] - 2026-10-25

The Desktop capture binder, and options for slow devices: the first Android build of the real engine was too slow for the challenges.

### Added

- `liveness-core`: `GeometricCaptureBinder` (package `binding`), the Desktop capture binder (TECHNICAL_DESIGN §8): the signed capture must show the scene the session just saw, from the same camera. The capture is compared with each of the session's last frames and the best pair counts, scored as the mean of face box overlap (coordinates scaled to 0..1, since resolutions may differ), landmark agreement (five points, the capture's scaled to the stream frame's size, relative to the eye distance) and a 64-bit perceptual hash of the face crop; bound at `bind.min.similarity` (0.60). The capture must be the camera's whole frame, at any resolution. Recent frames that cannot be compared (not exactly one face, a landmark that is not a number, a box almost outside the frame) are skipped. Never bound when liveness did not pass, no recent frame can be compared, or the capture does not show exactly one face that can be compared. The evidence digest becomes SHA-256 over the session's digest and the SHA-256 of the capture's decoded pixels. On test faces: the same scene 1.0, drifted 8 px 0.78 (0.78 too when the capture has twice the stream's resolution), another person where the face was 0.29. It proves the scene, not identity.
- `liveness-core`: options for slow devices (`EngineOptions`). The first Android build measured about 830 ms per frame on a mid-range phone (debuggable build), where the challenges cannot work.
  - `detectEvery(n)`: the face detector runs on the first frame, then every Nth, and at once when the face is lost; in between the face is followed with Face Mesh's own landmarks (`TrackingFaceAnalyzer`), as MediaPipe tracks. The followed box is mapped to a detector-sized box (mean IoU 0.95 against full detection on a drifting face). The passive PAD models run only on detection frames, since they were calibrated on detector boxes; the static-stream veto still sees every frame. The face count is the last detection's, so a second face is noticed at the next detection; for that reason a session passes only on a detection frame (the result may come up to n-1 frames after the last challenge), and n is at most 5. A face whose followed box would cross the frame's edge is detected again at once, since Face Mesh keeps following a face the detector has lost. Default 1, so Desktop is unchanged.
  - `withProvider("XNNPACK" | "NNAPI" | "CPU" | "AUTO")`: XNNPACK and NNAPI are used only where ONNX Runtime has them (Android); otherwise CPU, and `EngineInfo` says which. If an accelerator is tried and fails to start, the models that did load are closed and the engine is built on CPU instead of refusing to start: acceleration changes speed only. AUTO stays CPU until phone measurements pick a better default.
  - `threads(n)`: the inference threads; 0 (the default) is every core with XNNPACK and ONNX Runtime's own default otherwise. With XNNPACK the threads go to XNNPACK's pool and ONNX Runtime's own pool is one thread that does not spin, as ONNX Runtime's XNNPACK guide advises, so the two pools do not compete for the cores.
  - Every model class can load with given session options.

### Removed

- `EngineOptions.effectiveProvider()`: which provider runs depends on the platform's ONNX Runtime and on whether it starts, which the options cannot know. The engine reports it in `EngineInfo.executionProvider()`.

## [0.2.0] - 2026-10-24

The real engine: on-device face analysis, passive PAD and random challenges, loaded from a verified model bundle. Defaults for the face detector and the quality gate were set on one team member's real webcam clips; see each entry.

### Added

- `liveness-desktop`: `JpegFrames.decode` turns one JPEG of an SBI live stream into an RGB `FaceFrame` (API.md §4.2), and an MJPEG part reader reads `multipart/x-mixed-replace` streams by `Content-Length`, or up to the JPEG end marker when a device leaves that header out. Checked against MockMDS's exact framing.
- `liveness-desktop`: `MjpegFrameSource` reads an SBI 0.9.5 face live stream straight from the device service (for the Liveness Workbench): LIV-001 when no stream can be opened, LIV-012 for an unreadable frame, one reconnect on a drop and then LIV-002, and the measured frame rate over 2 s in `capabilities()`.
- `liveness-core`: `YuNetDetector` finds faces with YuNet 2023mar (box, five landmarks, score) the way OpenCV's `FaceDetectorYN` does, checked against OpenCV on MOSIP's mock device face photo. Shared by the face analyzer and, later, face verification.
- `liveness-core`: `FaceMeshLandmarker` (MediaPipe Face Mesh V2: 478 3D landmarks from a 1.5x face crop) and `BlendshapeScorer` (MediaPipe Blendshape V2: 52 expression scores, including eye blink, jaw open and smile), both checked against a reference pipeline on MOSIP's mock face. Tests on team members' own pose and expression shots are tagged `local-faces`, read from a folder outside git, and skipped when it is missing.
- `liveness-core`: `HeadPose.of(mesh)` gives yaw, pitch and roll from Face Mesh's 3D landmarks (positive yaw = the person's own left, positive pitch = looking up). Five-point pitch was too weak on real faces, so pose uses the dense mesh.
- New key `mosip.registration.face.liveness.detector.min.score` (default 0.70, range 0.5..0.99, global): the face detector's minimum confidence, adjustable for dim rooms. OpenCV's own default, 0.90, lost the face in most frames of a real head tilt upwards, so LOOK_UP could not pass; at 0.70 no bona fide clip gained a false face. `LivenessPolicy.detectorMinScore()`.
- `liveness-core`: `FrameQuality.of(frame, box)` measures brightness (mean luma) and sharpness (Laplacian variance on the face scaled to 128 px wide, so it barely depends on distance) inside the face box, both 0..1.
- `liveness-core`: `QualityGate.check(observation, width, height, policy)` decides whether a frame is good enough to judge, giving guidance only, never an attack verdict: LIV-004 for no face, LIV-005 for more than one, LIV-006 with `face_not_found` for a face too small or off-centre, and LIV-006 with `improve_lighting` for a dark, bright or blurred face.
- `liveness-core`: `OnnxFaceAnalyzer` is the on-device `FaceAnalyzer`. YuNet finds the faces, and the largest is measured: 478 landmarks, head pose, eye openness (1 - eyeBlink of the same eye, the person's own left and right), smile (mean of the two mouthSmile scores), mouth openness (jawOpen), sharpness and brightness. No face gives a count of 0 with every measure NaN. `OnnxFaceAnalyzer.load(modelDir, policy.detectorMinScore())`.
- `liveness-core`: `MiniFasNetPadDetector` is a passive PAD cue with one Silent-Face-Anti-Spoofing MiniFASNet model (`minifasnet_v2` at 2.7x, `minifasnet_v1se` at 4.0x). Its crop, resize (bit for bit with OpenCV's fixed-point `cv2.resize`), BGR input and softmax match the upstream pipeline, checked against it in Python on MOSIP's face and Minivision's own real and spoof samples (added as test resources with their provenance). It crops around YuNet's box as is: reshaping it towards the upstream detector's box made a real face in dim light fail (0.68).
- A-09 acceptance (`LocalFacesPassiveTest`, tagged `local-faces`, on one team member's own clips kept outside git): with the default policy, each threshold where the engine applies it: five frontal bona fide clips (straight, smiling, eyes closed, mouth open, dim light) pass the passive check (above 0.8); three strong head poses, seen only during challenges (turned left and right, a 40° look-up at 0.73), stay above the challenge veto (0.6); and four attacks (a phone photo at 30 and 50 cm, a phone video, a monitor) stay below 0.5. A small, single-person check: print attacks and backlit faces are not covered yet.
- `liveness-core`: `StaticStreamDetector` (`static_stream`) is a hard veto for a frozen stream (LIV-015): a still image or a paused video injected as the camera. The whole frame, shrunk to 64x48 grey, must differ from the previous frame by less than 0.15 grey levels on average, for at least 1 s and 5 frames; a live webcam measured 0.53 to 2.8. Frames without a face start the count again, so a covered lens gets guidance, not an attack verdict. MockMDS's `static` profile is vetoed after 1009 ms.
- `liveness-core`: `PassiveAssessor` makes a session's passive PAD decision (TECHNICAL_DESIGN §5.3): each frame's score is the weighted mean of the cues, and the session score is the median over the last `passive.min.frames` frames (NaN until then). Frames the quality gate rejects are skipped without restarting the window, weights are renormalised over the cues actually loaded, a cue without a weight only vetoes (it sees every frame, and its veto stays), and `cues()` gives each model's own median for diagnostics and audit.
- `liveness-core`: `RandomChallengeGenerator` draws a session's challenges (TECHNICAL_DESIGN §7.1): `challenge.min.count` to `challenge.max.count` from `challenge.pool`, never the same type twice in a row, then NEUTRAL_HOLD, each with `challenge.timeout.ms`. BLINK needs `blink.min.fps`, judged on the measured frame rate or, when that is unknown (0), the nominal one, so an Android source that reports only its nominal rate still gets BLINK. Without eye measurements, CLOSE_EYES and BLINK are left out.
- `liveness-core`: `Baseline` (each measure's median over the passive window) and `ChallengeVerifiers` (TECHNICAL_DESIGN §7.2), judged against the user's own baseline, because a webcam above or below the face already reads 10° of pitch and a neutral smile scores 0. Each challenge first waits for the face near its baseline, so a pose still held from the previous challenge cannot count; then its action must hold: turns 20° and nods 15° for 300 ms, a smile 0.35 above the baseline with the mouth closed (an open mouth also reads as a smile) for 400 ms, the mouth 0.35 open for 300 ms, both eyes closed (each at or below 0.55; real closed eyes read unevenly, up to 0.47) for 700 ms, a blink closed for at least 2 frames and open again within 600 ms, and a neutral hold within 5° for 1 s. A wrong action never fails a challenge; it times out at `challenge.timeout.ms` (LIV-010). On one person's clips each challenge passed on its own action only.
- `liveness-core`: `ChallengeGuard` checks every frame while the challenges are shown (TECHNICAL_DESIGN §7.2): a second face fails the attempt (LIV-005); a face box that jumps (IoU below 0.3 with the last face, carried across frames without a face, so a swap behind a hand is caught) or a pose that leaps more than 15° between frames under 100 ms apart (a cut in a replayed video) fails it with LIV-009, since a tracking glitch can look the same and is not proof of an attack. No false failure on one person's real clips.
- `liveness-core`: `ModelRegistry` (package `model`) loads the model bundle, `models.json` and its ONNX files, from a folder (`load(Path)`) or from bytes already read from Android assets (`load(Map<String, byte[]>, manifestJson)`). Every file must match its SHA-256 in the manifest; a changed byte, a missing file, a model without a hash or a manifest that is not JSON refuses the whole bundle with LIV-013 (TC-ENG-19). It reads the manifest with a small built-in JSON reader, since the core module takes no third-party dependency. Every model class can now load from bytes, and `OnnxFaceAnalyzer.load(registry, minScore)` and `MiniFasNetPadDetector.load(registry, name, scale)` load from the verified bundle, with the same output as from files.
- `liveness-core`: the real engine, `LivenessEngines.hybrid(models, options)` (`HybridLivenessEngine`, TECHNICAL_DESIGN §6). The models load once from the verified bundle and are shared; each session analyses frames on a worker thread, keeping only the latest frame while busy (TC-ENG-18), and calls back on that thread. A session waits for a usable face (the quality gate's guidance), gathers the passive window, then by mode: HYBRID passes passively at `passive.threshold` or escalates to the random challenges; ACTIVE always challenges; PASSIVE, or HYBRID with `active.enabled=false`, fails below the threshold with LIV-007. Challenges end with the neutral hold. During them the guard (LIV-005, LIV-009) and the passive veto (median below threshold - 0.2, LIV-008) apply; the static-stream veto (LIV-015) applies throughout. Timeouts give LIV-010, an analysis failure ERROR with LIV-013, `cancel()` CANCELLED; `onResult` comes exactly once. The evidence digest is SHA-256 over the nonce, the challenge transcript and each analysed frame's hash (TC-ENG-17). `EngineOptions` asks for an execution provider; this version always uses CPU and says so in `EngineInfo`. On one person's clips the straight face passes passively and a phone photo never passes.
- `liveness-core`: `AttemptTracker` (package `engine`, API.md §3.7), moved from the Android client's `livenessmanager` with the same signature and rules: the limit is the smaller of `max.retries` and the client's own; only errors that count as an attempt use one (a pass, a cancel or a device error never does); `reset()` starts again (TC-ENG-16).
- New workflow `RESIDENT_EXCEPTION_PHOTO` (policy code `exception_photo`) for the exception photo of a resident with biometric exceptions. Its mode defaults to PASSIVE, and only its own key `policy.exception_photo.mode` changes it, not the global `mode`: the person may not be able to do the challenges. Clients call `startLiveness("RESIDENT_EXCEPTION_PHOTO", ...)` for it instead of skipping liveness.
- The engine reports `onProgress` for the progress ring: the passive window filling, then each challenge's elapsed share of `challenge.timeout.ms`. It drops its buffered frames itself on CANCELLED, ERROR and a session timeout (API.md §3.4).
- `liveness-eval`: `VideoFileFrameSource` plays a recorded clip as a frame source, a folder of JPEGs in name order or an MJPEG file (JPEGs back to back, as ffmpeg writes them), decoded like a live SBI stream and stamped at a given frame rate; in real time on a background thread, or all at once for tests. A path that cannot be read refuses to start with LIV-001; an unreadable frame is reported as LIV-012.
- `pad.weights` is now read (`LivenessPolicy.padWeights()`, global only): `name:weight` pairs, each within 0..1, summing to 1. Unusable values fall back to the default with a warning; a cue this build does not have is ignored with a warning.
- Five new global keys under `mosip.registration.face.liveness.quality.` for the gate's limits: `min.face.width` (default 0.18 of the frame width, 115 px on 640, range 0.05..0.9; real faces at a desk webcam measured 125 to 191 px, so the design's quarter would have turned them away), `max.centre.offset` (0.25, range 0.05..0.5), `min.sharpness` (0.40), `min.brightness` (0.235, mean luma 60 of 255) and `max.brightness` (0.784, luma 200), the last three in 0..1. A minimum brightness not below the maximum gives a warning, and both fall back to their defaults.
- A `BytecodeLevelTest` in every module fails the build when a compiled class is not at the module's release level (Java 8 for `liveness-core` and `face-verification`, Java 11 for `liveness-desktop` and `liveness-eval`), so a newer class left in `target/classes` by an editor cannot reach a jar.

### Changed

- `FaceFrame` is now defined as upright and not mirrored, as the camera sees the person: every model assumes an upright face, and in a mirrored frame yaw changes sign. Clients may mirror their preview, never the frames they hand to the engine.
- The `pad.weights` default is now `minifasnet_v2:0.5,minifasnet_v1se:0.5` (API.md §5 had `minifasnet_v2:0.45,minifasnet_v1se:0.45,moire:0.10`). The moire cue was cut from A-09, so a config that still lists `moire` gets a warning and the two MiniFASNet weights are renormalised.

## [0.1.3] - 2026-10-21

Follows MOSIP's answers of 21 Oct: new keys default to the feature off, thresholds go in the packet, and changes target the clients' `master` branches.

### Added

- `PacketEvidence.of(result, policy)`: builds the API.md §9 keys for the face BIR's `others` map, so both clients write identical packet evidence. Adds `LIVENESS_THRESHOLD` (the passive threshold the session applied), as MOSIP asked for thresholds in the packet (21 Oct). Decimals always use a dot; the digest key is left out when the engine made no digest.

### Changed

- `liveness-desktop` and `liveness-eval` now compile with `--release 11`, because the Desktop Registration Client on `master` runs on a Zulu 11 FX JRE (MOSIP asked us to base changes on `master`, 21 Oct). CI builds and tests on JDK 11 and 21.
- `mosip.registration.face.liveness.enabled` now defaults to `false`, so a client without liveness config behaves exactly as before. MOSIP asked for new keys to default to the current behaviour (21 Oct). Configs that want liveness must set `enabled=true`.

## [0.1.2] - 2026-10-20

Same code as 0.1.1, which did not publish on JitPack.

### Fixed

- `jitpack.yml` now installs an exact SDKMAN JDK 21 identifier (`21.0.12+1.1-tem`); `21-open` does not exist, so JitPack fell back to JDK 17 and could not compile the Java 21 modules.

## [0.1.1] - 2026-10-20

### Added

- `face-verification` contract, reviewed with Person B: `FaceVerifier` (single reference, and a list of references with the probe analysed once), `VerificationResult` (MATCH, NO_MATCH, NO_FACE_PROBE, MULTIPLE_FACES_PROBE, REFERENCE_UNUSABLE; similarity clamped from cosine to 0..1; threshold; model version), `FaceVerificationConfig` (threshold 0.363 by default, warnings for invalid values) and `FaceVerifiers.stub(config)`. The real SFace verifier follows (A-25a).
- `bind.min.similarity` now applies to the Desktop geometric binder only; the Android match binder uses the face verification threshold.
- `CaptureFreshness`: on every capture, checks that the device-signed `transactionId` echoes the session nonce and that the signed timestamp is within `bind.freshness.max.age.ms` (default 60000) of the reference time; fails with LIV-014. `newNonce()` makes a valid SBI `transactionId` when liveness is off. Agreed with Person B 20 Oct.
- `LivenessResult.nonce()`: each session's random nonce, to send as the capture request's `transactionId`.
- `LivenessPolicy.bindFreshnessMaxAgeMs()` and the `mosip.registration.face.liveness.bind.freshness.max.age.ms` key (1000..300000).
- Model bundle in `models/` (YuNet, MediaPipe Face Mesh V2 and Blendshape V2, SFace, MiniFASNetV2, MiniFASNetV1SE) and the licence texts they require in `models/licenses/` with `models.json` (SHA-256, licence, source, input shape) and `tools/export_minifasnet.py`, which rebuilds the MiniFASNet ONNX files from the upstream weights and checks them against PyTorch.

### Changed

- ONNX Runtime pinned per platform: Desktop 1.20.0 (the Registration Client's bundled Zulu 21.0.3 cannot load 1.21+), Android 1.28.0. Shared modules compile against 1.20.0.

## [0.1.0] - 2026-10-20

The frozen interface contract (API.md §3, §5, §6) with a scripted stub engine, so both clients can integrate before the real engine lands in 0.2.0.

### Added

- Maven multi-module scaffold: `liveness-core`, `face-verification` (`--release 8`), `liveness-desktop`, `liveness-eval` (Java 21); build on push and pull request; JitPack configuration; MPL-2.0 licence.
- Contract types: frames and sources (`FaceFrame`, `FrameSource`, `FrameListener`, `SourceCapabilities`), analysis and PAD (`FaceAnalyzer`, `FaceObservation`, `PadDetector`, `PadScore`), challenges (`Challenge`, `ChallengeType`, `ChallengeGenerator`, `ChallengeVerifier`), engine and session (`LivenessEngine`, `LivenessSession`, `LivenessCallback`, `LivenessContext`, `LivenessResult`, `UserPrompt`), binding (`CaptureBinder`, `AnalysedFrame`, `BindingResult`) and `LivenessException`.
- `LivenessErrorCode`: the 15 `LIV-0xx` codes with category, message key and attempt rule.
- `LivenessConfigLoader`: per-workflow, global and default resolution of every `mosip.registration.face.liveness.*` key, with range checks and warnings instead of silent failure.
- `LivenessEngines.stub(config)`: scripted sessions on the frames' own timeline, with `stub.force` scenarios `PASS_ACTIVE`, `PASS_PASSIVE`, `NO_FACE`, `FAIL_CHALLENGE`, `TIMEOUT`, `PAD` and `ENGINE_ERROR`.
