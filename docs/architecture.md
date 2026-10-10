# Native Android architecture

The Gradle project at the repository root builds `dev.autoscroll.nativeapp`. `:gesture` is Kotlin/JVM without Android dependencies; `:app` contains Compose Material 3, CameraX, MediaPipe, the foreground service, native overlay and accessibility adapter. The legacy project has been removed; this is the only application. There is no Flutter Gradle plugin, engine, platform channel, Dart runtime or Flutter Activity in the native project.

```text
CameraX ImageAnalysis (front, KEEP_ONLY_LATEST)
  -> one worker: validated YUV sampling / upright rotation
  -> MediaPipe HandLandmarker VIDEO (one hand, existing model)
  -> 21 indexed normalized domain landmarks (mirrored x)
  -> main-thread GestureCoordinator
  -> ScrollActionCoordinator (freshness / session / ownership / one operation)
  -> AccessibilityScrollAdapter
  -> public AccessibilityService.dispatchGesture
```

Gesture recognition is deterministic with explicit timestamps. Detectors never dispatch touches, know CameraX types or call Compose. The application owns a single SessionStore with StateFlow and immutable settings. LifecycleService owns external tracking; the Activity owns isolated practice. Compose observes state and sends commands. A lightweight WindowManager view keeps the overlay independent of Activity destruction. Constructor injection supplies camera callbacks and the scrolling executor/clock; no DI framework is required.

## State and lifecycle

`Stopped -> Starting -> Active`; `Active/Starting -> Paused`; `Paused/Error -> Starting` only through the visible Activity. Any state can stop or fail. Repeated commands are idempotent. Generation tokens invalidate old result callbacks and action completions. Application-owned camera-disposal completion serializes engines even across old/new service instances after Stop/Start. Pause closes the CameraX use case and model; a paused notification/overlay can remain. Stop releases resources, removes overlay/notification, and stops the service. No Activity reference is retained by the service.

Startup calls startForeground before camera initialization. Service is START_NOT_STICKY and has no boot receiver: process recreation starts stopped. Screen off pauses; unlock does not start capture. Permission checks and camera state errors stop dispatch/release capture on permission loss, privacy toggles, unavailable camera or accessibility disconnection. Resume opens the app and requires an explicit button press. Activity destruction alone does not stop an active service.

Android may terminate a service/process; this implementation does not promise persistence or automatic recovery. Runtime camera, foreground-service and OEM behavior require device validation. App settings changes pause an active session, then recreate the pipeline on explicit Resume.

## Isolated practice

Gestures → Practice safely reuses CameraController and GestureCoordinator with a
saved NativeSettings snapshot. PracticeFeedback maps decisions and velocity into
five local sample cards, a playback toggle and double-tap count. It never creates
ScrollActionCoordinator, AccessibilityScrollAdapter or an external executor.
Movement is a local demonstration, not target-feed calibration.

Practice alone requests a preview from CameraController. The existing bounded
worker copies the upright inference pixels before MediaPipe recycles its input,
then coalesces the image and landmarks in the same latest-result slot. Both expire
with the same freshness guard. Display bitmaps are GC-owned so Compose cannot
draw an explicitly recycled image; Stop, failure and the watchdog clear the
practice preview. External service sessions allocate no preview images.
The fixed camera panel mirrors the image once; landmarks already use the domain's
selfie coordinates and are fitted into the same letterbox. The saveable Show hand
tracking switch hides only the drawing. Camera and live feedback stay fixed,
side by side on wide screens; all other practice content scrolls below them.

Entry reserves practice ownership in SessionStore before awaiting work. If the
service exists, an explicit preparation command acknowledges only after
releaseSession has published its worker teardown. Practice waits for that
acknowledgement and cameraReleased; reading an old completed cameraReleased
immediately after publishing Paused would race the service command. Queued service
Start/manual commands cannot activate while practice owns the screen. Practice
entry also invalidates the request token carried by queued Home Start commands,
so rapid entry/exit cannot activate an older Start after the reservation ends.
Only a new explicit Home press supplies a current request token. Practice
publishes its own cameraReleased deferred on close before the reservation is
lifted, so explicit Home Start/Resume waits for that worker too.

Explicit Start practice requires only camera access and a resumed Activity.
Generation/visibility/permission checks reject late results. Back, Activity pause,
lock, background and recreation cancel initialization and close capture; return
stays stopped. The Activity keeps the settings draft in SettingsDraft and saves
main-screen tab state while practice is open. Nothing is applied by route changes.

