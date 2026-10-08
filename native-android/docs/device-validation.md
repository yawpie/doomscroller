# Physical-device validation

Do not treat compiler, synthetic landmark fixtures, or Android dispatch callbacks as proof that TikTok/Reels/Shorts moved. Record the device/API, model, image dimension, thresholds, light, distance and observed content change for each trial. Installed Flutter/native IDs differ so measurements can be collected on the same device without replacing the baseline.

## Build/install and prerequisites

Use the commands in ../README.md. Install the debug APK and open Hand Scroll Native. Finish each permission through the dashboard yourself; no special permission is automatically granted. First open a feed app, return to Hand Scroll, keep camera Stopped, and press Swipe forward/backward. The task backgrounds and executes one action after one second. Confirm the correct page change independently of the API result. If no feed is behind the task, the launcher is rejected.

Then Start from the visible dashboard, wait for Active, switch to the feed. Tap/drag the floating status bubble to expand/move it. Test pause (camera off), explicit Resume from the app, Stop (overlay/notification gone), each direction and inversion. Camera-free diagnostics require Stopped, so press Stop to clear a retained Error session first.

## Trial matrix

| Group | Trials / expected evidence |
| --- | --- |
| Point up |20 deliberate confirmations; one backward action per pose; no repeat while held 5 seconds |
| Point down |20 deliberate confirmations; one forward action per pose |
| Rearm |20 open-hand release/repoint cycles; direct up/down switching cannot rearm; tracking loss does not count as neutral |
| Flick |20 per direction; cooldown+settlement before another flick |
| Neutral/jitter |20 trials with no unintended discrete action |
| Continuous |stationary calibration, slow up/down, reversal, idle, loss and reacquisition; measure touch threshold in each target app |
| Camera |normal/low/bright light, near/far hand, partial visibility, fast and slow motion |
| Overlay |drag near edges/center, expand/collapse, long press, Stop during swipe; injected path must not touch overlay |
| Targets |TikTok, Instagram Reels, YouTube Shorts and one plain scrollable app; forward/backward verified independently |
| Lifecycle |20 rapidStart/Pause/Resume/Stop cycles, Stop during initialization/inference; only one camera/model |
| System |app switch, Activity recreation, rotation/cutout, gesture and button navigation, lock/unlock |
| Revocation |camera privacy toggle, camera/overlay revocation, accessibility disconnect mid-session; resources release, no future actions |
| Recovery |force-stop/relaunch -> Stopped; process kill -> no automatic camera restart; visible explicit restart |

At least a limited-performance device and a newer device should be tested when available. Cover Android26-29,30-33,34-36, including at least one vendor with aggressive battery controls. Never mark unavailable devices/API levels passed.

## Benchmark collection

Use the Diagnostics screen's delivered analyzer FPS, effective inference FPS, completed/skipped/stale counts and conversion+inference latency summary. Record frame-arrival-to-dispatch latency separately from observed page-change latency. Do not subtract sensor capture timestamps from elapsedRealtime without first verifying their clock source. CameraX hidden drops are not measured.

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

- Permission or privacy error: check the corresponding Android settings; Stop then explicitly Start while the dashboard is visible.
- Pause stops capture; use app Resume after unlock or permission recovery. OEM low-memory/battery rules may kill the process.
- Gesture is classified but action rejected: check accessibility connection, camera session state, target window, busy/freshness, overlay-interaction quiet period and screen lock. Recognition candidates discarded during overlay interaction must be reconfirmed afterwards.
- Swipe completes without page change: target app behavior, swipe distance/duration, feed placement or a touch-slop threshold can differ. Calibrate actual target; completion is not navigation proof.
- Manual interaction: ordinary off-overlay finger touches are not monitored. A submitted accessibility stroke can cancel finger input; pause before sensitive interaction. One in-flight stroke may finish after Stop.
- API 26-29 use conservative system-bar dimensions; OEM landscape/multiwindow navigation bounds need calibration. Gesture output is intended for the default display; external/secondary displays are not validated.

Record every test as PASS/FAIL/SKIPPED with an observation and evidence file. Preserve failures and unresolved target compatibility; never silently tune fixture expectations to match them.
