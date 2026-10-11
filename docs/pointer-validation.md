# Finger pointer implementation and validation

Date: 10 October 2026. Branch: `codex/finger-pointer`, based on `develop`.
The one-hand MVP implements tap, touch and hold, and drag. Physical acceptance is
still pending. The [original plan](plans/2026-10-10-finger-pointer.md) retains its
proposed physical gates; emulator and build results do not prove camera accuracy.

## Implemented behavior

- Scroll remains the saved default. Pointer uses the existing Apply/Discard draft
  and cannot also emit Scroll actions.
- The local model, 21 landmarks and camera worker are reused. Index tip 8, thumb
  tip 4 and palm geometry use upright aspect metadata and mirrored coordinates.
  No model, framework, network permission or module is added.
- Practice captures two stable corners using screen buttons. Calibration is
  in-memory, one-use for external Start and invalidated by session/screen changes.
  Practice hit tests and drag/drop stay local, without an accessibility adapter.
- The focused board shows nine 48dp targets, the held center, draggable square
  and drop zone together with Stop and Back. Measured board geometry is the hit
  geometry. Replacing or clearing calibration requires explicit board entry again.
- Pointer has separate accessibility consent and required cursor overlay access,
  even when the floating control widget is off. The small cursor is non-touchable
  and non-focusable, with shared content bounds and integer dispatch endpoints.
- Pinch confirmation freezes the starting point; movement uses a relative offset.
  A single contact continues across 80ms moving segments, with one latest pending
  point. Stationary hold sends no heartbeat strokes, repeated DOWN or extra drop
  tap. Release clears pending movement and ends at the last executed endpoint.
- Freshness, bounds/rotation, target, widget path, lock, session and busy guards
  remain. State listeners can abort before BEGIN/MOVE submission. Platform
  cancellation releases ownership; unconfirmed release blocks new actions.
- Recovery blocks Scroll and Pointer. The user touches the screen manually,
  acknowledges in Home, recalibrates Pointer and separately presses Start.

## Executed validation

Device: task-owned `emulator-5556`, Pixel 7 API 34 / Android 14 AVD. JDK 21,
checked-in Gradle wrapper, pinned dependencies and bundled model. The connected
personal device was not modified. Raw logs, screenshots and builds remain ignored
under `app/build`; they are local evidence, not committed product assets.

All six required Gradle tasks passed after the final code changes (18 seconds):
gesture/app JVM tests, lintDebug, debug/release assembly and instrumentation
compilation. XML reports contain **145 tests, zero failures/errors/skips**. Lint
reports zero errors and 30 existing warnings. `git diff --check` passed, and Scroll
parity fixtures are unchanged.

**41 unique instrumentation cases have an observed passing execution**, across
the combined run and targeted reruns below. This is not a claim that the earlier
combined invocation was entirely green.

| Suite | Observed evidence |
| --- | --- |
| CameraPipelineTest | 13 passes: conversion, rotation, throttling, worker and model fixtures; not live hand accuracy |
| SettingsStoreTest | 2 passes: old preferences/default Scroll and Pointer persistence/consent isolation |
| PointerCursorTest | 2 passes: center/flags/removal and input through the visible overlay into a different UID target |
| AccessibilityDispatchTest | 2 passes: observed external test-surface scrolling and single/double taps; not a real feed |
| PointerDispatchTest | 9 passes: one-contact tap, direct drag, long press then drag, release priority, busy rejection, widget/freshness/session/bounds guards and actual own-app window switch. Accepted mismatched continuation records DOWN then CANCEL |
| PracticeLifecycleTest | 3 passes with original assertions: local preview toggle, background/recreation and rapid initialization Stop/exit |
| PointerPracticeScreenTest | 2 passes: calibration control and focused board. All 11 target rectangles are wholly visible and 48dp; row/column pitch does not overlap. Stop and explicit board re-entry after reset are verified |
| PointerHomeScreenTest | 3 passes: manual recovery acknowledgement without automatic Start, cursor permission required with widget off, and actual draft/tab/recreation/Apply/Discard behavior |
| SessionIntegrationSmokeTest | 5 passes across executions. Four passed in the combined run; its denied-camera case skipped while permission was granted. An isolated AVD revoke/run/restore passed that case |

The initial 38-case combined run had 36 passes, one draft-fixture failure and one
camera-denial skip. The final five-case Pointer UI rerun passed all five with no
skip (21.562 seconds); the isolated camera-denial rerun passed (3.144 seconds).
Earlier lifecycle helper failures were fixed without weakening assertions.
Fresh window semantics and one evaluation of a successful click prevent stale or
duplicate actions. Radio-button assertions use checked semantics, and scrolling
reveals controls omitted from the current viewport.

Visual QA used actual screenshots and configuration checks: normal light portrait,
200% text in light portrait, and 200% text in dark landscape. The focused board
kept all 11 targets and Stop/Back visible; full Hold/Drag/Drop labels remained
readable. Large-text landscape originally left no space for Pointer controls;
bounding and scrolling its feedback fixed entry into the board while preserving
Scroll's preview layout. Actual Home was also inspected with large text in dark
portrait and landscape. Three Home tests passed at 200% in dark mode, including
actual rotation plus recreation of the unsaved draft. Display settings and camera
permission were restored after QA.

## Evidence boundaries and remaining physical gates

Domain decisions, UI fixtures and public Android dispatch tests do not measure
the complete camera-to-touch flow. Bounds/rotation metadata rejection is not a
rotation-during-tracked-drag trial. Guard abort sends terminal UP; a short accepted
contact can still activate its target. No replacement DOWN or tap is sent.
An accepted mismatched continuation proves platform cancellation. UiAutomation
input bypasses the accessibility input filter, so it does not prove a native
finger interruption cancels a held stroke. Lost-binder manual recovery remains
physical QA. Camera denial and missing-overlay readiness were tested; revocation
or disconnect during a live tracked pinch is still unverified.

The [device matrix](device-validation.md#pointer-acceptance) still requires:
25/27 target hits; 20 drag trials with at least 18 drops; a 10-second hold without
repeat; 30-second idle; release latency; result-to-render p95; 15-minute
thermal/battery/fatigue observations; left/right hands, lighting, navigation,
cutout, two real apps and OEM/API 26–29/30–33/34–36 coverage. No unavailable
condition is marked passed. Thresholds and smoothing remain experimental defaults.
Two hands, two-finger scroll, double click, PC control and saved calibration remain
future work.
