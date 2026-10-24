# Test face images

| File | Source | Licence |
| --- | --- | --- |
| `mosip-mockmds-face.png` | The face in MOSIP's mock device data, `mosip-mock-services/MockMDS/Profile/Default/Registration/Face.iso` (ISO 19794-5, JPEG 2000), converted to PNG, 240x320 | MPL-2.0, as published by MOSIP in `mosip/mosip-mock-services` |
| `minivision-sample-T1.png`, `minivision-sample-F1.png`, `minivision-sample-F2.png` | The sample images of Minivision's Silent-Face-Anti-Spoofing, `images/sample/image_T1.jpg` (a real face), `image_F1.jpg` and `image_F2.jpg` (spoofs), at commit `b6d5f04ad78778917853b25c778acef6d5626d15`. Converted to PNG with their EXIF orientation applied, as `cv2.imread` reads them in the upstream pipeline, so 480x640 and upright. JPEG sha256: T1 `f4455149f488f76205fdee5499ec5261d08ef6279a1cff7b778ea85405331e94`, F1 `4b11b5d7a8a8e4a88f5f16a5426a0a7692e39e5bb45bb03b4ebe5e1606336860`, F2 `fbbea73450ae9d9bb555c8ccac77bf39d234261fe3be4190e3ed2999690c485f` | Apache-2.0, Copyright 2020 Minivision, as published in `minivision-ai/Silent-Face-Anti-Spoofing` |

Other test frames are made from this image in code (no face, two faces, dark, blurred, small, off-centre).

Pose and expression shots of team members are **not** kept here: they stay on the developer's machine, and the tests that use them are tagged `local-faces` and skipped when the folder is missing.
