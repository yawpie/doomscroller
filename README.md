# Hand Scroll Lab

An Android Flutter MVP that detects one hand with the front camera and scrolls its own reading list. MediaPipe inference and motion processing run entirely on-device. There is no backend, recording, frame upload, or runtime model download. Build-time dependency downloads require internet; installed inference works offline.

## Setup and run

Requirements: Flutter 3.44.1 / Dart 3.12.1 (or a compatible newer stable SDK), Android SDK 36, Java 17+ (verified with Android Studio JDK 21), accepted SDK licenses, and an Android API 24+ device with a front camera. Use a physical Android phone for the acceptance checklist below.

```sh
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
flutter run -d <android-device-id>
```

Android configuration is included: `minSdk = 24`, CAMERA permission, front-camera selection, portrait-up capture, and official `com.google.mediapipe:tasks-vision:0.10.35`. No AccessibilityService is registered. Unneeded microphone/storage permissions are removed. Release removes INTERNET; debug/profile retain Flutter's development network permission for tooling. The generated debug signing configuration must be replaced before distribution.

### Model

`assets/models/hand_landmarker.task` is bundled and registered in `pubspec.yaml`. No download is needed when running the app. Its source, Apache license, size, and SHA-256 are in [assets/models/README.md](assets/models/README.md). If restoring a missing model, download the exact linked model to that path, verify its checksum, and rebuild; do not download it at runtime. Missing/empty assets or detector initialization errors appear on screen.

### Controls

1. Tap **Start tracking**, grant camera permission, and hold one hand in the front preview.
2. Hold still for approximately 600 ms to establish the neutral Y reference. Calibration is automatic and does not gate movement.
3. Move down to increase the content offset, or up to decrease it. Faster motion increases speed; holding still stops. At the top, upward motion cannot move the list further.
4. Adjust **Sensitivity** (300–4000 pixels per normalized image height), or toggle **Invert scroll**. A setting change stops the old command until the next hand result.
5. Toggle **Debug overlay** for all 21 landmarks, emphasized wrist/palm bases, yellow palm center, green neutral line, palm X/Y and inference latency. Disable it to reduce debug painting.
6. **Stop tracking** stops scrolling and releases the camera/detector. Backgrounding suspends them; returning resumes only if tracking was requested. After permission denial, retry or enable Camera in Android app settings if Android no longer prompts.

The screen reports detection, DISABLED / SEARCHING / TRACKING / LOST, IDLE / UP / DOWN, hand velocity (image heights/s), smoothed scroll velocity (logical pixels/s), and completed inference FPS. The dummy content remains touch-scrollable.

## Architecture

```text
Front camera (requested 30 FPS preview, medium resolution)
  -> rate cap (12 inference FPS) + one in-flight frame; drop stale frames
  -> MethodChannel -> Kotlin worker: sampled YUV420 -> RGB -> upright rotation
  -> official MediaPipe Hand Landmarker, CPU / VIDEO mode / one hand
  -> timestamped HandTrackingResult with 21 indexed xyz landmarks
  -> palm center -> EMA -> bounded timestamped regression window
  -> displacement/velocity thresholds + stationary/jump guards -> HandMotion
  -> proportional velocity mapper -> display ticker -> Flutter ScrollController
```

Riverpod wires the existing feature-oriented services and publishes immutable tracking state. Widgets render state and forward controls/lifecycle events. The existing CameraService, HandTrackingService, MotionDetectionService and ScrollService boundaries remain; no new packages or architectural restructuring were needed. `ScrollVelocityMapper` isolates proportional mapping for camera-free testing. [docs/architecture.md](docs/architecture.md) documents ownership, channel data, coordinate conventions and extension boundaries.

