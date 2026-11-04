# Hand detector test fixture

`thumb_up.jpg` is the MediaPipe Apache-2.0 fixture from
https://storage.googleapis.com/mediapipe-assets/thumb_up.jpg, used by the
upstream Hand Landmarker tests. The license is in
`../../main/assets/models/LICENSE`.

CameraPipelineTest loads this image from the instrumentation APK and synthesizes
translated/rotated frames. It is excluded from the production APK. This fixture
does not establish physical camera accuracy.

`pointing_up.jpg` comes from
https://storage.googleapis.com/mediapipe-assets/pointing_up.jpg and is also used by
the upstream Apache-2.0 Hand Landmarker tests:
https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/tasks/python/test/vision/hand_landmarker_test.py.
Its rotated copies test all four screen directions using actual model output.
It is packaged only in the instrumentation APK.
