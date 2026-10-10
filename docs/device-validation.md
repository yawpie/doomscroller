# Physical-device validation

Do not treat compiler, synthetic landmark fixtures, or Android dispatch callbacks as proof that TikTok/Reels/Shorts moved. Record the device/API, model, image dimension, thresholds, light, distance and observed content change for each trial. The native application ID remains dev.autoscroll.nativeapp. Historical measurements must be labeled by revision and settings; the legacy source is available only in Git history.

## Build/install and prerequisites

Use the commands in ../README.md. Install the debug APK and open Scroll Vision. Finish each permission through Home's checklist yourself; no special permission is automatically granted. First open a feed app, return to Scroll Vision, keep tracking Stopped, and use Settings → Advanced → External action tests. The task backgrounds and executes one action after one second. Confirm the correct page change independently of the API result. If no feed is behind the task, the launcher is rejected.

Then Start from Home, wait for Tracking, switch to the feed. Tap/drag the floating status bubble to expand/move it. Test Pause (camera off), explicit Resume from Home, Stop (overlay/notification gone), each direction and inversion. External tests require Stopped, so press Stop to clear a retained Error session first. Diagnostics are grouped under Advanced.

Before external trials, use Gestures → Practice safely with camera access only.
Confirm no accessibility/overlay requirement, explicit Start practice, live hand/no
hand feedback, local navigation/playback/double taps and repeats in each saved mode
and inversion. Verify entry pauses normal tracking and cancels pending tests;
Stop/Back/background/lock/rotation release capture, discard late callbacks and
never restart automatically. Normal tracking needs explicit Home Start/Resume.
Local feedback proves neither physical recognition accuracy nor another feed's
response; record those as separate trials.

## Trial matrix

| Group | Trials / expected evidence |
| --- | --- |
| Point up |20 deliberate confirmations; backward actions repeat only after the configured delay and fresh confirmation while held 5 seconds |
| Point down |20 deliberate confirmations; forward actions repeat only after the configured delay and fresh confirmation |
| Rearm |20 repetitions using time only, including held poses, direct up/down changes and no hand during the delay; open/closed hands cannot shorten the interval; reacquisition needs fresh confirmation |
| Rearm settings |Move the 200–3000ms slider, compare action intervals including confirmation time, relaunch and verify the saved duration |
| Open hand |20 confirmations per hand in HYBRID/POSE; one center single tap; no repeat while held 5 seconds; independently observe pause/play toggle in supporting feeds |
| Thumbs-up |20 confirmations per hand; center double taps repeat only after the delay and fresh confirmation; test wrist rotation, curled thumb, partial visibility and non-curled fingers |
| Flick |20 per direction; cooldown+settlement before another flick |
| Unknown/jitter |20 malformed/partial/noisy pose trials with no unintended discrete action; open hand is now an intentional single-tap pose |
| Continuous |stationary calibration, slow up/down, reversal, idle, loss and reacquisition; measure touch threshold in each target app |
| Camera |normal/low/bright light, near/far hand, partial visibility, fast and slow motion |
| Overlay |drag near edges/center, expand/collapse, long press, Stop during swipe; injected path must not touch overlay |
| Targets |TikTok, Instagram Reels, YouTube Shorts and one plain scrollable app; forward/backward verified independently |
| Lifecycle |20 rapidStart/Pause/Resume/Stop cycles, Stop during initialization/inference; only one camera/model |
| System |app switch, Activity recreation, rotation/cutout, gesture and button navigation, lock/unlock |
| Revocation |camera privacy toggle, camera/overlay revocation, accessibility disconnect mid-session; resources release, no future actions |
| Recovery |force-stop/relaunch -> Stopped; process kill -> no automatic camera restart; visible explicit restart |

At least a limited-performance device and a newer device should be tested when available. Cover Android26-29,30-33,34-36, including at least one vendor with aggressive battery controls. Never mark unavailable devices/API levels passed.

## Pointer acceptance

Select Pointer in the settings draft and Apply. Practice needs only camera access.
Capture two stable, separated-finger corners with the screen buttons; Open practice
board keeps every 48dp target and Stop reachable. Use calibration stops practice;
start external tracking separately from Home after Pointer consent, accessibility
connection and required cursor overlay access, including with the widget disabled.

The [pointer validation receipt](pointer-validation.md) separates deterministic
logic and API 34 emulator evidence from the following physical trials. None of the
numbers below is a measured success rate until a device observation is recorded.

