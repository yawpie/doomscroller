# Hand pose scrolling

Keep one hand visible in the upright front-camera preview. Extend the index
finger toward the bottom of the image for **Page Down**, or toward the top for
**Page Up**, with the middle, ring and pinky curled. The thumb is unrestricted.
Hold the pose for at least 180 ms and three distinct observations. One confirmed
pose moves the reading list by one viewport. Repetition depends on the selected
**Pose re-arm** setting; the default remains one-shot open-hand release.
The existing inversion switch reverses both directions.

Before another action, show an open hand with all four fingers extended for at
least 180 ms. The previous action must also be at least 450 ms old. Keep the open
hand visible until the diagnostic state returns to READY, then point again.
Changing directly between up and down stays locked. A missing or ambiguous hand
is UNKNOWN and cannot release the lock. This prevents an occlusion or tracking
failure from repeating a held gesture.

Alternatively select **Hold same pose** in **Pose re-arm**. A consistently
recognized pose rearms after `stablePoseDuration` (default 1000 ms); the screen
slider offers 300–3000 ms in 100 ms steps. The dwell starts at the confirmed
action. A changed recognized pose starts a new dwell, and unknown/missing
tracking, non-increasing timestamps or excessive sample gaps discard dwell
evidence without unlocking immediately. A new valid observation starts fresh
evidence after loss. Open-hand geometry is not required in this mode; an
unchanged pointing pose can repeatedly scroll.

Cooldown still applies in both modes. Re-arm itself emits no action: the next
pointing pose must pass the normal confirmation interval/count again. The repeat
interval therefore includes dwell, confirmation and actual sample cadence.
“Same pose” means the same recognized classification, within geometric
hysteresis, rather than identical landmark coordinates or an immobile palm.
Choosing another mode or duration stops scrolling and resets recognition while
keeping the camera stream running. Settings last for the current app session.

The **Pose only** switch disables flick and continuous actions for testing.
Combined mode retains slow palm-motion scrolling and flicks when a discrete
gesture does not own recognition. Sensitivity affects continuous scrolling;
pose scrolling always uses the viewport, including when flicks use a configured
fixed distance.

## Recognition and state

`HandPose` remains the existing landmark container. `RecognizedHandPose` describes
pointing up, pointing down, neutral or unknown. `HandPoseClassifier` validates
all 21 ordered, finite normalized landmarks and uses projected joint angles,
palm-relative finger geometry and the index MCP-to-tip direction. Native output
is already rotated upright, with Y increasing downward and only X mirrored.
Recognition uses the existing landmark result and adds no model or native work.

| Phase | Behavior |
| --- | --- |
| READY | A valid pointing pose begins confirmation. |
| CONFIRMING | Require consistent direction, duration and observation count. Invalid tracking, direction changes, stale timestamps or excessive gaps discard evidence. |
| LATCHED | Emit once; keep the lock across tracking loss. Open-hand mode waits for neutral; timed mode begins stable-pose evidence at the action. |
| WAITING_REARM | Require continuous valid neutral evidence in open-hand mode or an unchanged valid classification in timed mode, plus expired cooldown. Invalid samples discard evidence; changed poses restart timed evidence. Successful re-arm returns to READY. |

Duplicate or decreasing timestamps do not contribute to confirmation or release.
Sample gaps longer than 250 ms break temporal evidence. Tracking loss discards
an unconfirmed candidate, but preserves a confirmed lock. Explicit stop,
background cleanup, disposal or gesture settings changes reset recognition.
Settings changes invalidate pending inference results without restarting camera
capture. Timing uses actual monotonic callback timestamps, never configured FPS.

## Arbitration and scroll ownership

`GestureCoordinator` owns arbitration. Existing pose confirmation/lock has first
priority; an existing flick candidate/lock retains its ownership. Otherwise a
new pose candidate has priority over a new flick, followed by continuous motion.
Competing detectors cannot dispatch actions themselves. Pose suppression clears
the continuous-motion window and flick evidence so stale movement cannot leak
into a later action. The frame that relinquishes a discrete owner remains idle.

`TrackingController` dispatches mutually exclusive pose, flick or motion actions.
Pose actions call `ScrollService.scrollByPage`, sharing the flick animation and
its existing 220 ms duration, inversion and boundary clamping. Starting a page
action stops the continuous ticker and replaces an active animation. Suppressed
idle observations leave that animation running. Missing tracking, explicit stop,
settings changes and disposal cancel scrolling; manual dragging uses Flutter's
normal animation interruption. Inference never awaits the animation.

The existing diagnostics show current pose, phase, confirmation progress, last
pose action/timestamp and cooldown/re-arm guidance. Diagnostics only render state.

## Configuration and tuning

`HandPoseDetectionConfig` is injected by `handPoseConfigProvider`.
`gestureModeProvider` switches combined/pose-only operation. Live changes reset
gesture ownership and cancel scrolling. Disable pose detection through `enabled`
to retain the existing flick/continuous pipeline.

| Setting | Experimental default |
| --- | --- |
| confirmationDuration / minimumObservations | 180 ms / 3 |
| releaseDuration / cooldown | 180 ms / 450 ms |
| rearmMode / stablePoseDuration | openHand / 1000 ms |
| maximumSampleGap | 250 ms |
| minimumIndexAngle / exitIndexAngle | 160° / 150° |
| maximumCurledAngle / exitCurledAngle | 110° / 125° |
| minimumVerticalAlignment / exitVerticalAlignment | 0.85 / 0.70 |
| minimumFingerLengthRatio / minimumBoneLengthRatio | 0.35 / 0.03 of wrist-to-middle-MCP length |

