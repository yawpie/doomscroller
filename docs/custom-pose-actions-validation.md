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

## Home hand-presence diagnostics (4 November 2026)

With custom pose actions enabled, the service previously returned at the blocked
target guard before updating hand presence. Opening Scroll Vision could therefore
leave Home/Diagnostics at "not detected" despite fresh camera results. Scroll
hand-presence/loss diagnostics now update before that guard; recognition and
external actions remain gated by the existing target, freshness and session checks.

Validation: 182 JVM tests passed with no failures/skips; debug lint completed with
0 errors and 30 warnings; debug and instrumentation APKs built. The patched debug
APK was installed with an in-place update on Samsung SM-A165F / Android API 36.
All 13 `CameraPipelineTest` cases passed on that device, including actual inference
on the bundled test-only hand fixture, YUV conversion, rotation, timing and preview
freshness. These tests do not capture a live camera hand or dispatch external actions.
Saved settings and permission grants matched before/after installation and tests.
The session remains stopped until an explicit Home Start.

Raw result: ignored `artifacts/hand-diagnostics/camera-pipeline-samsung.txt`.
The user subsequently confirmed live-hand presence as detected in Home/Diagnostics.
That confirmation does not establish pose accuracy or target-app behavior.

## Custom pose classification and passive Diagnostics (4 November 2026)

The user reported that Home/Diagnostics detected a hand but showed UNKNOWN for
every pose, while the reported problem was limited to that screen. The blocked
target guard skipped pose classification. Fresh blocked-target samples now receive
passive custom-pose classification for Home/Diagnostics. This never advances
confirmation, consumes a discrete action or releases a target fence, as covered by
`CustomSessionRecognitionTest`. External dispatch stays guarded. Threshold
preferences and saved bindings are preserved.

The active custom classifier still used projected 2D joint angles on this branch.
Two added regressions reproduced rejected tilted pointing hands and a depth-folded
index incorrectly treated as straight. Joint angles now include x/y/z with aspect
correction. Direction and visibility guards remain screen-based; historical parity
fixtures are unchanged.
Coverage includes mirrored/aspect-varied hands and up → left → right transitions.

The real `thumb_up.jpg` fixture initially produced valid MediaPipe landmarks but
failed custom pose classification. Its IP joint measured about 157°, below the
160° entry threshold. Thumb classification retains the stricter base-joint entry
test and uses the existing 150° exit threshold at the IP joint. Synthetic cases
cover a mild bend and rejection of a strongly folded tip in both profiles.

Checks before the pointing retest: 186 JVM tests passed without failures/skips; lint 0 errors and 30
warnings; debug and instrumentation APK builds passed. All 14 `CameraPipelineTest`
cases passed on Samsung SM-A165F / API 36, including THUMBS_UP classification from
the actual model fixture in both custom profiles. The updated debug APK is installed;
saved settings and permission grants match the pre-install snapshot. The initial
fixture failure and final pass are preserved in ignored
`artifacts/hand-diagnostics/pose-classification-samsung.txt` and
`artifacts/hand-diagnostics/pose-classification-samsung-verified.txt`.

Live-hand pose accuracy, real-app response, sustained performance and the full UI
matrix remain unverified for this update. The release APK was not rebuilt for this
investigation. Explicit Home Start is required after installation/testing.

### Pointing retest and closed-thumb correction

The user confirmed that common poses became visible but all four pointing poses
still failed. A new canonical `pointing_up.jpg` test-only MediaPipe fixture also
failed at POINTING_UP on the Samsung before the correction. The classifier required
a thumb joint to bend below the curled-finger threshold even when the thumb rested
across the palm. Closed-thumb recognition now uses 3D tip-to-palm proximity, keeping
extended-thumb precedence, existing visibility checks and proximity hysteresis.
Regression cases cover a resting thumb across the palm, four directions, mirrors,
aspect ratios and rejection of a thumb extended toward the camera.

Final checks: 187 JVM tests passed with no failures/skips; lint 0 errors and 30
warnings; debug and instrumentation APKs built. All 15 `CameraPipelineTest` cases
passed on Samsung SM-A165F / API 36. The actual model/classifier now recognizes the
pointing image in all four rotated screen directions, alongside the thumbs-up
fixture in both profiles. No fixture image is packaged in the production APK; both
are in the instrumentation APK only. The patched debug APK is installed, with saved
settings and permission grants unchanged. The user subsequently confirmed that all
four pointing poses are now recognized live in Home/Diagnostics. This is a limited
qualitative confirmation; recognition rates, external-app response and sustained
performance remain unmeasured.

The initial failure is retained in ignored
`artifacts/hand-diagnostics/pointing-fixture-before-fix.txt`; final evidence is
`artifacts/hand-diagnostics/pointing-samsung-verified.txt`. Fixture results do not
establish real-hand accuracy or external-app acceptance.

## Combined poses and pointing autoscroll (4 November 2026)

Pointing directions and finger combinations now share one recognition set.
POINTING_UP is the single identity for point up/index finger up; the duplicate
INDEX_ONLY runtime identity and profile selector are removed. INDEX_MIDDLE requires
both fingers pointing up and defaults to continuous next. Middle-only and common
poses retain independent bindings. Canonical geometry still uses the verified
depth-aware finger/closed-thumb checks.