```text
lib/
  main.dart
  app/{app,providers}.dart
  core/config.dart
  features/
    camera/{application,domain,infrastructure}/
    hand_tracking/{domain,infrastructure}/
    gesture/{application,domain}/
    auto_scroll/
      application/{tracking_controller,tracking_state,scroll_velocity_mapper}.dart
      domain/scroll_service.dart
      infrastructure/flutter_scroll_service.dart
      presentation/{tracking_screen,hand_overlay}.dart
android/app/src/
  main/kotlin/dev/autoscroll/autoscroll_compvis/{MainActivity,HandTrackingChannel,YuvFrame}.kt
  main/AndroidManifest.xml
  release/AndroidManifest.xml
  test/kotlin/dev/autoscroll/autoscroll_compvis/YuvFrameTest.kt
assets/models/{hand_landmarker.task,README.md,LICENSE}
test/{motion_detection,tracking_controller,scroll_velocity_mapper,scroll_and_screen}_test.dart
integration_test/{native_landmarks,tracking_smoke}_test.dart
integration_test/fixtures/
docs/architecture.md
```

## Native MediaPipe and coordinates

The `autoscroll/hand_tracking` MethodChannel calls `initialize`, `detect`, and `dispose`. One Kotlin worker owns conversion, model lifetime and synchronous `detectForVideo`; expensive image work never runs on Flutter's UI thread. VIDEO mode preserves temporal hand tracking while giving one request/one response. Raw planes include row/pixel stride, supporting padding and planar/interleaved chroma.

Frames are rotated upright before inference. Only output X is then mirrored to match the selfie preview; Y increases downward. Results contain 21 ordered landmark indices, finite normalized X/Y, relative Z, the frame timestamp, and optional handedness/classification confidence. MediaPipe's public task result does **not** expose per-result hand detection/presence confidence, so those scores are not invented. Thresholds for detection/presence/tracking remain configurable.

