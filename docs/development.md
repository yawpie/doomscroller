# Development guide

Open the repository root in Android Studio. It is the complete Gradle project,
including both modules, documentation and CI configuration. Use the checked-in
wrapper and pinned dependencies, not a system Gradle installation.

## Ownership and change locations

| Concern | Location | Rules |
| --- | --- | --- |
| Landmark models, thresholds, smoothing, recognition | `gesture/src/main/kotlin/.../gesture` | Pure Kotlin; explicit timestamps; no Android, Compose or MediaPipe types. |
| Capture and image conversion | `app/src/main/java/.../camera` | Close every ImageProxy, keep only the latest frame, serialize teardown. |
| MediaPipe and metrics | `app/src/main/java/.../mediapipe` | One worker owns model lifetime; adapt results into domain landmarks. |
| Session state and lifecycle | `app/src/main/java/.../session` | Service owns resources; SessionStore publishes immutable StateFlows; generation tokens reject stale work. |
| Android entry point and permissions | `MainActivity.kt` | Collect state with lifecycle awareness; own permission launchers and Android intents. |
| Screen rendering | `presentation/HandScrollScreen.kt`, `SettingsPanel.kt` | Receive immutable data and callbacks; keep persistence, services and permissions outside composables. |
| Settings | `settings/NativeSettings.kt`, `SettingsStore.kt` | Store validated immutable snapshots; preserve preference names and defaults. |
| Action arbitration | `scrolling` | One in-flight operation, no backlog; freshness, session and inversion checks stay centralized. |
| Platform dispatch and overlay | `accessibility`, `overlay` | Keep target/lock/overlay guards and affirmative consent; do not retrieve screen content. |

The app already needs a service-owned state holder: an Activity/ViewModel cannot
own a camera session that continues over another app. Manual constructor injection
and the application container are sufficient for the current app.
Do not add a DI framework, use-case wrapper, or module per package without a real
ownership or reuse need. These boundaries follow Android's
[architecture recommendations](https://developer.android.com/topic/architecture/recommendations).

## Validation before a commit

From the repository root on Windows:

```powershell
.\gradlew.bat :gesture:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
```

On Unix use `sh ./gradlew` with the same tasks. CI runs that command on every push
and pull request. Release APKs are unsigned; signing is a separate distribution
step. Instrumentation compilation is not device execution.

On an explicitly selected test device/emulator:

```powershell
$env:ANDROID_SERIAL = '<test-device-serial>'
.\gradlew.bat :app:connectedDebugAndroidTest
```

The camera fixture is packaged only in the instrumentation APK. Tests requiring
preapproved accessibility/overlay access skip when permission is unavailable;
review skips, not just the green summary. Settings persistence checks restore the
original settings. Use an emulator when personal-device permissions must remain
untouched. Physical capture, target-feed response and sustained performance still
need the [device validation matrix](device-validation.md).

Keep [saved parity fixtures](../parity-fixtures/README.md) unchanged during
refactors. Add native cases for intentional new behavior. For UI changes, compare
Home, Gestures, Settings, every Advanced group and the accessibility disclosure.
Check default and large text, light/dark mode, setup denial/retry, optional overlay
skip, draft edits across tabs and rotation, Apply/Discard, and single-group resets.
Permission launchers and persistence remain in MainActivity; presentation receives
callbacks. The small Activity-owned SettingsDraft ViewModel retains unapplied
values across configuration changes without writing them to saved preferences.

Before committing, inspect `git diff --check` and ensure models, test images,
signing keys, SDK paths, build outputs and local logs have the correct ownership.
Only the bundled licensed model belongs in the production APK; test images and
goldens do not. Never commit `local.properties` or signing credentials.
