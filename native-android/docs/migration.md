# Native migration report — 2026-10-09

## Status

Native implementation is present and independently buildable. Camera/service/overlay/accessibility/Compose integration is implemented; recognition parity, unit checks, native build and lint pass. Physical acceptance and release readiness are separate gates. The legacy Flutter variant has been removed at the user's request. Historical source remains in Git; native fixtures and the licensed model are self-contained. Device acceptance remains pending and does not block source retirement.

## Native-only completion verification — 2026-10-09

| Check | Current result |
| --- | --- |
| JVM regression suites | 83 passed: 58 gesture, 25 app; no failures/errors/skips. Saved 39 cases / 418 samples unchanged. |
| Android lint and builds | `:app:lintDebug`, debug APK, unsigned release APK and instrumentation APK all pass after legacy removal. |
| API 34 emulator instrumentation | 22 discovered: 19 passed, 3 assumption skips, no failures. Real bundled MediaPipe/model-fixture inference, settings persistence, Activity recreation and camera-free lifecycle checks executed. |
| Permission-dependent skips | Two external accessibility dispatch tests and the preapproved overlay test skipped because access was not enabled. No phone permissions were changed. |
| UI preservation | Dashboard, Settings, Diagnostics and Help initial viewports have zero changed app-area pixels against the pre-refactor native APK at 1080×2400; system bars excluded. Accessibility labels, bounds and enabled/selected states also match. All literal UI labels remain unchanged. |
| Asset preservation | Model, instrumentation JPEG and golden JSON match their pre-retirement SHA-256 values. Both production APKs contain the original model, no Flutter runtime/packages and no instrumentation image/goldens. |
| Manifest/privacy | Debug and release merged manifests have no network or microphone permission. Application ID and preference keys remain unchanged. |
| Repository/tooling | No Dart files, package manifests, legacy wrapper or SDK metadata remain outside Git history. Native-only CI uses pinned action commits and the wrapper's official distribution checksum. Golden files are declared Gradle test inputs. |
| Developer navigation | Local documentation links resolve; screen rendering and settings models have explicit ownership in the development guide. |

Current emulator evidence is in ignored `artifacts/instrumentation.log` and
`artifacts/ui-before`, `artifacts/ui-after`; JVM/lint reports remain under module
build directories. CI is configured but has not run on GitHub; no commits were
pushed. Live physical camera/feed behavior, sustained performance, the full
permission/lifecycle matrix and release signing remain pending.

## Architecture and migration

- `:gesture`: pure Kotlin/JVM models, config, EMA/windowed continuous detector, flick detector, pose geometry/state machine, deterministic coordinator and shared golden regression tests.
- `:app`: Android Compose Material3 dashboard/settings/diagnostics/onboarding/help; CameraX front-camera analysis, MediaPipe worker, LifecycleService, application-owned StateFlow session, WindowManager controls, native accessibility adapter and centralized action coordinator.
- Constructor injection supplies domain detectors, executor and clocks. Two modules enforce the useful framework boundary without a module per package or DI framework.
- Camera/session state belongs to the foreground service and application store, not Compose. One worker and one authoritative session; generation guards and shared teardown completion cover stale callbacks and rapid service replacement.
- The former Flutter app/channels are retained only in Git history. The native application ID, preference keys, algorithms and UI are preserved. Licensed model, test image and saved goldens now live entirely inside native-android.

The [inventory](migration-inventory.md) maps old responsibilities to current source. During the initial migration, separate implementation owners covered gesture, camera/inference, accessibility and application integration; the camera owner independently reviewed service/UI/action boundaries. That historical review is retained in the [QA report](qa-review.md). Current maintenance boundaries are in the [development guide](development.md).

## Implemented functionality