Autoscroll while pointing defaults off. Enabling it temporarily maps the four
pointing poses to held up/down/left/right actions; disabling it restores stored
bindings. Other poses are unaffected. Horizontal holds reuse the guarded executor,
100ms segments and provisional 300px/s speed. Practice integrates only the matching
local axis using elapsed time. Existing loss, freshness, generation, busy, target,
overlay, permission and explicit-resume guards remain in place.

Preference format 2 stores the toggle. Version 1 up/index conflicts follow the
previously active profile; an old NONE two-finger binding adopts continuous next.
Other explicit bindings are preserved. Retired keys remain stored but inactive,
and unsupported future formats remain preserved on unrelated updates. Tests cover
both old profiles, version 2 explicit NONE, corruption and future-version handling.

Validation: 192 JVM tests passed, zero failures/skips, including the unchanged
historical parity replay. The full development gate passed: debug lint has 0 errors
and 30 warnings; debug, unsigned release and instrumentation APKs built. Samsung
SM-A165F / API 36 passed all 23 selected instrumentation cases with no skips:
15 camera/model, 4 settings/migration and 4 pose UI tests. UI checks include the
shared up binding, toggle off/on restoration, Apply/recreation, draft protection,
and 200% text with light/dark component themes. The debug update is installed.

The physical-device run changed only the expected migration keys (format version,
two-finger default and the new toggle); unrelated preferences and permission grants
matched before/after. The user confirmed the requested behavior through Practice.
This is qualitative local-practice confirmation, not measured external-app or
sustained-use acceptance.

Raw device evidence: ignored `artifacts/combined-poses/samsung-instrumentation.txt`.
Additional emulator landscape checks exposed scroll-locator timing in the test
harness; the failing runs remain recorded separately from successful checks.
After replacing page-sized forward scrolling with shorter swipes and retrying
elements that moved out of view, all 4 pose UI tests passed on the API 36 emulator
in landscape, with zero skips. The same assertions cover large text/light/dark,
binding independence, draft recreation/Apply and the dirty-draft Practice guard.
Final emulator evidence: ignored
`artifacts/combined-poses/emulator-landscape-ui-short-scroll.txt`. Earlier locator
failures are preserved in the other emulator logs. The temporary read-only emulator
was closed after verification; personal-device rotation/font settings were not changed.

## Editing pointing bindings with autoscroll enabled (4 November 2026)

Pointing action buttons remain editable while Autoscroll while pointing is on.
The toggle now converts each selected one-shot scroll binding to its held equivalent,
instead of forcing a direction from the pose identity. Explicit held actions,
NONE, taps and Back retain their configured behavior. Defaults still follow the
four pointing directions. Turning autoscroll off restores one-shot scrolling for
the selected one-shot bindings. No preference format or stored key changes are needed.

The editor and picker descriptions display these effective semantics. UI regression
cases edit pointing actions with the toggle on, retain the toggle, and verify
draft recreation/Apply and large text in both themes. Native regressions cover
remapped scroll directions, hold versus discrete triggers, NONE ownership and
unchanged two-finger/common bindings. All 193 JVM tests passed without failures or
skips; debug lint completed with 0 errors and 30 warnings, and debug, unsigned
release and instrumentation APK builds passed. Historical parity fixtures are unchanged.

The updated debug APK is installed on Samsung SM-A165F / API 36. All 4 pose UI
tests passed with no skips, including edits made with autoscroll on and their
retention through recreation/Apply. The first UI run passed its assertions but
post-run comparison found the reversal preference changed. Its original value was
restored and the UI test class now snapshots fresh saved settings before each test
and restores them afterward. The final rerun passed with all saved preference
values and permission grants unchanged. Initial and final UI logs are preserved in
ignored `artifacts/pointing-edit-autoscroll/samsung-ui.txt` and
`artifacts/pointing-edit-autoscroll/samsung-ui-verified.txt`.
Emulator execution and observed gestures in external apps were not repeated for
this correction; the earlier combined-pose receipt does not establish those results.


## Timed full-swipe pointing autoscroll (4 November 2026)

This update supersedes the pointing short-segment behavior described above;
earlier device/UI receipts do not validate the new repeat behavior.

All four pointing scroll directions now emit discrete full-swipe events when
Autoscroll while pointing is on, or when an explicit pointing held binding is
selected. Each event uses the saved swipe distance and duration. Repeats wait for
the saved rearm delay, then fresh confirmation duration and minimum observations
(defaults: 450 ms, 180 ms, three observations). Tracking loss/gaps discard
confirmation. Target changes still require a valid release before reconfirmation.
Tap, Back, NONE, one-shot bindings with autoscroll off, and non-pointing held
segments keep their previous behavior. Preferences and parity goldens are unchanged.

Validation on this checkout:
- 195 JVM tests passed (88 gesture, 107 app), no failures or skips.
- Tests cover all four axes with the toggle on and explicit held bindings with it
  off, full requests at 1200/1900/2600 ms for 100 ms sampling, direction changes,
  duplicate/gapped samples, target fences, local Practice page steps and busy drops.
- Lint, debug APK, unsigned release APK and instrumentation APK compilation passed.
- Instrumentation execution, large-text/theme/rotation UI checks, real camera/feed
  response and sustained physical-device acceptance have not been rerun.
