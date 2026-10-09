# Hand Scroll Native

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

Read the accessibility disclosure and grant camera permission. Allow the optional
floating widget, or disable it in Settings. Enable accessibility yourself in Android
Settings. Start on Dashboard and wait for Active before switching to a feed.

HYBRID/POSE: point down/up to navigate, show an open hand for one single tap per
presentation, or show thumbs-up for double tap. Pointing/thumbs-up repeat after the
configured rearm delay and fresh confirmation. CONTINUOUS/FLICK preserve their
movement controls. Settings changes pause an active session; Resume applies them.
Pause releases capture; Stop removes the notification and overlay. Screen lock
pauses, and process restart remains stopped. Camera-free screen-action tests are
available while stopped. Dispatch completion does not guarantee a feed response.

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
