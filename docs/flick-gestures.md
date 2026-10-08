# Flick gestures

Camera frames still go through the existing rate cap and single native MediaPipe
worker. Dart receives the same timestamped, normalized 21-landmark result. A
`FlickDetector` reads the average of wrist and MCP landmarks 0, 5, 9, 13, 17 before
the existing EMA/windowed continuous-motion detector. No model, asset, dependency,
camera setting, or inference scheduling change is needed.

`GestureCoordinator` produces the existing `HandMotion` plus an optional
`FlickEvent`. `TrackingController` dispatches one action per result through
`ScrollService`. Riverpod provides `FlickDetectionConfig`, and `TrackingState`
exposes an event for the existing diagnostic label. Recognition has no widget or
MediaPipe dependency.

## Recognition and coordination

- Use actual elapsed callback timestamps for segment velocity and candidate mean
  velocity. These timestamps are monotonic camera-callback arrival times, not
  sensor exposure timestamps.
- A fast segment starts a candidate at its previous palm sample. Require at least
  three observations, enough vertical displacement, sufficient average speed,
  duration inside the configured inclusive bounds, and consistent direction.
- Direction consistency is absolute net vertical displacement divided by total
  absolute vertical travel. Reject strong reversals, stale/non-increasing
  timestamps, long sample gaps, and large palm XY discontinuities.
- Continuous motion is suppressed from candidate entry. The original continuous
  detector resets when suppression begins, preventing its old window from
  scrolling after a flick. Slow motion still uses its original smoothing,
  confirmation, calibration, and proportional mapping.
- A successful flick emits once. Detection stays locked until cooldown expires
  and the hand settles within a small radius at low speed. Failed candidates also
  settle before re-arming, avoiding repeated candidate/continuous oscillation.
- Missing tracking stops scrolling and discards candidate history. A previously
  triggered lock survives reacquisition so loss cannot bypass cooldown. Explicit
  lifecycle cleanup resets both detectors. A missing-result watchdog or an
  inference older than the tracking timeout stops scrolling and resets evidence.

The detector uses scalar state and bounded work; it retains no frame or landmark
history. A formal six-state framework is unnecessary: candidate, locked/re-arm,
and ordinary tracking branches have one deterministic owner.

## Actions and tuning

`ScrollConfig.flickAction` selects `FlickScrollAction.page` (default: one viewport)
or `FlickScrollAction.fastScroll` (default distance: 600 logical pixels).
`flickDuration` defaults to 220 ms. This application has a `ListView`, so a page
means a viewport step rather than a route or a `PageView` item. The existing
direction convention is preserved: down advances the offset, up goes back;
the inversion control applies to both actions.

The scroll adapter stops its continuous ticker before `animateTo`, replaces any
active animation, and clamps the target to the content extents. Suppressed idle
samples preserve the flick animation. Loss, explicit stop, settings changes,
and disposal cancel it. Manual dragging uses the normal Flutter scroll behavior
and can interrupt the animation. Inference never waits for UI animation.

| FlickDetectionConfig field | Experimental default |
| --- | --- |
| minimumVelocity | 0.8 normalized image heights/second |
| minimumDisplacement | 0.10 image heights |
| minimumDuration / maximumDuration | 80 / 350 ms |
| cooldown | 500 ms |
| directionConsistency | 0.75 |
| maximumSampleGap | 250 ms |
| maximumPositionJump | 0.25 normalized XY distance per sample |
| rearmVelocity / rearmDuration / rearmRadius | 0.15 heights/s / 150 ms / 0.012 |

Candidate release uses half the entry velocity to provide hysteresis. Tune
velocity and displacement first on the target device; increase them for false
positives, decrease them for missed deliberate flicks. Adjust jump/gap thresholds
carefully: allowing larger discontinuities improves capture of fast movements
but also admits more tracking errors. Keep re-arm slower than candidate release.
The existing tracking and continuous-motion configuration values are preserved,
including local changes already present before this feature.

## Performance and validation boundaries

Three observations span roughly 133 ms at 15 FPS or 167 ms at 12 FPS, plus native
inference latency. These are sampling estimates, not device measurements or
guaranteed detection times. Gestures occurring between observations can be missed;
effective inference rates below the cap also affect recognition. Position-jump
guards intentionally reject extremely fast movements with too much travel
between samples. A consistently displaced three-frame tracking error can still
look like a flick, and the existing single-hand pipeline does not establish a
persistent hand identity.
An aborted fast candidate also requires settling: a brief high-speed blip followed
by sustained medium-speed motion pauses continuous scrolling until re-armed.

Automated tests use synthetic timestamped landmarks and mounted scrollable
widgets. They cover slow/fast up/down, stationary hands, jitter, tracking jumps,
loss/reacquisition, duplicates, irregular intervals, cooldown/re-arm, reversals,
duration boundaries, animation replacement, boundaries, inversion, and manual
interaction. Physical-camera accuracy, false-positive rates, CPU/RAM, thermal
behavior, and sustained latency require profiling on real phones.

Next validation: collect deliberate slow/flick/jitter traces under different
lighting and camera distances, measure event latency and missed/false events,
then tune these defaults before changing capture or native memory allocation.

## Implementation report — 8 October 2026

Three specialized subagents investigated independently before edits. The
architecture agent then owned controller/state/provider integration; the gesture
agent owned detection and deterministic coordination; the testing/performance
agent owned synthetic, controller, and widget tests. The main agent consolidated
the design, implemented scroll/config/UI/documentation changes, reviewed
tracking-loss re-arm behavior, and ran the checks.

Created:

- `lib/features/gesture/domain/flick_event.dart`
- `lib/features/gesture/application/flick_detector.dart`
- `lib/features/gesture/application/gesture_coordinator.dart`
- `test/flick_detection_test.dart`
- `docs/flick-gestures.md`

Modified:

- `lib/core/config.dart` — added flick recognition/action configuration;
  preserved pre-existing tracking/motion changes.
- `lib/app/providers.dart` — flick configuration injection.
- `lib/features/auto_scroll/application/tracking_controller.dart` — mutually
  exclusive event dispatch, tracking-loss handling, and stale-inference rejection.
- `lib/features/auto_scroll/application/tracking_state.dart` — diagnostic event.
- `lib/features/auto_scroll/domain/scroll_service.dart` — flick action contract.
- `lib/features/auto_scroll/infrastructure/flutter_scroll_service.dart` —
  viewport/fixed-distance animation, cancellation, and boundary handling.
- `lib/features/auto_scroll/presentation/tracking_screen.dart` — flick status and
  usage guidance.
- `test/tracking_controller_test.dart`, `test/scroll_and_screen_test.dart` —
  updated adapter fake and integration/regression tests.
- `README.md` — usage and feature-documentation link.

Executed successfully on the final Dart implementation:

| Check | Result |
| --- | --- |
| `flutter analyze` | No issues found |
| `flutter test --reporter expanded` | 75 tests passed; 44 added for this feature |
| `git diff --check` | Passed |

The first analyzer run found two test issues while tests were being finalized;
the completed test configuration and collection syntax were corrected before
the successful analysis and test run. Flutter/Dart and Git diff needed execution
outside the filesystem sandbox because their sandboxed invocations failed;
no repository or machine configuration was changed to work around this.

The 30 new detector/coordinator tests, eight widget tests, and six controller
tests cover all 14 requested scenarios. This validation measures camera-free
behavior on the development host. Android builds, native device integration,
physical-phone performance, and camera gesture accuracy were not re-run.