The Android real-image integration test uses the upstream hand fixture and verifies actual MediaPipe output for all four sensor rotations, downward-increasing Y and mirrored X. Another test connects those native landmarks to motion detection and visible list scrolling. These tests do not prove alignment for every physical camera. References: [official Android guide](https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker/android), [official result description](https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker/python#handle_and_display_results), [camera permissions/lifecycle](https://pub.dev/packages/camera).

Release shrinking rules preserve the protobuf/JNI/Flogger contracts required by MediaPipe. Keep `android/app/proguard-rules.pro`. On Windows with Pub cache/project on different drives, Kotlin incremental compilation is disabled locally to avoid cross-drive cache failures.

## Motion algorithm and configuration

`lib/core/config.dart` is the tuning point; Riverpod provider overrides can configure services before they are created. Sensitivity and inversion also change live from the screen.

- Palm X/Y averages wrist and four finger bases: indices 0, 5, 9, 13, 17. Fingertips do not control the position.
- EMA: `smoothedY = alpha * currentY + (1-alpha) * previousY`. The default alpha is 0.4; the existing PositionSmoother can be replaced.
- Up to six smoothed positions are retained, requiring four valid frames. Displacement is last-minus-first; least-squares slope uses actual timestamps to estimate normalized velocity.
- Entry requires displacement >= 0.018 and speed >= 0.025. Moving displacement hysteresis uses 0.008; two confirming frames and a 150 ms direction cooldown suppress chatter/reversals.
- A raw one-step stationary guard (<= 0.015 heights/s) only **cancels** old motion. Recognition still requires the smoothed multi-frame window. It clears EMA/regression momentum so a stationary displaced hand does not sustain scrolling.
- A position jump > 0.15 image heights resets history/calibration instead of creating a fling. Missing hands and sample gaps > 350 ms reset too; duplicate/out-of-order timestamps cannot move the list.
- A stable position within 0.012 of the initial stable anchor for 600 ms establishes neutral Y from recent smoothed samples. Neutral is a calibration/debug reference, not a persistent position-based scroll command. Loss/jump resets it.
- Scroll target = signed hand speed × sensitivity, clamped to ±1200 pixels/s; speeds <= 0.025 heights/s map to zero. A second EMA (alpha 0.3 at 60 Hz, adjusted for ticker time) smooths speed changes. Idle/loss/stop immediately zero velocity. There is no artificial minimum speed above zero.
- The display ticker integrates actual elapsed time, caps a step at 50 ms, clamps content boundaries, and stops if results are older than 350 ms. It does not issue long animateTo operations.

| Configuration | Defaults / purpose |
| --- | --- |
| TrackingConfig | 12 inference FPS; confidence thresholds 0.5; maximum inference dimension 320; loss timeout 750 ms |
| MotionDetectionConfig | EMA 0.4; window 6 / minimum 4; displacement 0.018 / hysteresis 0.008; velocity 0.025; confirmation 2; cooldown 150 ms; gap 350 ms; jump 0.15; stationary 0.015; calibration 600 ms / radius 0.012 |
| ScrollConfig | sensitivity 1800 (UI range 300–4000); max 1200 px/s; velocity dead zone 0.025; EMA 0.3; freshness 350 ms |

DISABLED means processing is off. SEARCHING means no hand has yet been acquired (or loss lasts >= 750 ms). TRACKING means the latest result has a hand. LOST means an acquired hand disappeared recently. Missing results also clear the overlay and motion via a 750 ms result watchdog; scrolling has the shorter 350 ms watchdog.

## Performance decisions

Preview capture requests 30 FPS independently of the configurable inference cap. Busy/throttled frames are dropped before the channel; the next eligible fresh frame is accepted, with no waiting frame queue. Native code also rejects overlapping detection. The default worker samples a 640×480 frame to 320×240 during conversion, reducing converted pixels/RGB allocation by four times without resizing the preview. Sampling is configurable; small/distant hands may benefit from a larger inference dimension. Channel transport still carries full YUV planes. RGB/bitmap allocations are bounded by one inference; native CameraX-only capture or reusable buffers should be considered only after device profiling shows a need.

The UI updates tracking diagnostics at inference cadence and only the scroll-velocity label listens at ticker cadence. Debug mode adds optional painting. Requested preview/UI/inference rates are targets, not guarantees.

## Testing

```sh
flutter analyze
flutter test
flutter build apk --debug
# From android/, with JAVA_HOME pointing at a supported JDK:
./gradlew :app:testDebugUnitTest
# Windows: .\gradlew.bat :app:testDebugUnitTest
flutter test integration_test -d <android-device-id>
```

The camera smoke test needs permission granted when its Android dialog appears; Flutter may reinstall the test application and reset the grant between integration files. It verifies a real camera/native response, stop and lifecycle restart. The fixture tests need no camera permission, but require Android for the actual native model. Test images are embedded only in integration-test source, not the production asset bundle.

Unit tests use synthetic landmark sequences for stationary/jitter, slow/fast up/down, loss, jumps, frame intervals, boundaries and calibration; mapping tests cover proportional speed, clamping, inversion and invalid values. Coordinator tests cover lifecycle races, dropped frames, explicit states, denial and inference errors. Native tests cover YUV padding, interleaved chroma, truncated input and downsampling.

## Physical-device acceptance (pending)

- Launch on a real API 24+ Android phone, grant permission and check a responsive front preview.
- Enable debug overlay. Verify wrist/palm alignment, X mirroring and increasing Y when lowering the hand; repeat on front sensors with different rotations.
- Hold still: neutral is established and content remains stationary. Move up/down: offset changes in the expected direction; faster movement scrolls faster. Test inversion and sensitivity.
- Stop moving: scrolling stops. Remove the hand: LOST then SEARCHING with zero scrolling. Return: fresh acquisition and calibration work.
- Stop/start, deny/regrant permission, background/foreground, lock/unlock and interrupt with another camera app. Confirm recovery without freezes.
- Test airplane mode. Measure preview/inference/UI FPS, inference latency, memory, heat and battery under sustained tracking and different lighting/distances.

## Limitations and next iteration

Android phone/portrait-up only; tablet/foldable orientation overrides need separate validation. One hand, no hand identity continuity or arming gesture. Calibration and thresholds need physical-device tuning. Continuous movement has finite travel in the image; stationary hands deliberately stop, so there is no persistent position-based scrolling or clutch behavior. Debug APKs retain tooling network permission; release has no INTERNET. Native process aborts cannot be recovered by exception handling. Release distribution signing remains pending.

Next: complete the physical acceptance checklist and profile/tune on target phones, then package a signed offline release. Add system-wide AccessibilityService scrolling only in a later iteration with its own explicit permission/lifecycle design. Accounts, backend, analytics, cloud inference, iOS, training and multi-hand control remain outside this MVP.
## Implementation changes

Created:

- `lib/features/auto_scroll/application/scroll_velocity_mapper.dart`
- `lib/features/auto_scroll/presentation/hand_overlay.dart`
- `test/scroll_velocity_mapper_test.dart`
- `integration_test/native_landmarks_test.dart`
- `integration_test/fixtures/{README.md,thumb_up.jpg,hand_image.dart}`

Modified:

- `lib/app/providers.dart`, `lib/core/config.dart`
- `lib/features/auto_scroll/application/{tracking_controller,tracking_state}.dart`
- `lib/features/auto_scroll/infrastructure/flutter_scroll_service.dart`
- `lib/features/auto_scroll/presentation/tracking_screen.dart`
- `lib/features/camera/infrastructure/front_camera_service.dart`
- `lib/features/gesture/application/windowed_motion_detection_service.dart`
- `lib/features/gesture/domain/hand_motion.dart`
- `lib/features/hand_tracking/domain/hand_models.dart`
- `lib/features/hand_tracking/infrastructure/mediapipe_hand_tracking_service.dart`
- `android/app/src/main/kotlin/dev/autoscroll/autoscroll_compvis/{HandTrackingChannel,YuvFrame}.kt`
- `android/app/src/test/kotlin/dev/autoscroll/autoscroll_compvis/YuvFrameTest.kt`
- `test/{motion_detection,tracking_controller,scroll_and_screen}_test.dart`
- `integration_test/tracking_smoke_test.dart`
- `README.md`, `docs/architecture.md`

No dependencies were added and the existing feature folders/service contracts were retained. MotionConfig was renamed MotionDetectionConfig and its callers updated. Scrolling settings remain local to the Flutter adapter; the ScrollService contract stays reusable for a future explicitly scoped Android adapter.

## Verification on 6 October 2026

| Check | Result |
| --- | --- |
| flutter analyze | No issues |
| flutter test | 31 tests passed |
| Android :app:testDebugUnitTest | 4 tests passed |
| Android native-landmark integration tests | 2 tests passed: rotations/mirroring and real-landmark-driven scrolling |
| Android camera integration smoke test | 1 test passed: camera/native result, lifecycle restart and shutdown |
| flutter build apk --debug | Successful; build/app/outputs/flutter-apk/app-debug.apk |
| flutter build apk --release | Successful; build/app/outputs/flutter-apk/app-release.apk |

Integration runs used an API 34 x86_64 emulator. The real-image pipeline test measured a mean native request duration of 69 ms over 16 frames, excluding test-fixture generation. Debug and release previews were visually inspected offline in SEARCHING, with approximately 5–6 completed inferences/s in the emulator's synthetic scene. This is below the configured cap and is not a physical-phone throughput result. The release was installed, launched and ran native inference in airplane mode with Wi-Fi disabled. Its permissions were inspected: no INTERNET, RECORD_AUDIO, READ_EXTERNAL_STORAGE or WRITE_EXTERNAL_STORAGE. It retains CAMERA, ACCESS_NETWORK_STATE and an internal signature receiver permission.

Remaining tooling warnings concern Flutter's embedded Kotlin compatibility and AGP 9 legacy Kotlin/DSL deprecations. No Dart analyzer warnings remain. Physical-camera hand behavior, the 10–15 FPS phone target, sustained memory/thermal/battery behavior and distribution signing remain unverified.
