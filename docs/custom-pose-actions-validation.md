# Custom pose actions: implementation and validation receipt

Recorded: 1 November 2026 (Asia/Makassar).

Status: automated and emulator checks complete; physical acceptance remains pending. This receipt separates
synthetic/unit and emulator evidence from real-hand acceptance. It does not establish
physical accuracy, real-feed behavior, continuous-scroll calibration or readiness
for everyday production use.

## Scope and revisions

- `6ce19e2`: P1 contracts, settings and resolver.
- `6a6a65c`: P2 native pose recognition and trigger policy.
- `312ccb8`: P3 Settings, Gestures and local Practice.
- `4c1c525`: P4 external-session dispatch and safety guards.
- `c80ac69`: held-generation guard before executor submission and two scheduler tests.

The unchanged `parity-fixtures/gesture-goldens.json` remains the legacy baseline.
Custom actions default off. Profiles are exclusive, the three finger combinations
have independent bindings, and open hand/fist/thumbs-up are common. Back uses
Android's [public global action API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#performGlobalAction(int));
ACCEPTED is distinct from observed navigation. Continuous pose scrolling uses
one bounded 100ms swipe at a time and provisional 300 px/s; physical calibration
and touch-slop verification remain outstanding.

## Checks performed

| Layer | Result and scope |
| --- | --- |
| Final unit and build gate (`c80ac69`) | 173 unit tests passed, 0 failed/skipped; lint 0 errors and 30 warnings; debug, unsigned release and instrumentation APK builds passed. APK compilation is not instrumentation execution. |
| P4 Android platform execution (`4c1c525`) | Clean API 36 emulator run: 15 passed, 0 skipped (BackDispatch 1, AccessibilityDispatch 2, PointerDispatch 9, TapDescription 3). An isolated Back rerun passed, including an observed 30px/100ms test-list scroll. This uses a controlled surface, not a real feed or hand. |
| P3 Settings UI | Focused PoseActionsPanel tests passed on API 36 in dark portrait at 200% font and light landscape at 200% font: 3 tests per configuration. The focused PointerHome tests also passed (3 tests). |
| P5 integration / lifecycle / camera (`4c1c525`) | 29 cases: 28 passed and one conditional camera-denied case skipped because permission was already granted. On the dedicated emulator, a separate run after revoking camera permission passed that case; permission was restored. All 29 cases were therefore covered without a failure. |
| Physical capture and apps | SKIPPED: no hand-test device available. No confusion matrix, two real target-app checks, 15-minute thermal trial or OEM/API 26–35 coverage. |

P3 initially had compilation and UI test-harness failures; the issues were fixed and
focused reruns passed. P4's first combined platform run skipped Back and the first
scroll test while waiting for accessibility reconnection. After a reconnect attempted
during the later Pointer tests, that run reported recovery and nine failures. A clean process
and one reconnection at the readiness window produced the clean 15/15 result above;
test expectations were not weakened. Preserve these distinctions rather than
replacing the initial outcomes with the final pass.

Raw logs and captures are local ignored artifacts under `artifacts/custom-pose-actions`;
they are not committed. Physical hand accuracy, real target-app behavior, hold stop
latency, touch threshold at 300 px/s, thermal/battery and OEM/API coverage must be
recorded separately using the [physical matrix](device-validation.md). The
provisional speed is not a measured acceptance threshold.

## Horizontal pose actions (1 November 2026)

Added `SCROLL_LEFT` and `SCROLL_RIGHT` as discrete actions for every existing pose.
They preserve saved defaults and use the existing string-ID preference format.
Direction refers to content navigation, so the finger swipes in the opposite
direction. Saved swipe distance applies to content width; duration stays shared
with vertical swipes. Horizontal overlay avoidance selects a clear row or rejects
dispatch. Practice updates an independent local five-page indicator.

The JVM suites pass 177 tests with zero failures, errors or skips, including
horizontal coordinates/distance, overlay avoidance, custom direction with Reverse
enabled, busy/stale/pause guards and local recognition/release/edge behavior.
Legacy parity fixtures remain unchanged. Instrumentation cases now cover horizontal
Android paths, both picker choices, draft recreation/Apply and preference round-trip.
The full development gate passed: `:gesture:test :app:testDebugUnitTest
:app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest`.
Debug lint reports zero errors and 30 warnings. `git diff --check` also passes.
Those instrumented cases have not been executed for this change. Visual checks for
large text, themes, rotation and permission flows, real-hand recognition and actual
horizontal target-app response remain unverified; earlier emulator evidence above
does not validate this extension. No connected physical-device permissions were changed.


> Historical receipt: subsequent unified recognition replaces the default legacy pose path. See [unified pose validation](unified-pose-recognition-validation.md) for current evidence.
