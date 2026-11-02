# Unified pose recognition validation

The classifier now includes estimated landmark depth in joint-angle calculations;
direction and minimum visible bone checks remain screen-based. A synthetic rigidly
tilted hand exercises finger identity, up/left/right detector transitions, mirroring,
aspect ratios, and depth-related rejection. This is not a camera or physical-device
accuracy result. See the [MediaPipe landmark coordinate reference](https://developers.google.com/edge/api/mediapipe/java/com/google/mediapipe/tasks/components/containers/NormalizedLandmark)
for the approximate depth scale.

For this depth change, the full six-task gate succeeded in 2m 32s
(`artifacts/depth-pose-final-gate.txt`). The unfiltered `:gesture:test` suite was
then rerun against the final fixture and succeeded in 3s
(`artifacts/depth-pose-native-final.txt`). The combined unit results contain 167
tests, with no failures, errors or skips. No UI, emulator or physical-device tests
were rerun for this change; the historical results below are not current evidence.

This receipt covers the replacement of the fixed pose pipeline with the shared
canonical classifier and detector. The default preset and custom bindings now
use the same recognition path. Strict canonical geometry and discrete actions
once per appearance intentionally replace the previous preset repeat behavior.

## Automated checks

The following gates ran with JDK 17 and Android SDK 36:

| Revision | Result |
| --- | --- |
| Baseline `7d396f6` | Full gate: 177 unit tests, zero failures or skips. |
| Classifier consolidation `62235c1` | Full gate: 178 unit tests, zero failures or skips. |
| Reviewed unified runtime | Full gate: 163 unit tests across 26 XML reports, zero failures, errors or skips; Android lint: 30 warnings and zero errors. |

The reviewed runtime gate succeeded for `:gesture:test`,
`:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`,
`:app:assembleRelease` and `:app:assembleDebugAndroidTest`. Instrumentation APK
compilation is not device execution. XML results were copied under
`artifacts/unified-pose-recognition/reviewed/{app,gesture}`, and the gate log is
`artifacts/unified-pose-recognition/task2-reviewed-gate.txt`.

Before the runtime replacement, targeted tests confirmed that default Practice
and Session recognition did not publish a canonical pose and that a held default
pointing pose repeated. Before the review fixes, the direct disabled-binding
detector and settings-snapshot inversion regressions also failed as intended;
those runs are recorded in `quality-red-native.txt` and `quality-red-app.txt`
under the same artifacts directory. The reviewed gate passed after those fixes.

The historical JSON fixture remains byte-for-byte unchanged: SHA-256
`0A79027A3CD2C158B66193933B3B15E008E6BD9E0A1EF14A2202E29A9DD8FB5F`.
Native and app tests replay motion and flick projections for 26 cases without
legacy pose ownership; recognition replay uses all-NONE bindings to isolate
those projections. The other 13 pose-owning cases and legacy pose assertions
are retired because strict canonical classification and once-per-appearance
semantics intentionally differ. Native tests cover the new contracts; this is
not a claim of full historical pose parity. The lower test count reflects the
replacement of obsolete fixed-pose and timed-repeat cases.

The specification and quality rereviews and a final overall review approved the
reviewed implementation and the later instrumentation helper fixes. After the
last helper change, the full gate ran again successfully (`final-gate.txt`):
132 tasks, 8 executed and 124 up to date, in 14 seconds. Production code did not
change after the reviewed gate; its 163 unit results and lint result remain
applicable, with Gradle reusing up-to-date work.

## Emulator execution

The explicit test target was `emulator-5554`, Android_16, API 36, x86_64,
headless with a read-only image and SwiftShader software GPU
(`swiftshader_indirect`). No personal device was used. The latest status for
all 52 distinct instrumentation cases is PASS, compiled from the individual
runs rather than a single all-green batch. The deduplicated summary and latest
statuses are saved in `artifacts/unified-pose-recognition/instrumentation-summary.json`
and `instrumentation-latest-status.txt` in the same directory.

- `platform-tests.txt`: 17/17 passed. This includes the new default-off preset
  test, which holds synthetic pointing-down landmarks through the actual
  recognition, action coordinator and accessibility adapter to an external
  test surface, observing exactly one scroll request and forward movement. The
  run also covers Back, vertical scroll, single and double tap, horizontal
  stroke, and nine Pointer cases. These use a preapproved accessibility service;
  synthetic landmarks do not validate camera recognition accuracy.
- The initial 35-case integration run (`integration-initial-failed.txt`)
  recorded 30 passes, four UI helper failures and one conditional camera-granted
  skip. Its 35 cases covered CameraPipeline (13), Settings (3),
  PointerHome (3), PointerPractice (2), Overlay (2), Session (5), Practice (4)
  and PoseActionsPanel (3).
- Narrow fixes to `PoseActionsPanelTest` and `PracticeLifecycleTest` retained
  the assertions and timeouts. They reacquired the current root/target ancestor
  after an offscreen or stale click, selected the main scroller rather than the
  first feedback scroller after new pose feedback rows appeared, and revealed
  the toggle before checking it. `ui-lifecycle-retest.txt` passed all three
  panel cases and the awake Practice case, with three Practice cases still
  failing. After the scroller fix, `practice-final.txt` passed three of four;
  the remaining `cameraOnlyPracticeStopsOnBackgroundAndRecreationWithoutAutoRestart`
  passed in `practice-camera-final.txt` after the reveal fix. All four initial UI failures therefore passed on retest.
- `camera-denied.txt` passed 1/1 after camera permission was revoked and then
  restored on this emulator, covering the initial conditional skip. The
  deduplicated latest results contain no unresolved case.

UI checks passed 3/3 in dark portrait at 200% text (`ui-dark-large.txt`) and
3/3 in verified light landscape at 200% text
(`ui-verified-landscape-large.txt`). The earlier
`ui-light-landscape-large.txt` run remained portrait despite a rotation request
and is not landscape evidence. For the verified run the emulator used fixed-to-
user-rotation and user-rotation lock 1; `dumpsys` reported `mRotation=1`, and
the 1920×1080 screenshot `verified-landscape.png` was visually inspected.

## Physical validation still needed

These checks do not establish physical hand recognition accuracy, behavior in
real external feed apps, OEM-specific behavior, or 15-minute sustained
performance. The 300 px/s held-scroll speed is provisional. Record physical
trials separately using [device validation](device-validation.md).