| Component | Result |
| --- | --- |
| Camera | Front CameraX ImageAnalysis, bounded KEEP_ONLY_LATEST, configurable resolution/FPS/dimension, stride-aware sampling, actual display rotation, finally-close ImageProxy |
| Inference | Existing 0.10.35 MediaPipe/model, one VIDEO worker, strict input timestamps, 21 normalized indexed landmarks, existing mirroring/handedness convention, asynchronous deterministic release |
| Recognition | Continuous/flick/pose/hybrid modes; actual Dart thresholds, palm average, EMA, window confirmation, cooldown, tracking loss, hysteresis/neutral rearm and exclusive ownership |
| Session | Stopped/Starting/Active/Paused/Error, StateFlow, explicit visible Start/Resume, pause camera release, stop cleanup, notification controls, screen-off pause, permission/error watchdog, stale callback guards |
| Overlay | Optional draggable small status bubble, expansion, Pause/Resume/Stop/Settings and explicit swipe tests, persisted/clamped position, configuration reposition, independent native view lifecycle |
| External scroll | Public accessibility gestures, forward/backward/inversion, configurable distance/duration, proportional continuous strokes, freshness, no action queue, one operation, package/lock/overlay guards |
| Manual validation | Camera-free dashboard test after task backgrounds; overlay explicit tests suppress classification until action completes. API result remains distinct from observed target content |
| Diagnostics | Throttled FPS/counts, last/rolling average/P50/P95 inference+conversion latency, tracking loss, pose/flick/state, action/rejection and arrival-to-dispatch latency |
| Privacy | On-device model; no recording, uploaded data, node/text scraping, analytics, accounts or backend. Transitive INTERNET/ACCESS_NETWORK_STATE permissions explicitly removed from merged manifest |

## Behavior parity and differences

39 deterministic Dart-generated golden cases contain 418 samples. The pure Kotlin replay checks exact event count/type/direction/time, detector state/ownership/suppression, and floating intermediates at 1e-9 tolerance. Separate action-layer tests replay all 39 cases for mapping/inversion. No Kotlin-generated expectations replace Dart results.

Original Dart acquisition callback timestamps are preserved as native analyzer-arrival elapsed timestamps. Sensor exposure/input/completion are recorded separately. Orientation follows the display instead of Flutter's portrait lock. The continuous detector/mapping is ported, while its output adapter changes from ListView's ticker to 100ms bounded touch strokes. Native pause/lock releases camera and explicit visible Resume replaces automatic foreground resumption. The baseline migration used open-hand rearm; the [native tap extension](tap-gestures.md) now reserves open hand for one pause tap per presentation and maps thumbs-up to double tap. Native rearming uses only a configurable elapsed-time delay and fresh confirmation, with no open/closed-hand requirement. Original rearm behavior remains a tested pure-domain compatibility option with taps disabled. See [parity guide](gesture-parity.md).

## Prior migration validation evidence

| Check | Executed result |
| --- | --- |
| Initial Flutter working tree/revision | Clean; 582d9086ca0dd3f3d3036c500060daa4f2cc7c60 |
| Original Flutter unit/widget baseline |165 passed |
| Final flutter analyze |No issues |
| Final Flutter suite incl saved golden verification |166 passed |
| Gesture JUnit |47 passed: 39 goldens+8 safety;0 failures/errors/skips |
| App JUnit |21 passed: 17 accessibility/mapping/coordinator+3 session state+1 teardown;0 failures/errors/skips |
| :app:assembleDebug |Passed; native APK generated |
| :app:lintDebug |Passed after correcting actual errors; informational pinned-dependency/API warnings remain |
| Instrumentation APK compilation |Passed; camera/model/session/external-surface tests supplied |
| First connectedDebugAndroidTest attempt |0 tests executed; Redmi Note8Pro Android 11/API 30 rejected installation, INSTALL_FAILED_USER_RESTRICTED / Install canceled by user |
| Retry after user agrees to allow installation |Again 0 tests executed; same INSTALL_FAILED_USER_RESTRICTED rejection before test startup |
| API 36 emulator instrumentation |18 discovered: 16 passed, 2 skipped (accessibility dispatch and overlay approval not enabled); 0 failures. Run with adb am instrument on temporary read-only/no-snapshot emulator |
| Skip verification |Targeted raw runner output confirms both assumption skips with status -4; no permission settings changed |
| Native UI |Dashboard, Settings, Diagnostics and Help rendered and screenshots visually inspected at 1080x1920; stopped state/permission onboarding and configuration/status/help layouts observed |
| Native APK packaging |ZIP/DEX inspection found 0 Flutter assets/libs/packages; original model SHA256 retained. Final merged manifest has no network permissions |
| Diff scope/whitespace |git diff --check passed; original production source retained |

