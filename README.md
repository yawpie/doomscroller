# Scroll Vision

Kotlin Android application with Compose Material 3, front CameraX/MediaPipe
tracking, pose/flick/continuous recognition, foreground camera service, draggable
controls and user-requested accessibility swipes and taps. The repository root is
the complete Android Studio project; no Flutter or Dart SDK is required.

Two modules: `gesture` contains Android-free Kotlin logic; `app` supplies platform
adapters, service/session state, persistence and UI. Model/license are in
`app/src/main/assets/models`; test images in `app/src/androidTest/assets`; saved
behavior fixtures in `parity-fixtures`. No asset path depends on a sibling project.

## Build and run

Use JDK 17+ (locally tested with JDK 21), Android SDK 36 / Build Tools 36.0.0,
accepted SDK licenses and network/cached pinned dependencies. Minimum Android
version is 8.0 / API 26. Open this directory in Android Studio.

Create ignored `local.properties` with your SDK location, for example
`sdk.dir=C\:/Users/your-name/AppData/Local/Android/sdk`. Set JAVA_HOME in your
terminal if needed; no machine-wide configuration is required.

From this directory on Windows:

```powershell
.\gradlew.bat :gesture:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
adb -s <test-device-serial> install -r app\build\outputs\apk\debug\app-debug.apk
```

On Unix use `sh ./gradlew` with the same tasks. For instrumentation on a selected
test device, set `ANDROID_SERIAL` and run `:app:connectedDebugAndroidTest`.
Conditional permission tests may skip; check the report. The application ID
`dev.autoscroll.nativeapp` and existing preference keys are preserved. Debug
signing is for development; the release APK is unsigned.

## Controls

Home shows readiness and one Start / Pause / Resume control, with Stop below it.
Use its permission checklist to grant camera access and read the affirmative
accessibility disclosure, then enable Scroll Vision gestures in Android Settings.
Camera denial explains retry or App info access. Floating controls and notifications
are optional; Skip floating controls saves the existing overlay preference as off.
Start on Home and wait for Tracking before switching to a feed.

HYBRID/POSE: point down/up to navigate, show an open hand for one single tap per
presentation, or show thumbs-up for double tap. Pointing/thumbs-up repeat after the
configured rearm delay and fresh confirmation. CONTINUOUS/FLICK preserve their
movement controls. The illustrated cards in Gestures explain the current mode and
repeats. Tap video sends a center tap; Pause tracking releases the camera.
Gestures → Practice safely uses saved recognition settings with a local sample
feed and live feedback. It needs camera access only. Start practice is explicit;
entry pauses external tracking and cancels pending manual tests. Leaving, locking,
backgrounding or recreation releases its camera and never restarts it automatically.
Return to Home and press Start / Resume to control another app again. Practice
does not create an accessibility action adapter or send any external swipe or tap.
Settings offers direction reversal and floating controls. Every existing technical
control, diagnostics field and manual action test is grouped under Advanced.
Edits remain a draft across tabs and rotation. Apply changes saves once and pauses
active or starting tracking; Resume from Home restarts with the saved values.
Discard and per-group resets affect the draft. Drafts are not saved across process
death; existing saved preference keys and values are preserved.
Pause releases capture; Stop removes the notification and overlay. Screen lock
pauses, and process restart remains stopped. Camera-free screen-action tests are
available in Settings → Advanced while stopped; they explicitly act on another
app. Dispatch completion does not guarantee a feed response. Floating controls show
status, Pause, Stop and Open app; resume always needs an explicit press in Home.

## Documentation

- [Development guide](docs/development.md)
- [Architecture and lifecycle](docs/architecture.md)
- [Migration inventory](docs/migration-inventory.md)
- [Migration and validation report](docs/migration.md)
- [Historical gesture parity](docs/gesture-parity.md)
- [Native tap gestures](docs/tap-gestures.md)
- [Permissions](docs/permissions.md)
- [Physical-device trials](docs/device-validation.md)
- [Prior independent QA review](docs/qa-review.md)

No recording, frame upload, backend, analytics, runtime model download or network
permission. Live camera accuracy, real-feed behavior, thermal/battery performance
and distribution readiness require physical acceptance.
