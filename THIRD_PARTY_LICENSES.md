# Third-party licences

Every dependency and model shipped with this project uses an OSI-approved licence. Entries are added when a component is added.

## Libraries

| Component | Version | Licence | Used by | Source |
| --- | --- | --- | --- | --- |
| ONNX Runtime (Java API) | 1.20.0 (Desktop), 1.28.0 (Android) | MIT | `liveness-core`, `face-verification` (provided by the host), `liveness-desktop`, `liveness-eval` | https://github.com/microsoft/onnxruntime |
| Jackson Databind | 2.17.2 | Apache-2.0 | tests only | https://github.com/FasterXML/jackson-databind |
| JUnit 5 | 5.11.4 | EPL-2.0 | tests only | https://junit.org/junit5/ |

## Models

Full details (file, version, SHA-256, input shape) are in [`models/models.json`](models/models.json). The licence texts these models require ship in [`models/licenses/`](models/licenses/): the Apache License 2.0 and the MIT notice for YuNet.

| Model | Licence | Source | Notes |
| --- | --- | --- | --- |
| YuNet face detector `2023mar` | MIT | https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet | Unmodified |
| MediaPipe Face Mesh V2 | Apache-2.0, © Google LLC | https://github.com/PINTO0309/PINTO_model_zoo/tree/main/410_FaceMeshV2 (model card: https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MediaPipe%20Face%20Mesh%20V2.pdf) | Modified: ONNX conversion of the original TFLite by PINTO model zoo |
| MediaPipe Blendshape V2 | Apache-2.0, © Google LLC | https://github.com/PINTO0309/PINTO_model_zoo/tree/main/390_BlendshapeV2 (model card: https://storage.googleapis.com/mediapipe-assets/Model%20Card%20Blendshape%20V2.pdf) | Modified: ONNX conversion of the original TFLite by PINTO model zoo |
| SFace face recognition `2021dec` | Apache-2.0 | https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface | Unmodified. Training data not documented upstream; the SFace paper uses research-only datasets (CASIA-WebFace, VGGFace2, MS1M) |
| MiniFASNetV2 `2.7_80x80` | Apache-2.0, © 2020 Minivision | https://github.com/minivision-ai/Silent-Face-Anti-Spoofing | Modified: converted from the PyTorch weights to ONNX by `tools/export_minifasnet.py`. Training data not documented upstream |
| MiniFASNetV1SE `4_0_0_80x80` | Apache-2.0, © 2020 Minivision | https://github.com/minivision-ai/Silent-Face-Anti-Spoofing | Modified: converted to ONNX as above. Training data not documented upstream |