The prior migration recorded these ignored local logs (some legacy logs have since been removed): migration-flutter-analyze-final.log, migration-flutter-tests.log (baseline), migration-flutter-tests-final.log; native-validation-complete.log (all nonconnected Gradle tasks passed), native-validation-approved.log and native-validation-device.log (phone installation rejected), native-instrumentation-emulator.log (full emulator suite), and native-instrumentation-skips.log (two assumption skips with raw status). Generated reports are under `gesture/build/reports/tests`, `app/build/reports/tests/testDebugUnitTest`, `app/build/reports/lint-results-debug.html`, and `app/build/reports/androidTests/connected/debug`. The latter connected report describes the phone installation rejection; it must not be confused with the separate successful adb emulator runner log. Dashboard screenshot is in `app/build/reports/ui/dashboard.png`. Build outputs/reports are local and ignored by Git; rerun commands from [README](../README.md).

## Performance

No sustained camera benchmark, thermal/battery run, accuracy trial or Flutter/native performance improvement has been measured. Bounded diagnostic summaries are implemented; synthetic model tests do not establish camera throughput. Inference summary covers recent 120 completed conversion+inference frames; arrival-to-dispatch includes that work, and excludes sensor exposure latency. CameraX internal drop counts cannot be directly measured and are not fabricated.

## Known limits and acceptance still required

1. Physical camera alignment/tracking, pose/flick accuracy and target navigation must be validated in TikTok, Reels, Shorts and a plain scroll surface. Swipes can complete without an observed page change; small continuous strokes may be below target touch slop.
2. Only user-approved Android permissions enable live capture/overlay/accessibility. Android 14+ camera foreground-service activation requires a legal visible path. OEM battery/privacy/camera rules and process termination can interrupt operation. Process death restarts stopped, not active.
3. Already submitted accessibility strokes cannot be cancelled by the public API and may finish after pause/stop. Off-overlay finger touches are not passively monitored; dispatched strokes may cancel them. System/own-app screens and intersecting overlay paths are guarded.
4. API 26-29 system-bar geometry is conservative; landscape, cutouts, gesture navigation, multiwindow and secondary displays need physical calibration. Default-display operation is implemented, secondary displays are unverified.
5. Instrumentation test source/compilation is not execution proof. Conditional accessibility/overlay tests skip unless access was already approved; no secure settings or device restrictions are bypassed. Actual camera session/service/notification permission-loss and rapid-command stress remains required even if camera-free smoke tests pass.
6. Release signing, Play Accessibility/FGS declarations, broader API/device matrix and distribution review remain unfinished. This is a debug/test deliverable, not a release-ready or fully physically validated migration.

## Next acceptance steps

1. Complete manual permission onboarding and camera-free forward/backward validation; record dispatch separately from observed content change.
2. Execute the [device trial matrix](device-validation.md), including20 pointing trials per direction, 20 flicks per direction, 20 neutral/jitter trials, timed pose repetition, open-hand tap debounce, delay persistence, tracking loss, rotation and rapid lifecycle commands.
3. Repeat on a newer device/API 34+ and a limited-performance device with equivalent settings. Record actual inference/gesture latency, CPU/memory,20 minute thermal/battery use and target compatibility.
4. Resolve measured failures without rewriting goldens to hide behavior changes. Legacy source retirement is complete; continue native acceptance with preserved behavioral fixtures/history.
5. Prepare signed release and required store disclosures/declarations only after device acceptance.
