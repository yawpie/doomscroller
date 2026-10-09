# Permission onboarding and Android restrictions

Home provides a guided checklist. Start remains blocked until camera, consent and
accessibility are ready, plus overlay access when floating controls are enabled.
Notifications never block readiness.

Gestures → Practice safely needs only CAMERA. Accessibility consent/connection,
floating controls and notification access are unnecessary. Start practice is
explicit; permission grants or a return from Settings never start it automatically.
It keeps all feedback and feed movement inside Scroll Vision. Entering practice
pauses external tracking and cancels pending manual tests; an already submitted
short accessibility stroke can still finish because Android has no public
dispatchGesture cancellation operation.

If a manufacturer cannot open the requested system settings page, onboarding falls
back to App info. If that also fails, the checklist explains the manual route.
Opening system settings stops an existing session, so returning does not activate
the camera automatically.

1. Camera: runtime CAMERA is requested only by an explicit checklist action. Front-camera frames stay on device and are not recorded. Denial leaves capture stopped and explains retry. After Android blocks further requests, Open camera settings opens App info with instructions to allow Permissions → Camera. A privacy toggle, revoked permission, resource contention or CameraX error safely releases the pipeline.
2. Overlay: optional SYSTEM_ALERT_WINDOW access is opened with ACTION_MANAGE_OVERLAY_PERMISSION for this app. Skip floating controls saves the existing overlay preference as disabled, so users can start without that permission. It can be enabled later with Settings → Apply changes. Stop removes it. Permission onboarding stops an existing session before opening system settings. Overlay is hidden while Scroll Vision's own Activity is visible.
3. Accessibility: read the scrollable disclosure and affirm “I agree · open Settings,” then manually enable Scroll Vision gestures in Android Settings. Cancel does not grant consent. The app never changes secure settings or grants this access. Purpose is user-requested vertical swipes, center single taps, and center double taps; only current-window package names are inspected to block unsafe targets. No screen content, input text, node retrieval or unrelated automation is requested. AccessibilityTool is false.
4. Notifications: API 33+ optionally requests POST_NOTIFICATIONS. The service still supplies its foreground notification if notification permission is denied; Android determines where the service notice is visible. Pause and Stop are available in the session notice/overlay. The camera foreground-service permissions are declared, including FOREGROUND_SERVICE_CAMERA, and service type is camera.

Camera starts/resumes from the visible Activity following explicit Start/Resume. Creating a camera foreground service from the background is restricted by while-in-use camera permission, particularly on Android 14+. Overlay access is not a substitute for a legal camera activation. A stopped/paused overlay opens the application; it never secretly resumes capture. Screen lock pauses and unlock requires explicit Resume. There is no boot receiver, automatic process-death restart, accessibility auto-enable or permission bypass.

Native minSdk26 is required for TYPE_APPLICATION_OVERLAY (dispatchGesture itself is available since 24). Target/compile SDK 36 are used. AGP 9.0.1 / Gradle 9.1.0 / bytecode17 are compatible with the documented AGP toolchain; dependency versions are pinned. Permissions/lifecycle need real testing across API 26-29,30-33,34-36 and OEMs, rather than treating a successful API 30 test as proof of all versions.

Google Play release is not completed by this migration. Before distribution, supply the required Accessibility API declaration, store disclosure/affirmative consent evidence, foreground-service declarations, privacy policy and signed release packaging appropriate to the final product. This app does not claim to be an accessibility tool for disability support.

Official references checked during migration:

- [Camera foreground-service type and while-in-use restrictions](https://developer.android.com/develop/background-work/services/fgs/service-types#camera)
- [Restrictions on starting foreground services](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [AccessibilityService public gesture dispatch](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler))
- [AGP 9.0 compatibility](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [CameraX1.5.3 release notes](https://developer.android.com/jetpack/androidx/releases/camera#1.5.3)
