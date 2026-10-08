# Native tap gestures

This extension applies only to `native-android`. Flutter production source and the saved Dart parity fixtures remain unchanged. Use HYBRID (default) or POSE mode and enable the existing accessibility service through Android Settings.

| Pose | Action |
| --- | --- |
| Index pointing down / up | Forward / backward swipe, respecting direction inversion |
| Open hand (`NEUTRAL` in diagnostics) | One single tap at the content center |
| Thumbs-up: thumb vertically extended, other four fingers curled | One double tap at the same content point |
| Closed hand (four fingers curled, thumb no longer up) | Release the current pose latch; no tap |

Hold an action pose for the existing confirmation duration and minimum observation count (defaults: 180ms, three observations). A held pose triggers once. Close the hand long enough for release and cooldown before repeating (defaults: 180ms release, 450ms cooldown after the previous event). Changing between tap poses or from a tap to pointing requires release evidence followed by fresh confirmation. Direct pointing up/down changes do not rearm. An open hand after pointing now releases toward a single-tap action, so use a closed hand when you want to release without tapping.

Tracking loss, malformed landmarks, duplicate timestamps, sample gaps, and brief pose changes cannot release a confirmed latch. A new session/settings change resets recognition. CONTINUOUS and FLICK modes retain their movement-only behavior.

Single tap toggles pause/play in feeds that support tapping their center; it cannot guarantee a paused state without inspecting the feed. Double-tap meaning is determined by the target app. Both taps use public Android accessibility dispatch with the same freshness, session, target, lock, interaction, overlay, and busy guards as swipes. If the widget covers the center, the tap shifts to a clear column at the same height or is rejected when no safe column exists.

Single tap uses a 50ms stationary touch. Double tap uses two 50ms stationary touches in one dispatch, with a 100ms up-to-down gap capped at half Android's double-tap timeout. The implementation follows the public [StrokeDescription contract](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.StrokeDescription) and [ViewConfiguration timeout](https://developer.android.com/reference/android/view/ViewConfiguration#getDoubleTapTimeout()). Pause/Stop blocks future dispatch; Android provides no public cancellation of an already submitted short gesture.

Dashboard's camera-free Single tap / Double tap buttons let you check target-app behavior before enabling capture. First open the target app, return to Hand Scroll, keep the session Stopped, and choose the test. Hand Scroll backgrounds and dispatches after one second.

## Verification

`TapGestureTest` covers geometry on both mirrored hands, scale/position/rotation, hysteresis, malformed/partial poses, confirmation, held-pose suppression, closed-hand release, cooldown, tracking loss and timestamps. Coordinator tests cover mapping, inversion, freshness, busy rejection, and pause cancellation. Coordinate tests cover center placement and overlay avoidance. `TapDescriptionTest` checks stationary single/sequential-double stroke construction on Android; `AccessibilityDispatchTest` additionally checks observed single/double taps in an external test-APK surface when the service is enabled through Settings.

Real-camera thumb accuracy and pause/play behavior in TikTok/Reels/Shorts require the physical-device trials in [device validation](device-validation.md).

Executed on 2026-10-09:

| Check | Result |
| --- | --- |
| Kotlin JVM tests | 80 passed, 0 failed/skipped, including the unchanged 39-case Dart baseline |
| Android stationary/sequential tap construction | 2 passed on API 36 Pixel emulator |
| Observed external test surface | 2 passed together: forward/backward scroll and single/double taps; raw instrumentation status 0 for each |
| Debug APK, test APK, Android lint | Build successful |

The service was enabled/reconnected through the emulator's Settings UI during the instrumentation run, because instrumentation restarts the app. Initial permission-related skips, a test-APK Kotlin runtime failure, and a test-surface cleanup race were preserved in the ignored `artifacts/tap-validation` logs; the final test surface uses Android/Java only and waits for destruction between tests. Final evidence: `artifacts/tap-validation/observed-actions-verified.txt`. The emulator ran read-only without saving snapshots. These checks establish injected-touch behavior on the controlled surface, not real-camera gesture accuracy or feed-specific pause/play behavior.
