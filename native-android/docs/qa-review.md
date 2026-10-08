# Independent architecture and lifecycle QA review

Source review performed 2026-10-08 by the camera implementation agent, separately from the service/UI and accessibility implementation owners. This report distinguishes inspected code, supplied tests, executed checks, and device evidence. UI and service integration were still being completed during the first review; line references below identify that snapshot.

## Architecture boundary audit

| Boundary | Source evidence | Assessment |
| --- | --- | --- |
| Recognition | `gesture/src/main/kotlin/dev/autoscroll/nativeapp/gesture/` | Kotlin/JVM models, detectors and coordinator; no Android or MediaPipe imports. Explicit timestamps permit camera-free replay. |
| Capture/inference | `app/.../camera/CameraController.kt`, `mediapipe/HandLandmarkerEngine.kt` | Front CameraX image analysis owns one bounded worker; VIDEO MediaPipe retains one-hand defaults. Worker creates/closes model; main receives results, errors and completion callbacks. |
| Pixel orientation | `camera/YuvFrame.kt`, `HandLandmarkerEngine.kt` | Direct YUV reads preserve plane offsets, stride and padding; four sensor rotations produce upright pixels; normalized x is mirrored after inference, with handedness unchanged. |
| Session ownership | `session/GestureCameraService.kt`, `SessionMachine.kt`, `SessionStore.kt` | LifecycleService owns capture, overlay, action coordinator and watchdog. Generation tokens reject stale camera callbacks. Application state survives Activity recreation only; process recreation does not auto-start. |
| Overlay | `overlay/OverlayController.kt` | Native WindowManager view belongs to the service, with non-focusable window, drag slop, clamped positioning and explicit removal. No Activity reference is retained. |
| Scroll execution | `scrolling/ScrollActionCoordinator.kt`, `AccessibilityScrollAdapter.kt` | Landmark-free executor boundary. At most one operation; additional discrete events are consumed/rejected rather than queued for replay. Generation tokens protect cancellation and stale completion. |
| Accessibility scope | `accessibility/GestureAccessibilityService.kt`, `res/xml/gesture_accessibility.xml` | Public dispatchGesture; only window package metadata, no node traversal or screen text. Window-content retrieval disabled and accessibilityTool=false. Screen lock/privacy-sensitive surfaces block dispatch. |
| Persistence | `settings/SettingsStore.kt` | SharedPreferences is existing-platform persistence; no added DataStore dependency. User settings/affirmative consent are separate from volatile session state. |
| Packaging/privacy | manifest and model sourceSet | Separate native application ID; original Flutter is retained. No INTERNET permission, recording path, boot restart or analytics SDK. Existing model is included directly from repository assets. Final APK inspection remains required. |

## Concrete findings sent to implementation owners

1. **Stop/Start teardown serialization (P1, source fix re-reviewed):** the first `GestureCameraService.kt` snapshot called `releaseSession()` at line 209 and `stopSelf()` at 214 while camera cleanup remained asynchronous. Per-service `closingCamera` only serialized Pause/Resume; a newly created service could start a second worker while the previous service still released MediaPipe. The final reviewed code stores a `CompletableDeferred` in application-level `SessionStore.cameraReleased`, completes it after camera close, and awaits it before constructing the next controller with generation/destroyed checks. Rapid device Stop/Start remains unverified.
2. **Watchdog reacquisition gap (P2, source fix re-reviewed):** first snapshot lines 156-163 disabled the action coordinator on stalled results, but resumed it on a later 250ms watchdog tick. A newly recognized event before that tick could be consumed and rejected as inactive. The reviewed `onResult` now re-enables actions immediately on an accepted recovered result before arbitration/dispatch.
3. **Settings cross-field consistency (P2, source fix re-reviewed):** detector validation requires flick `minimumVelocity > 0.30` while default rearmVelocity stays 0.15; pose entry thresholds must remain compatible with exit thresholds (index >=150, curled <=125, alignment >=0.70). Reviewed UI ranges respect these constraints. The shared `NumberSetting` now clamps converted Float values back to the original Double interval, fixing .8 becoming .800000011 and .70 becoming .699999988. Settings pause capture and apply to the next session; the UI describes that behavior.
4. **Manual isolation path (source re-reviewed):** initial service manual commands required an Active camera session. `MainActivity.testSwipe` now uses an independent coordinator, moves its task to the background, waits one second for the target window, and dispatches a single explicit swipe with the camera stopped. Readiness and affirmative consent gate the controls. This path has not been executed against an external application. API completion alone is not proof that content changed.
5. **Overlay interaction ownership (source fix re-reviewed):** both bubble drag/touch and expanded button touch now notify pressed/released ownership. The service suppresses recognition during interaction and uses an 850ms delay for explicit manual tests. Model callbacks still refresh the watchdog without entering recognition while interaction/manual output owns the session.
6. **Retained overlay after disabling (P2, source fix re-reviewed):** changing overlayEnabled=false paused capture but initially retained the old window. Resume did not create a new widget, yet backgrounding could show the previous one. The reviewed service now collects saved settings and immediately closes/nulls any overlay when disabled, including while Paused.
7. **Persisted discrete swipe duration (P2, source fix re-reviewed):** initial SettingsStore reload restored swipeDurationMs but left ScrollConfig.flickDurationMs at220. Discrete ScrollRequests override the adapter duration, so a saved400ms setting would execute220ms after restart. The reviewed store loads the saved duration into both settings and validates flick/pose detector constraints on load/update. The added reload regression test uses an isolated qa_ preference namespace and deletes only that test namespace; production settings are untouched.

