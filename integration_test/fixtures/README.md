# Native detector fixture

`thumb_up.jpg` is the MediaPipe project's Apache-2.0 test fixture from
https://storage.googleapis.com/mediapipe-assets/thumb_up.jpg, referenced by
https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/tasks/python/test/vision/hand_landmarker_test.py.
The Apache license is included at `../../assets/models/LICENSE`.

`hand_image.dart` is its base64 encoding so the Android integration test can load
it without adding test images to the production Flutter asset bundle. To regenerate:

```powershell
$bytes = [System.IO.File]::ReadAllBytes('integration_test/fixtures/thumb_up.jpg')
$encoded = [Convert]::ToBase64String($bytes)
Set-Content integration_test/fixtures/hand_image.dart "const handImageBase64 = '$encoded';"
```

The test synthesizes translated and rotated YUV frames to verify the actual native
detector's coordinates. It does not represent physical camera acceptance testing.
