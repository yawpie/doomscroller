# Migration inventory

The only maintained application is `native-android`. Legacy source can be reviewed
in Git revision `078544f`; it is no longer part of the working tree or build.

| Former responsibility | Current source |
| --- | --- |
| App/providers and screen | GestureApplication, MainActivity, presentation/HandScrollScreen |
| Settings widgets | presentation/SettingsPanel |
| Gesture config/models/smoothing | gesture module configs/models/ContinuousMotionDetector |
| Flick/pose classification and arbitration | FlickDetector, HandPoseClassifier, HandPoseDetector, GestureCoordinator |
| Tracking state/lifecycle | GestureCameraService, SessionStore, SessionMachine |
| Camera capture and platform channel | CameraController, YuvFrame, HandLandmarkerEngine; direct native calls |
| Scroll mapping/execution | ScrollVelocityMapper, ScrollActionCoordinator, AccessibilityScrollAdapter |
| Floating controls | OverlayController |
| Preferences | NativeSettings, SettingsStore |
| Bundled model/license | app/src/main/assets/models |
| Detector test image/license | app/src/androidTest/assets; production model license |
| Historical generated behavior | parity-fixtures/gesture-goldens.json, GoldenParityTest, ScrollGoldenParityTest |
| Device integration checks | app/src/androidTest |

Two modules enforce the useful framework boundary: `gesture` has no Android
imports; `app` owns the service, native adapters and UI. Constructor injection and
application-owned StateFlow stores keep ownership explicit without a DI framework
or a module per package. See [development](development.md).

## Preserved decisions

- Keep the current application ID `dev.autoscroll.nativeapp`, preference names,
  defaults, permission flows, text and Compose layout. Existing native users keep
  their settings. The removed app's separate application ID/data is not converted.
- Retain MediaPipe 0.10.35, the model checksum, CameraX 1.5.3, Gradle 9.1.0,
  AGP 9.0.1 and Kotlin 2.3.20. No runtime dependency upgrade is part of retirement.
- Native minimum API remains 26; compile/target SDK remain 36; bytecode target 17.
- Keep explicit acquisition timestamps, bounded processing, stale-result guards,
  permission checks and asynchronous serialized camera teardown.
- Keep timed rearm, open-hand single tap, thumbs-up double tap, continuous/flick
  modes and their centralized ownership. Historical fixtures intentionally use
  compatibility configuration; native tap tests cover current app behavior.
- Keep the existing touchscreen adapter and explicit visible Start/Resume behavior.
  Do not infer physical camera/feed performance from synthetic tests.

## Retirement coverage

Legacy Dart sources/tests, Android platform-channel wrapper, package manifests,
SDK metadata, generated caches/build outputs and obsolete development instructions
were removed. Production and test assets were moved unchanged before removal;
both native test suites receive explicit fixture paths. CI builds/tests only the
native project. Historical QA reports retain their dated evidence and limits.
