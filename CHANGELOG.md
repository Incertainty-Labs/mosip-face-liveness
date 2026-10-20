# Changelog

All notable changes to this project are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/). While the version is `0.x`, a contract change needs agreement from both maintainers and an entry here.

## [Unreleased]

### Added

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
