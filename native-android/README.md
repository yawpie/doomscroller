# Hand Scroll Native

Independent Kotlin Android application with Compose Material3, front CameraX/MediaPipe tracking, deterministic pose/flick/continuous recognition, foreground camera service, draggable controls and user-requested accessibility swipes. The original Flutter app is preserved at the repository root.

Two Gradle modules: `gesture` is Android-free JVM logic; `app` supplies the Android runtime. No Flutter SDK, Engine, Dart runtime, Flutter Activity or plugins are required by this project. Existing model/license are referenced from `../assets`.

## Build on Windows

Requirements: JDK17+ compatible with Gradle9.1 (tested with Android Studio JBR21), installed Android SDK36/Build Tools36, accepted SDK licenses, network/cached pinned dependencies. Native minimum Android version is8.0/API26.

Create ignored `native-android/local.properties` pointing to your SDK; use forward slashes and escape the drive colon, e.g. `sdk.dir=C\:/Users/ASUS/AppData/Local/Android/sdk`. Set JAVA_HOME for your terminal, without changing machine configuration:

```powershell
cd 'F:\Muhammad Rafi\Projects\autoscroll-compvis\native-android'
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :gesture:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

On Unix use `sh ./gradlew` with the same tasks. Wrapper scripts/JAR are included. Open this directory in Android Studio for native development. Gradle/Kotlin DSL is independent of android/ (the Flutter wrapper). Application ID `dev.autoscroll.nativeapp` can coexist with Flutter `dev.autoscroll.autoscroll_compvis`; permissions/settings/data are independent. Debug signing is for development, not a completed Play release.

For cross-language tests run the original Flutter fixture test before native JUnit; see [gesture parity](docs/gesture-parity.md). APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Documentation

- [Migration inventory/decisions](docs/migration-inventory.md)
- [Migration and validation report](docs/migration.md)
- [Architecture and lifecycle](docs/architecture.md)
- [Behavior parity](docs/gesture-parity.md)
- [Permissions](docs/permissions.md)
- [Physical-device trials and benchmarks](docs/device-validation.md)
- [Independent integration QA](docs/qa-review.md)

Test/build results and remaining acceptance criteria are recorded in the migration report. Full physical gesture/app/permission/lifecycle validation is required before release.