## Timing and ownership

- Sensor capture time stays in the camera timestamp clock.
- Analyzer callback arrival uses elapsedRealtime and supplies MediaPipe VIDEO input time and domain recognition time. The old FrontCameraService stamped its Dart camera callback with a Stopwatch; TrackingController consumed that unchanged. Native code preserves arrival semantics instead of computing velocity from FPS or inference completion.
- MediaPipe input timestamps are strictly increasing. Inference completion is a distinct elapsedRealtime timestamp.
- Freshness includes preprocessing, inference and delivery delay. Results aged at least lostAfter become no-hand results. Tracking watchdog suppresses output when callbacks stall.

Each ImageProxy closes once in finally. Initialization, inference, and disposal share one bounded worker; no per-frame coroutine or unbounded request queue. Closed controllers coalesce/discard results and suppress stale callbacks. UI diagnostics update at most twice/sec, with inference metrics at most once/sec. Camera FPS means analyzer deliveries; CameraX's internal dropped-frame count is unavailable, and is not fabricated.

## Gesture arbitration and external actions

HYBRID and POSE include the native tap extension; CONTINUOUS and FLICK isolate their movement detectors. Pose/flick candidate, latch and cooldown ownership suppress competitors. Suppression resets continuous history. Native pose rearming uses elapsed time only, with a 200–3000ms Settings slider (default 450ms). After the interval, fresh confirmation permits another action, including held pointing/thumbs-up poses and direct up/down changes. Open hand (`NEUTRAL`) sends one pause tap per presentation and suppresses movement while held; its tap debounce survives tracking loss and clears on a recognized non-open pose. It never releases the timer. A vertically extended thumb with the other four fingers curled sends a double tap. Tracking loss discards candidates, while valid monotonic timestamps can expire the timer without a hand. The original open-hand/stable-pose rearm remains available with `tapGesturesEnabled=false` for the unchanged Dart compatibility fixtures.

Down pose/flick -> forward; up -> backward; inversion applies once and does not affect taps. Continuous motion uses the same velocity/sensitivity/EMA mapping, rendered as bounded 100ms proportional touchscreen strokes. This is an intentional adapter difference from Flutter's per-frame ListView ticker: small strokes can be below another app's touch threshold. Taps use the content center and 50ms stationary strokes; double tap is two sequential strokes in one dispatch, separated by up to 100ms within Android's double-tap timeout. No delayed backlog of actions is retained. Busy, stale, inactive, disconnected, locked, system and own-app targets reject all output, including taps. The overlay's actual bounds are excluded from the gesture path; center taps use a clear column if the widget occupies the center.

Every overlay press/drag suppresses recognition dispatch and resets recognition evidence. Floating controls contain status, Pause, Stop and Open app; resuming requires Home. Camera-free external tests in Settings → Advanced run only when Stopped: one explicit action after the task backgrounds. They are separate from safe local practice. Swipe API completion and observed feed navigation are separate results.

Accessibility consumes only window-state package names, disables window-content retrieval, and does not traverse nodes or read text. Physical touch outside the overlay cannot be monitored without additional intrusive capabilities. A dispatch can interrupt a finger touch; an already submitted short stroke can finish after pause/stop because dispatchGesture has no public cancellation operation.

## Model and resources

`app/src/main/assets/models/hand_landmarker.task` is included through the standard Android asset source directory with its original license. SHA-256: `fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1`. No extra model/download, INTERNET permission, frame recording, diagnostic disk traces, analytics or backend is added. Only preferences (settings, consent and overlay position) persist. Diagnostics remain in memory.

## Developer-facing boundaries

MainActivity is the Android entry point: it collects lifecycle-aware StateFlows,
handles permissions/intents and sends service commands. `presentation/HandScrollScreen`
receives immutable values and explicit callbacks; `SettingsPanel` owns rendering
of settings controls. Neither composable accesses the application, preferences or
Android services. Local tab/dialog state remains in Compose. `NativeSettings` is
separate from `SettingsStore`, which owns validation and persistence. The external
service/session lifetime and dispatch adapters remain separate from the
Activity-owned local practice controller.

Production models and instrumentation images use standard Android source sets.
Both JVM suites receive an explicit Gradle fixture path into `parity-fixtures`;
they never search parent directories for a legacy checkout. See the
[development guide](development.md) for ownership, review and validation rules.
