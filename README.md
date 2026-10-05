# Hand Scroll Lab

An Android Flutter application that uses the front camera and on-device MediaPipe Hand Landmarker to scroll its own reading list. There is no backend, frame upload, recording, or runtime model download. Build-time dependency downloads require internet; the installed application works offline.

## Setup

- Flutter 3.44.1 / Dart 3.12.1 or a compatible newer stable SDK.
- Android SDK 36, a compatible Java 17+ toolchain (verified with Android Studio's JDK 21), and accepted SDK licenses.
- Android 7.0 / API 24 or newer with a front camera. A physical device is needed to evaluate real hand tracking.

```sh
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
flutter run -d <android-device-id>
```

The 7.8 MB model is already included at `assets/models/hand_landmarker.task`. Its provenance and checksum are in [assets/models/README.md](assets/models/README.md). No additional model setup is required.

Tap **Start tracking**, grant camera access, and hold one hand in view. Move down to increase the list's scroll offset, or up to decrease it. At the top of the list, upward motion cannot scroll further. The screen shows detection status, action, normalized vertical velocity, and completed inference FPS (updated once per second). **Stop tracking** releases the camera and detector. After denial, retry; if Android no longer prompts, enable Camera in the application's Android settings.

## Architecture and pipeline

```text
Front camera / YUV420 frames
  -> rate limit + one in-flight frame (default 12 FPS)
  -> Kotlin worker: YUV to RGB + portrait rotation
  -> MediaPipe Hand Landmarker (CPU, VIDEO mode)
  -> normalized HandTrackingResult / HandPose / HandLandmark
  -> palm center -> exponential smoothing -> rolling displacement / velocity
  -> thresholds + dead zone + confirmation / cooldown -> HandMotion / HandAction
  -> ScrollService -> Flutter ScrollController
```

Riverpod wires services and publishes immutable debug state. Widgets render state and forward user/lifecycle events. Motion models and interpretation use Dart without MediaPipe or camera SDK classes. No code generation, repositories, or use-case wrappers are needed.

[docs/architecture.md](docs/architecture.md) explains ownership, threading, the channel contract, action semantics, and the future Android scrolling seam.

## Source structure

```text
lib/
  main.dart
  app/
    app.dart
    providers.dart
  core/
    config.dart
  features/
    camera/
      application/camera_service.dart
      domain/camera_frame.dart
      infrastructure/front_camera_service.dart
    hand_tracking/
      domain/hand_models.dart
      domain/hand_tracking_service.dart
      infrastructure/mediapipe_hand_tracking_service.dart
    gesture/
      domain/hand_motion.dart
      domain/motion_detection_service.dart
      application/position_smoother.dart
      application/windowed_motion_detection_service.dart
    auto_scroll/
      domain/scroll_service.dart
      application/tracking_controller.dart
      application/tracking_state.dart
      infrastructure/flutter_scroll_service.dart
      presentation/tracking_screen.dart
android/app/src/
  main/kotlin/dev/autoscroll/autoscroll_compvis/
    MainActivity.kt
    HandTrackingChannel.kt
    YuvFrame.kt
  main/AndroidManifest.xml
  release/AndroidManifest.xml
  test/kotlin/dev/autoscroll/autoscroll_compvis/YuvFrameTest.kt
assets/models/
  hand_landmarker.task
  README.md
  LICENSE
test/
  motion_detection_test.dart
  tracking_controller_test.dart
  scroll_and_screen_test.dart
docs/
  architecture.md
integration_test/
  tracking_smoke_test.dart
```

Standard Flutter Android Gradle files, resources, `pubspec.yaml`, and `pubspec.lock` are also included. The pre-existing `graft/` directory is unrelated and preserved.

`android/app/proguard-rules.pro` preserves the MediaPipe JNI/protobuf and logger contracts in shrunk release builds.

## MediaPipe integration

A MethodChannel named `autoscroll/hand_tracking` calls the official Android `com.google.mediapipe:tasks-vision:0.10.35` API. A Kotlin worker owns initialization, conversion, inference, and disposal. Synchronous `detectForVideo` executes on that worker, never the Flutter UI thread, and uses increasing frame timestamps. VIDEO mode retains temporal tracking without needing a second asynchronous result channel.

Only accepted frames cross this bridge. Incoming YUV planes carry row and pixel stride; conversion supports planar and interleaved chroma with padding. One hand is returned, rotated upright; x is mirrored to match the selfie preview. Dart receives primitive coordinates and the optional handedness classification score. This score is **not** a hand detection or presence confidence. Detection, presence, and tracking thresholds are configured at initialization; the API does not expose those scores per result.

References: [official Android integration guide](https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker/android), [model overview](https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker), [camera lifecycle and permissions](https://pub.dev/packages/camera).

## Motion processing and tuning

`core/config.dart` groups tracking, motion, and scrolling settings. Override their Riverpod providers before creating the coordinator for tests or alternative configurations.

- Palm y is the mean of wrist and four finger base landmarks (indices 0, 5, 9, 13, 17).
- Exponential smoothing defaults to alpha 0.4. Inject a different `PositionSmoother` without changing consumers.
- A bounded six-sample window requires at least four valid frames. Least-squares slope estimates velocity using actual timestamps; displacement is the window's last minus first smoothed position.
- Enter motion at displacement >= 0.018 image heights and speed >= 0.025 heights/s. Once moving, use a smaller 0.008 displacement dead zone for hysteresis.
- A direction requires two confirming frames; direction transitions have a 150 ms cooldown. An unconfirmed reversal emits idle.
- Missing hands immediately emit stop, clear history, and require a fresh warmup. Gaps over 350 ms also reset history. Duplicate/out-of-order samples emit idle without entering the window.
- Scroll speed is absolute normalized hand velocity multiplied by 1800 pixels, capped at 1200 pixels/s. A display ticker integrates speed, clamps list boundaries, and caps each time step to avoid a jump after a stalled UI. Idle/start/stop cancel scrolling. A 350 ms freshness watchdog also stops scrolling if results cease.

Tests cover the requested stationary, slow/fast up/down, jitter, and temporary-loss cases, plus gaps, timestamp order, settling, reversals, lifecycle races, dropped frames, permission/inference errors, the screen, scroll direction/bounds, and the freshness watchdog. They do not need a camera.

Native conversion tests can also be run with:

```sh
cd android
# POSIX:
./gradlew :app:testDebugUnitTest
# Windows PowerShell:
.\gradlew.bat :app:testDebugUnitTest
```

An Android smoke test starts the real camera, requires a response from the packaged native detector, then stops and checks resource shutdown. Grant camera permission beforehand on a test device (or respond to the permission dialog) and run:

```sh
flutter test integration_test/tracking_smoke_test.dart -d <android-device-id>
```

For Windows builds with the project and Pub cache on separate drives, `android/gradle.properties` disables Kotlin incremental compilation to avoid its cross-drive relative-path cache failure. This affects build speed, not application behavior.

## Known limitations

- Android only; the MediaPipe channel has no iOS implementation.
- Portrait-up only. Large-screen Android devices may override orientation restrictions; tablets and foldables need orientation-aware frame rotation before being supported.
- One hand, no identity continuity if a different hand replaces it, no pose-based arming gesture. Start/stop refer to tracking acquisition/loss and the UI control, not finger gestures.
- Thresholds and speed mapping need calibration on physical devices, with different lighting and hand distances. The configured FPS is a cap, not a throughput guarantee.
- The camera plugin delivers its stream to Dart before gating. This limits inference/channel conversion work, but does not eliminate the plugin's own per-frame transport. Native CameraX-only capture is a profiling-driven future option.
- Frames allocate RGB buffers/bitmaps on the worker; memory is bounded by one in-flight frame but sustained heat, latency, and battery usage still need physical-device profiling.
- Continuous motion has finite travel in the image; this initial mapping does not implement clutching or persistent scrolling while the hand is stationary.
- Backgrounding suspends tracking and releases resources; foregrounding resumes only if tracking was requested. There is no background service.
- Microphone/storage permissions inherited from the camera plugin are removed. The debug build uses Flutter's standard development network permission for tooling. Release explicitly removes INTERNET, enforcing offline operation. Android dependencies may retain network-state inspection and an internal signature permission; neither permits internet access. Release signing still uses the generated debug configuration and must be replaced before distribution.

## Roadmap / remaining MVP work

1. Validate portrait rotation, preview alignment, permissions, resume, and hand detection on physical Android devices.
2. Measure inference FPS, latency, memory, thermal behavior and battery usage; tune thresholds and scrolling gain with real users.
3. Decide whether explicit pose-based arming or a clutch/recentering interaction is required, then implement and test that agreed behavior.
4. Package a signed release and verify it offline on the target devices.
5. Later, add an Android AccessibilityService-backed `ScrollService`, its permission/settings flow and target-app behavior tests. System-wide scrolling is intentionally not implemented in this scaffold.


## Verification in this workspace

Verified on 6 October 2026 using Flutter 3.44.1 / Dart 3.12.1 and an Android API 34 x86_64 emulator:

- Strict `flutter analyze`: no issues.
- `flutter test`: 22 tests pass, including every requested motion scenario.
- `:app:testDebugUnitTest`: 3 native conversion tests pass.
- Android camera/MediaPipe integration smoke test: passes.
- Debug and release APKs compile. The release was installed, launched, and processed the emulated front-camera stream with airplane mode enabled and Wi-Fi disabled.
- The release merged manifest contains no INTERNET, RECORD_AUDIO, READ_EXTERNAL_STORAGE, or WRITE_EXTERNAL_STORAGE permission.
- The release camera preview and debug indicators were visually inspected; the emulator produced approximately 4–5 completed inferences/s with no hand in its synthetic scene. This is not a physical-device hand accuracy or performance measurement.

Remaining tooling warnings are Flutter/Gradle Kotlin version compatibility and AGP 9 deprecations for the generated legacy Kotlin/DSL settings. The Windows cross-drive cache failure and MediaPipe release shrinking failures are handled by project-local settings/rules. Physical-device validation and distribution signing remain pending.
