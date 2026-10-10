# Scroll Vision UX validation

Run date: 2026-10-10 (Asia/Makassar). Branch: `codex/scroll-vision-ux`.

Implementation commits:

- `84953a8` — everyday navigation, setup, settings drafts and floating controls.
- `d39038e` — isolated live practice, camera handoff and stale Start protection.
- `4650db0` — understandable recovery from practice camera failures.

## Automated verification

The repository-required command passed on final code at `4650db0`:

```powershell
.\gradlew.bat :gesture:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
```

JUnit XML reports contain 58 gesture tests and 41 app tests: **99 tests, zero failures, errors or skips**. Unchanged gesture tests were up to date; app tests executed after the final changes. New focused coverage includes readiness, changed-only Apply, isolated group resets, draft retention, practice pose/repeat/mode/inversion feedback, camera reservation/teardown, queued Start invalidation and readable camera errors.

Debug, release and instrumentation APKs build successfully. Debug lint reports **zero errors and 27 warnings**, covering dependency updates, API/resource/preference notices and existing overlay touch/RTL notices. No dependencies were changed for this work.

Device execution used only `emulator-5554`, Pixel_7_API_34 (Android 14), with explicit `adb -s emulator-5554` commands. The connected personal phone was not used.

```powershell
adb -s emulator-5554 shell am instrument -w -r dev.autoscroll.nativeapp.test/androidx.test.runner.AndroidJUnitRunner
```

| Run | Result | Evidence under ignored `artifacts/` |
| --- | --- | --- |
| Full instrumentation suite after practice/Start guard | 21 passed, 3 prerequisite skips, zero failures; 101.221 s | `ux-full-instrumentation.txt` |
| Accessibility dispatch after reconnecting through Android settings during the test | 2 passed, zero skips/failures; swipes move the test surface and single/double taps are recognized; 7.392 s | `ux-dispatch-instrumentation.txt` |
| Denied-camera test with camera permission revoked on the emulator | 1 passed, zero skips/failures; 2.441 s | `ux-denied-camera-instrumentation.txt` |
| Final practice lifecycle tests after the error-message fix | 2 passed, zero skips/failures; 16.027 s | `ux-practice-final-instrumentation.txt` |

All 24 distinct instrumentation cases have a successful execution across these runs. The initial skips were two accessibility tests requiring reconnection after instrumentation restarts the process, and the denied-camera test requiring a denied permission. Camera permission was restored through Android settings after the targeted denial test.

## Emulator UI inspection

Screenshots were captured and visually inspected; hierarchy snapshots and read-only camera diagnostics supported the interactions.

| Area | Observed behavior |
| --- | --- |
| Home and navigation | Home, Gestures and Settings are accessible; Start/Pause/Resume is prominent, Stop secondary, readiness and compact guide visible. |
| Guided setup | Camera denial and blocked-permission recovery explain next steps. Accessibility disclosure stays scrollable at 200% text; Cancel does not record consent. Optional floating controls can be skipped without changing tuning. |
| Gesture cards | Illustrated cards separate Tap video from Pause tracking and explain mode-dependent movement and repeats. |
| Settings | Draft changes survive tab/configuration changes and leave persisted values unchanged. Apply saves once and pauses active tracking; Resume reacquires the camera. Discard and per-group reset behave as expected. Advanced tuning, diagnostics and manual tests are grouped. |
| Floating controls | Collapsed status and expanded Pause, Stop and Open app controls inspected. Pause releases the camera; opening the app leaves tracking paused until explicit Resume. |
| Practice | Start uses the camera with accessibility disabled. Local sample content and feedback appear. Backgrounding and Back release the camera; returning does not restart it. Instrumentation also covers recreation and immediate Stop/exit during initialization. |
| Dark mode and large text | Home/navigation, gesture cards, settings draft, consent disclosure and practice inspected in dark mode at 200% font scale. Text wraps and content/buttons remain reachable by scrolling. |

Device inspection found and resolved tab-label wrapping, settings draft loss on configuration changes, a blank practice feed viewport, and unreliable test traversal of merged Compose nodes. The practice viewport now shows the local sample title in both visual and instrumentation checks.

Useful screenshots include `ux-apply-paused.png`, `ux-floating-expanded.png`, `ux-floating-open-app.png`, `ux-fixed-large-dark.png`, `ux-disclosure-large-dark.png`, `ux-practice-final-light.png` and `ux-practice-final-large-dark-feed.png`.

At the end of testing no camera clients remained active. Font scale and night mode were returned to their original defaults.

## Source and review boundaries

The gesture module/fixtures, camera pipeline, accessibility dispatch, scrolling implementations, preference storage keys/defaults and dependencies remain unchanged from `8951849`. Practice reuses the bounded camera/recognition pipeline and updates local feedback only. Existing action safeguards remain in place; an already-submitted Android stroke can finish while a pause is being processed.

Independent specification and code-quality reviews approved Task 1. Task 2 specification review approved the camera recovery correction. Final overall quality review approved `4650db0` with no actionable findings.

## Remaining verification

- Physical-hand recognition/calibration, real social/video feed responses, sustained latency/battery/thermal behavior and other Android/OEM versions were not tested in this run.
- Numeric hardware camera failures were tested through focused message tests; their live recovery screen was not induced on the emulator.
- Timers, new gestures and performance presets remain deferred as requested.
