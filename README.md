# MOSIP Face Liveness

Hybrid passive and active face liveness and presentation attack detection (PAD) for the MOSIP Desktop and Android Registration Clients. One shared engine checks face frames for liveness with no user action, escalates to random facial challenges when confidence is low, and blocks face capture and operator or supervisor authentication when liveness fails. Everything runs offline.

Built for MOSIP Decode 2026, Problem Statement 04, by team Incertainty.

> **Status:** project scaffold. Modules build and test; engine code lands from `v0.1.0`.

## Modules

| Module | Java | Purpose |
| --- | --- | --- |
| `liveness-core` | `--release 8` | Engine contract and implementation, shared by Desktop and Android (API 28) |
| `face-verification` | `--release 8` | 1:1 face verification (YuNet alignment + SFace), shared by both clients |
| `liveness-desktop` | 21 | Desktop frame sources, ISO face decoding, JavaFX overlay |
| `liveness-eval` | 21 | Replay harness and ISO/IEC 30107-3 metrics |

Related repositories (forks, branch `ps04/liveness`):

- [registration-client](https://github.com/Incertainty-Labs/registration-client): Desktop client integration
- [android-registration-client](https://github.com/Incertainty-Labs/android-registration-client): Android client integration
- [mosip-mock-services](https://github.com/Incertainty-Labs/mosip-mock-services): webcam and video mock SBI device

## Build

Requirements: JDK 21 and Maven 3.9.

```bash
mvn -B verify
```

## Using the library

Published through JitPack once tagged:

```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>

<dependency>
  <groupId>com.github.Incertainty-Labs.mosip-face-liveness</groupId>
  <artifactId>liveness-core</artifactId>
  <version>TAG</version>
</dependency>
```

The host application supplies ONNX Runtime 1.28.0: `com.microsoft.onnxruntime:onnxruntime` on Desktop, `com.microsoft.onnxruntime:onnxruntime-android` on Android.

## Licence

[Mozilla Public License 2.0](LICENSE). Third-party components and model licences are listed in [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
