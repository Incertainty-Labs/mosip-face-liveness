# Test face images

| File | Source | Licence |
| --- | --- | --- |
| `mosip-mockmds-face.png` | The face in MOSIP's mock device data, `mosip-mock-services/MockMDS/Profile/Default/Registration/Face.iso` (ISO 19794-5, JPEG 2000), converted to PNG, 240x320 | MPL-2.0, as published by MOSIP in `mosip/mosip-mock-services` |

Other test frames are made from this image in code (no face, two faces, dark, blurred, small, off-centre).

Pose and expression shots of team members are **not** kept here: they stay on the developer's machine, and the tests that use them are tagged `local-faces` and skipped when the folder is missing.