## Timing and diagnostic limitations

The native recognition stream uses analyzer callback-arrival elapsed timestamps. This preserves the original Dart camera callback's `_clock.elapsed` timestamp propagated unchanged through MediaPipe and TrackingController. It is not sensor exposure time and it is not inference completion. The timestamp mapping test deliberately separates input/completion and verifies that freshness includes inference and main-delivery delay. Capture timestamps remain in the camera clock and must not be subtracted from elapsedRealtime without validating the camera timestamp source.

`cameraFps` measures analyzer deliveries, including frames rejected by the throttle. CameraX's hidden KEEP_ONLY_LATEST drops cannot be counted from this API. Inference latency includes sampled YUV conversion and MediaPipe. A bounded rolling window retains only the last 120 latency values; average and nearest-rank P50/P95 are computed on the worker at most once per second. Input-arrival-to-dispatch latency includes inference and delivery, but excludes sensor exposure/capture delay. No performance improvement or battery/thermal result is established by architecture, compilation, synthetic fixtures or model-fixture smoke tests.

Late inference (>=lostAfterMs) and overly delayed main delivery become no-hand results. A closed controller cannot emit gesture actions. close(onClosed) completes on main after worker disposal; it does not block main. Android's public dispatchGesture API cannot cancel an already submitted stroke: pause/stop prevents subsequent output, but one already-running short gesture can finish. API 26-29 content bounds use conservative system-bar resources and need OEM/landscape calibration.

## Supplied deterministic and instrumentation coverage

- `CameraPipelineTest`: padded YUV rows, nonzero buffer positions, planar/interleaved chroma, all four quarter-turn orientations, odd-size downsampling, truncated buffers, invalid rotations, frame-interval rounding, callback-arrival timestamp mapping/age boundaries, bounded latency percentiles, packaged-model VIDEO inference/duplicate timestamps, actual 21-point detection from the shared official hand image with upright-y/mirrored-x shifts, idempotent asynchronous cleanup.
- `SessionIntegrationSmokeTest`: manifest privilege boundaries, Activity recreation/backgrounding without automatic camera start, denied camera without granting/revoking permission, preapproved overlay creation/reposition/removal, and discrete swipe duration after settings reload in isolated test preferences. The denial case skips when camera was already granted; the overlay case skips unless the user already approved overlays and API29 window inspection is available. Neither enables accessibility nor dispatches external gestures.
- Existing JUnit sources cover the pure session generation machine, recognition/golden replay, scroll mapping, dispatch freshness, one-operation backpressure, inversion and stale completion. Execution results must come from the migration runner's reports, not the existence of test files.

No Gradle or connected test run was performed by this reviewer while the main agent owned the shared Gradle execution. The main agent reported a successful integrated build/unit/lint run before the final metrics/model tests were added; the final validation report must establish those additions too. A connected API 30 Redmi device is environment availability, not evidence of camera, overlay, accessibility or third-party-app validation.