Entry thresholds start a candidate. Exit thresholds only maintain its current
direction, reducing jitter without admitting the opposite direction. Palm-relative
minimum finger/bone lengths reject collapsed geometry and remain configurable.
No absolute pixel position or hand size is assumed.

Tune on the target phone: first inspect landmark alignment and poses in pose-only
mode, then adjust angle/alignment thresholds for observed false or missed events.
Change confirmation/release timing only after checking effective inference cadence.
Keep cooldown in both modes. Open-hand mode requires neutral release; timed mode
allows repeated actions while pointing. A shorter timed dwell repeats more often.

## Limits and physical Android validation

Projected 2D angles can become ambiguous with out-of-plane fingers, occlusion,
small/distant hands or overlapping landmarks. Such geometry stays UNKNOWN.
Moderate in-plane wrist rotation is supported within the vertical-alignment
threshold. The single-hand tracker does not establish persistent person/hand
identity: a clearly open reacquired hand can release an existing lock. This is
not biometric recognition.

Recognition performs bounded scalar calculations over 21 points, retains no
frames or landmark histories, and does not copy images. Native inference, camera
settings, worker count and frame-rate limits are unchanged. Automated synthetic
and widget tests validate logic, not camera accuracy or measured performance.

On a physical Android phone, validate both hands at different positions/sizes,
lighting and distances; up/down direction and selfie-preview alignment; long
holds; direct reversals; short/open-hand releases; occlusion and reacquisition;
manual drag, boundaries, inversion, background/resume and settings changes.
Repeat in combined mode with deliberate flicks and slow motion. Record false and
missed events, event latency and effective inference FPS, then profile sustained
CPU/RAM, battery and thermal behavior. Those measurements require actual hardware.

## Implementation files

Added:

- `lib/features/gesture/domain/pose_event.dart`
- `lib/features/gesture/application/hand_pose_classifier.dart`
- `lib/features/gesture/application/hand_pose_detector.dart`
- `test/hand_pose_detection_test.dart`
- `test/gesture_coordinator_pose_test.dart`
- `test/support/hand_pose_fixtures.dart`
- `docs/hand-pose-scrolling.md`

Updated:

- `lib/core/config.dart` and `lib/app/providers.dart`: pose configuration and live mode injection.
- `lib/features/gesture/application/gesture_coordinator.dart` and `flick_detector.dart`: arbitration and explicit flick ownership.
- `lib/features/auto_scroll/application/tracking_controller.dart` and `tracking_state.dart`: dispatch, diagnostics, settings/lifecycle resets and stale-result handling.
- `lib/features/auto_scroll/domain/scroll_service.dart` and `lib/features/auto_scroll/infrastructure/flutter_scroll_service.dart`: shared page action.
- `lib/features/auto_scroll/presentation/tracking_screen.dart`: diagnostics and pose-only control.
- `test/tracking_controller_test.dart` and `test/scroll_and_screen_test.dart`: controller, provider, lifecycle and mounted-list integration coverage.
- `test/flick_detection_test.dart`: make its existing 0.8 velocity boundary explicit, retaining the locally tuned 0.4 default.
- `README.md` and `docs/architecture.md`: usage and architecture notes.

Existing staged and unstaged changes were retained. Native code, model/assets,
dependencies, camera configuration and inference scheduling were not edited.

## Initial one-shot validation — 8 October 2026

Three specialized subagents implemented recognition, integration and tests with
separate file ownership. The main agent reviewed and integrated the changes,
corrected flick ownership after tracking loss, and ran final validation.

| Check | Result |
| --- | --- |
| Scoped `dart format` | 18 changed Dart files checked; 0 further formatting changes |
| `flutter analyze` | No issues found |
| `flutter test --reporter expanded` | 152 passed, including 77 added pose/integration cases |
| `git diff --check` | Passed |

Added tests comprise 45 classifier/state-machine cases, 8 coordinator cases,
14 controller cases and 10 widget/provider cases. They cover invalid/degenerate
geometry, scale/position/rotation/jitter, entry/exit hysteresis, temporal boundaries,
duplicates and reversed timestamps, tracking loss/reacquisition, neutral release
and cooldown, ownership and stale-window resets, pose-only configuration,
page directions/inversion/bounds, animation replacement/preservation, manual
dragging, settings changes, watchdogs and lifecycle/disposal.

Flutter/Dart startup and working-diff inspection required execution outside the
filesystem sandbox. No development environment or repository configuration was
modified to bypass that limitation. APK builds, native Android integration tests,
camera pose accuracy and physical-device performance were not re-run for this
Dart-only feature.

## Re-arm selection validation — 8 October 2026

The open-hand default and timed stable-pose alternative share the same state
machine and cooldown. The configuration provider is now writable through
`HandPoseConfigNotifier.configureRearm`; its existing listener resets ownership,
cancels scrolling and invalidates pending results without replacing the camera.

Thirteen additional tests cover timed boundary/reconfirmation, repeated holds,
pose changes, unknown/missing tracking, gaps, duplicate/reversed timestamps,
cooldown, explicit reset, preservation of detector tuning, invalid settings and
live dropdown/duration controls. The widget test switches back to open-hand mode
and verifies held poses stop repeating while the stream stays active.

Final checks: six changed Dart files formatted, `flutter analyze` reports no
issues, all 165 tests pass with the expanded reporter, and `git diff --check`
passes. Physical Android validation and performance measurements were not run.
