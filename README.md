# Hand Scroll Native

Native Android hand-gesture scrolling with Kotlin, Compose, CameraX and on-device
MediaPipe. A foreground camera service recognizes continuous motion, flicks,
pointing poses, open-hand single taps and thumbs-up double taps. Optional floating
controls and Android accessibility dispatch actions into the active supported app.

The complete Android Studio / Gradle project is [native-android](native-android/README.md).
The legacy app, platform channel and SDK tooling have been removed; its behavioral
baseline remains in Git history and the native golden regression fixtures.

Requirements: Android SDK 36, JDK 17+, and an Android API 26+ device with a front
camera. From `native-android` on Windows:

```powershell
.\gradlew.bat :gesture:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

On Unix use `sh ./gradlew` with the same tasks. No cross-platform SDK is required.

- [Setup, controls and build commands](native-android/README.md)
- [Architecture and resource ownership](native-android/docs/architecture.md)
- [Developer guide and validation](native-android/docs/development.md)
- [Migration status and verification limits](native-android/docs/migration.md)
- [Physical-device acceptance](native-android/docs/device-validation.md)

Inference works offline with the licensed bundled model. Camera, overlay and
accessibility permissions remain user controlled. Release signing and physical
camera/feed/performance acceptance are separate from compilation and regression
checks.
