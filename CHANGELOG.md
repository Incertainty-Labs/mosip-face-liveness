# Changelog

All notable changes to this project are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/). While the version is `0.x`, a contract change needs agreement from both maintainers and an entry here.

## [Unreleased]

### Changed

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
