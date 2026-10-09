# Native tap gestures

This extension applies only to `native-android`. Flutter production source and the saved Dart parity fixtures remain unchanged. Use HYBRID (default) or POSE mode and enable the existing accessibility service through Android Settings.

| Pose | Action |
| --- | --- |
| Index pointing down / up | Forward / backward swipe, respecting direction inversion |
| Open hand (`NEUTRAL` in diagnostics) | One single tap at the content center |
| Thumbs-up: thumb vertically extended, other four fingers curled | One double tap at the same content point |
| Closed hand (four fingers curled, thumb no longer up) | No action; not required for rearming |

Hold an action pose for the confirmation duration and minimum observation count (defaults: 180ms, three observations). Rearming uses elapsed time only. Settings > Rearm delay sets the interval after an action, from 200 to 3000ms (default 450ms). After the delay, the next action needs fresh confirmation. Held pointing and thumbs-up poses can repeat; direct up/down changes are allowed after the delay. Neither an open nor a closed hand releases the timer early.

Open hand is reserved for a single pause tap per presentation. Holding it blocks movement and never repeats the tap, even after the rearm delay or tracking loss. A recognized non-open pose allows a later open-hand presentation to tap again; this tap debounce does not control navigation or thumbs-up rearming. Tracking loss and sample gaps discard candidate evidence; the timer can expire without a tracked hand, but reacquisition still needs fresh confirmation. Duplicate/decreasing timestamps cannot advance it. A new session/settings change resets recognition. CONTINUOUS and FLICK modes retain their movement-only behavior.

Single tap toggles pause/play in feeds that support tapping their center; it cannot guarantee a paused state without inspecting the feed. Double-tap meaning is determined by the target app. Both taps use public Android accessibility dispatch with the same freshness, session, target, lock, interaction, overlay, and busy guards as swipes. If the widget covers the center, the tap shifts to a clear column at the same height or is rejected when no safe column exists.

Single tap uses a 50ms stationary touch. Double tap uses two 50ms stationary touches in one dispatch, with a 100ms up-to-down gap capped at half Android's double-tap timeout. The implementation follows the public [StrokeDescription contract](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.StrokeDescription) and [ViewConfiguration timeout](https://developer.android.com/reference/android/view/ViewConfiguration#getDoubleTapTimeout()). Pause/Stop blocks future dispatch; Android provides no public cancellation of an already submitted short gesture.

Dashboard's camera-free Single tap / Double tap buttons let you check target-app behavior before enabling capture. First open the target app, return to Hand Scroll, keep the session Stopped, and choose the test. Hand Scroll backgrounds and dispatches after one second.

## Verification

`TapGestureTest` covers geometry on both mirrored hands, scale/position/rotation, hysteresis, malformed/partial poses, confirmation, timed repetition, configurable delay, direct direction changes, timer expiry without a hand, open-hand tap debounce, tracking loss and timestamps. `SettingsStoreTest` checks delay persistence and native timed defaults. Coordinator tests cover mapping, inversion, freshness, busy rejection, and pause cancellation. Coordinate tests cover center placement and overlay avoidance. `TapDescriptionTest` checks stationary single/sequential-double stroke construction on Android; `AccessibilityDispatchTest` additionally checks observed single/double taps in an external test-APK surface when the service is enabled through Settings.

Real-camera thumb accuracy and pause/play behavior in TikTok/Reels/Shorts require the physical-device trials in [device validation](device-validation.md).

Initial tap-extension verification on 2026-10-09, before the timed-rearm update:

| Check | Result |
| --- | --- |
| Kotlin JVM tests | 80 passed, 0 failed/skipped, including the unchanged 39-case Dart baseline |
| Android stationary/sequential tap construction | 2 passed on API 36 Pixel emulator |
| Observed external test surface | 2 passed together: forward/backward scroll and single/double taps; raw instrumentation status 0 for each |
| Debug APK, test APK, Android lint | Build successful |

The service was enabled/reconnected through the emulator's Settings UI during the instrumentation run, because instrumentation restarts the app. Initial permission-related skips, a test-APK Kotlin runtime failure, and a test-surface cleanup race were preserved in the ignored `artifacts/tap-validation` logs; the final test surface uses Android/Java only and waits for destruction between tests. Final evidence: `artifacts/tap-validation/observed-actions-verified.txt`. The emulator ran read-only without saving snapshots. These checks establish injected-touch behavior on the controlled surface, not real-camera gesture accuracy or feed-specific pause/play behavior.

Timed-rearm update verified on 2026-10-09:

| Check | Result |
| --- | --- |
| Kotlin JVM tests | 83 passed (58 gesture, 25 app), 0 failed/skipped; unchanged Dart golden replay passed |
| Android settings persistence and tap construction | 3 passed on API 36 Pixel emulator; raw status 0 for each |
| Settings UI | Slider rendered at 1080x1920; 450ms changed to 1600ms and remained 1600ms after force-stop/relaunch |
| Debug APK, test APK, Android lint | Build successful |
| Scope and whitespace | Native-only changes; diff check passed |

Local UI XML/screenshots and raw instrumentation output are under ignored `artifacts/timed-rearm`. Physical camera/target-feed trials and the external accessibility test surface were not rerun for this update; the previous observed-touch checks above remain separate evidence.