Final JUnit report files were independently read: `gesture/build/test-results/test/` contains47 passing cases (39golden parity,8safety); `app/build/test-results/testDebugUnitTest/` contains21 passing cases (5swipe configuration,9action coordinator,1scroll golden,2velocity mapper,1camera teardown,3session machine). All68 report zero failures/errors/skips. These JVM results do not establish camera capture, model-fixture inference or overlay/accessibility execution; those require instrumentation/device evidence.

The shared connected-run report was read during final source review: `app/build/outputs/androidTest-results/connected/debug/TEST-Redmi Note 8 Pro - 11-_app-.xml` contains zero executed tests and `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`; the exit-code artifact is1. Installation was blocked before instrumentation began. This is not a passing test result. Device installation/security settings must not be changed to bypass that decision; rerun after the user explicitly permits installation through the device UI.

## Required device lifecycle matrix

| Scenario | Required observation | Status at source review |
| --- | --- | --- |
| Rapid Start/Pause/Resume | One live camera/model; no old callback action | Source protections inspected; hardware unverified |
| Rapid Start/Stop/Start | Previous worker disposed before next session | Application-level teardown fix source-reviewed; hardware unverified |
| Stop during MediaPipe initialization | No later camera bind/overlay reappearance | closed checks inspected; hardware unverified |
| Camera privacy switch/revocation | Session errors safely; camera/resources release | CameraState observer + permission watchdog inspected; hardware unverified |
| Accessibility disconnect mid-stroke | Readiness false, pending result unavailable, no future actions | Source inspected; hardware unverified |
| Overlay permission revoke | Session terminates safely; no stale floating window | Watchdog source inspected; hardware unverified |
| Rotation/cutout/navigation changes | Upright landmark orientation and reachable overlay | Synthetic orientation tests supplied; actual display unverified |
| Activity recreation/backgrounding | Foreground service continues where permitted; UI never auto-starts capture | Smoke test supplied; continuous session unverified |
| Screen off/lock/unlock | Capture pauses, no locked-screen gesture, explicit visible resume | Broadcast + lock guards inspected; hardware unverified |
| Process kill/relaunch | Stopped state; no camera or accessibility activation without user Start | START_NOT_STICKY and no boot path inspected; hardware unverified |
| Tracking loss/reacquisition | No stale gesture; valid release/rearm required; no double action | Detector/unit sources inspected; end-to-end unverified |
| TikTok/Reels/Shorts manual and hand gestures | API result separately recorded from observed content change | Requires explicit user-approved permissions and manual device trials |

Run camera-free tests/build first. Only after affirmative user consent, grant each permission through its own onboarding screen and run the opt-in cases. Do not programmatically enable accessibility or overlays and do not claim skipped cases passed.

## Final main-agent execution evidence (2026-10-09)

Independent native build, unit tests, instrumentation APK and lint passed; JUnit reports total 68 passing tests. The Redmi API30 install was rejected twice before test startup. A temporary API36 emulator was started read-only with snapshot saving disabled; both APKs installed there, and the full AndroidJUnitRunner suite discovered 18 cases: 16 passed and 2 assumption skips. A targeted raw runner confirmed the accessibility dispatch and overlay approval cases skipped because special access was not enabled. No special permissions were granted by the test harness.

The 12 camera/model checks and four unprivileged session checks executed successfully, including packaged hand landmark detection, x/y shift conventions, image rotation/strides, input/completion timestamp mapping, rolling metrics, cleanup, manifest boundaries, Activity recreation/backgrounding, denied camera and isolated preference reload. Dashboard, Settings, Diagnostics and Help screenshots were actually inspected. These results do not establish live foreground camera continuation, granted overlay behavior, external swipe navigation, real-hand accuracy or physical thermal/battery performance.

## Primary API references checked

- [CameraX ImageAnalysis](https://developer.android.com/media/camera/camerax/analyze): analyzer ownership and KEEP_ONLY_LATEST behavior.
- [MediaPipe Hand Landmarker for Android](https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker/android): VIDEO mode, one-hand options and model setup.
- [ServiceInfo camera foreground type](https://developer.android.com/reference/android/content/pm/ServiceInfo#FOREGROUND_SERVICE_TYPE_CAMERA): introduced API 30. Reviewed service and manifest smoke test use the typed start/assertion only on API 30+.
