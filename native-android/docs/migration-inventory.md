# Migration inventory and integration ownership

Baseline revision: `582d9086ca0dd3f3d3036c500060daa4f2cc7c60`. Working tree was clean before migration. No AGENTS.md found in repository or its checked parent directories. Original Flutter production files remain unchanged; new native application has a distinct application ID.

| Existing source | Native replacement | Owner / scope |
| --- | --- | --- |
| lib/main.dart, app/app.dart, app/providers.dart | GestureApplication, Compose MainActivity, application SessionStore | Main / architecture A |
| core/config.dart, hand_tracking/domain/hand_models.dart | Pure JVM gesture configs/models; camera TrackingConfig | Gesture C, camera B |
| position_smoother.dart, windowed_motion_detection_service.dart | ContinuousMotionDetector, PositionSmoother | Gesture C |
| flick_detector.dart | FlickDetector | Gesture C |
| hand_pose_classifier.dart, hand_pose_detector.dart | HandPoseClassifier, HandPoseDetector | Gesture C |
| gesture_coordinator.dart | GestureCoordinator with four modes | Gesture C |
| tracking_controller.dart, tracking_state.dart | GestureCameraService, SessionStore, SessionMachine | Main / services D |
| front_camera_service.dart, camera_frame.dart | CameraController / YuvFrame | Camera B |
| HandTrackingChannel.kt, YuvFrame.kt | HandLandmarkerEngine and direct-plane preprocessing without platform channel | Camera B |
| scroll_velocity_mapper.dart | Pure Kotlin ScrollVelocityMapper | Accessibility E |
| ScrollService, FlutterScrollService | ScrollExecutor, ScrollActionCoordinator, AccessibilityScrollAdapter | Accessibility E |
| tracking_screen.dart, hand_overlay.dart | Compose permission/dashboard/settings/diagnostics and WindowManager overlay | Main / services D |
| assets/models/hand_landmarker.task | Same asset included directly in native APK | Camera B / main |
| Dart unit/widget tests | Preserved baseline; shared JSON fixture generator and Kotlin JUnit comparisons | Gesture C, independent QA F |
| native integration fixtures | Native Android instrumentation tests and device-validation guide | Camera B, accessibility E, QA F |

Domain contracts are defined once in :gesture (no Android imports). :app owns Android and depends on :gesture. Constructor injection and the application-owned SessionStore replace Riverpod; no DI framework needed. Main owns Gradle, manifest, resources, session, overlay, UI. Camera, gesture, and accessibility edits are disjoint. Camera B also performed the independent architecture/QA source review and added camera/session instrumentation smoke tests; findings and evidence boundaries are in qa-review.md.

## Decisions

- Two modules enforce an Android-free recognition boundary. No module per package.
- Native minSdk 26 supports application overlays and dispatchGesture. Original Flutter minSdk remains 24.
- Reuse AGP 9.0.1 / Gradle 9.1.0 already installed; AGP built-in Kotlin with explicit Kotlin 2.3.20 JVM/Compose compiler. Compile/target SDK 36 installed locally. Java bytecode target 17; local Android Studio JBR 21 can run Gradle 9.1.
- MediaPipe 0.10.35 and existing model are retained. CameraX 1.5.3 is a documented stable release; dependencies are pinned, not selected by newest version.
- Capture, MediaPipe input, and completion clocks are distinct. Gesture recognition consumes analyzer callback-arrival elapsed time, matching the original Dart camera callback's elapsed timestamp propagated unchanged through tracking. Freshness includes inference and callback delivery delay. Arrival-to-dispatch diagnostics include inference/delivery but exclude sensor exposure/capture delay. Golden replay uses identical explicit timestamps in both languages.
- Analysis follows display rotation and MediaPipe receives upright pixels. Original Flutter capture was locked portrait-up. Front x is mirrored after detection, y is upright, and MediaPipe handedness labels are retained exactly as before. YUV plane stride, padding, and buffer offsets are validated without copying whole camera planes.
- One bounded worker serializes MediaPipe creation, inference, and release. CameraX KEEP_ONLY_LATEST bounds frame retention; elapsed-time gating preserves 15 FPS / 320-pixel defaults. Results reaching main are coalesced and metrics published at most once per second. Diagnostic camera FPS counts analyzer deliveries; hidden CameraX drops are not measured.
- Original open-hand/stable-pose rearm is retained with tap gestures disabled for pure-domain Dart parity. The native app uses timed rearm with a Settings delay slider: pointing/thumbs-up can repeat after the delay and fresh confirmation. Open hand is reserved for one pause tap per presentation and never rearms another action. See [tap gestures](tap-gestures.md).
- Foreground camera starts/resumes only after explicit confirmation in a visible Activity. Pause releases camera. Stop removes overlay and notification. Process restart is stopped; no boot receiver.
- External swipe dispatch is a new adapter: discrete downward gestures map to forward and upward to backward. Continuous detector/velocity mapping remains unchanged; touchscreen movement is bounded incremental swipes rather than a Flutter list ticker.
- No performance gain is claimed without equivalent physical-device measurements.

## Scope to verify

Callers include service pipeline, settings recreation, overlay/notification commands, action executor, golden runners, unit/instrumentation tests, manifest service metadata, model assets, both wrapper scripts, and installation docs. Flutter channels remain solely in the preserved Flutter variant. Native APK must contain neither Flutter classes nor libflutter/libapp.so.
