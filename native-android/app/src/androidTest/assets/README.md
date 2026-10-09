# Hand detector test fixture

`thumb_up.jpg` is the MediaPipe Apache-2.0 fixture from
https://storage.googleapis.com/mediapipe-assets/thumb_up.jpg, used by the
upstream Hand Landmarker tests. The license is in
`../../main/assets/models/LICENSE`.

CameraPipelineTest loads this image from the instrumentation APK and synthesizes
translated/rotated frames. It is excluded from the production APK. This fixture
does not establish physical camera accuracy.