| Trial | Physical evidence required |
| --- | --- |
| Nine targets | Three taps per 48dp target after calibration; proposed minimum 25/27 hits, no double activation from one pinch |
| Idle | 30 seconds of pointing without pinch; no unintended touch |
| Hold | 10 seconds pinched and stationary; one DOWN, no repeated dispatch, one release; distinguish target's own repeat behavior |
| Long press |Target's long-press response plus a short pinch control; measure release-to-UP latency separately |
| Drag, primary gate | 20 drops per local practice, external test activity and compatible real app; proposed minimum 18/20 successes, horizontal/vertical/diagonal/edge directions, direct and hold-then-drag, no UP between segments or added tap after drop |
| Interruptions |Loss, gap, target-app change, Pause/Stop, widget interaction, lock, rotation, bounds change, accessibility disconnect and camera/overlay revocation while held; observe terminal event/contact outcome and hidden cursor |
| Recovery |If release is unconfirmed, new Scroll/Pointer actions blocked; physical screen touch then explicit Home acknowledgement, fresh calibration and separate Start; no automatic restart |
| Tracking/performance | Actual inference FPS, stationary jitter, position error, losses, result-to-render p95 (proposed ≤100ms, not camera-to-screen latency), separate end-to-end observation |
| Sustained use | 15 minutes; thermal/battery/FPS changes and hand fatigue, without production frame recording or upload |
| Conditions | Left/right hand, good/dim light, distance/occlusion, portrait/landscape/cutout, gesture/three-button navigation, at least two real target apps; API 26–29/30–33/34–36 and OEM variants when available |

A guard abort requests terminal UP at the last executed point. An accepted short
touch may still activate the target; no new replacement tap is sent. Long-press
effects cannot generally be undone. After binder loss Android has no public cancel
operation, so a manual screen touch can be needed before explicit recovery.

## Benchmark collection

Use Settings → Advanced → Diagnostics for delivered analyzer FPS, effective inference FPS, completed/skipped/stale counts and conversion+inference latency summary. Record frame-arrival-to-dispatch latency separately from observed page-change latency. Do not subtract sensor capture timestamps from elapsedRealtime without first verifying their clock source. CameraX hidden drops are not measured.

Collect a 5-minute steady session and 20-minute thermal/battery session after warm-up, with no per-frame production logging. Record CPU, memory/PSS, thermal status, battery start/end/elapsed using Android Studio Profiler and read-only device diagnostics. For example:

```powershell
adb shell dumpsys meminfo dev.autoscroll.nativeapp
adb shell dumpsys cpuinfo
adb shell dumpsys thermalservice
adb shell dumpsys battery
```

Summarize inference average/P50/P95, effective FPS, tracking-loss rate, input-to-dispatch and observed navigation latency, CPU, memory growth, thermal behavior and battery consumption. Rolling latency summaries describe the recent bounded 120frames, not the entire session; sample at a consistent interval or use a controlled benchmark harness for full-run percentiles. Record missing measurements as unmeasured.

Only compare Flutter/native runs on the same device, model, dimension, settings, lighting and duration. Do not infer a performance gain from removing Flutter or passing tests.

## Troubleshooting / limits

- Permission or privacy error: check the corresponding Android settings; Stop then explicitly Start from Home, or Start practice in practice.
- Pause stops capture; use app Resume after unlock or permission recovery. OEM low-memory/battery rules may kill the process.
- Gesture is classified but action rejected: check accessibility connection, camera session state, target window, busy/freshness, overlay-interaction quiet period and screen lock. Recognition candidates discarded during overlay interaction must be reconfirmed afterwards.
- Swipe completes without page change: target app behavior, swipe distance/duration, feed placement or a touch-slop threshold can differ. Calibrate actual target; completion is not navigation proof.
- Manual interaction: ordinary off-overlay finger touches are not monitored. A submitted accessibility stroke can cancel finger input; pause before sensitive interaction. One in-flight stroke may finish after Stop.
- API 26-29 use conservative system-bar dimensions; OEM landscape/multiwindow navigation bounds need calibration. Gesture output is intended for the default display; external/secondary displays are not validated.

Record every test as PASS/FAIL/SKIPPED with an observation and evidence file. Preserve failures and unresolved target compatibility; never silently tune fixture expectations to match them.
