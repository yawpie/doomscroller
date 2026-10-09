# Scroll Vision UI implementation plan

> Execute in this session with subagent-driven-development; review specification, then code quality after each task.

**Goal:** Make everyday setup and use straightforward while retaining all gesture behavior and technical settings.

**Architecture:** Keep Android permission/service ownership in MainActivity and existing service. Composables receive data and callbacks. Practice reuses CameraController and GestureCoordinator, has only local feedback, and cannot dispatch accessibility actions.

**Tech stack:** Existing Kotlin, Compose Material 3, CameraX, MediaPipe, JUnit and Android instrumentation dependencies. No new runtime dependencies.

**Branch:** codex/scroll-vision-ux, from 89518496061dc0b1269817a08196bd9c3495f863. Preserve gesture module, parity fixtures, preference keys, defaults, action guards and bounded camera pipeline.

## Task 1: Everyday UI, settings and floating controls

Files: MainActivity.kt; presentation/HandScrollScreen.kt; presentation/SettingsPanel.kt; new focused presentation/theme/gesture illustration files as needed; settings/NativeSettings.kt; overlay/OverlayController.kt and its service caller; AndroidManifest.xml; focused app tests; README.md and docs/development.md, docs/permissions.md.

1. Add failing focused tests for draft/apply rules, permission readiness and reset isolation using existing JUnit/instrumentation tools. Run to establish missing behavior.
2. Build Home, Gestures, Settings navigation, Advanced inside Settings. Home shows readiness, guided permission checklist, prominent Start/Pause/Resume, secondary Stop, compact guide. Keep affirmative accessibility disclosure. Explain camera denial including system settings fallback and privacy switch; let user skip floating controls through existing overlay preference. Notifications remain optional.
3. Gestures uses illustrated cards, honest mode-specific pointing/flick/continuous guidance, repeat/rearm explanations. Distinguish open-hand Tap video (one center tap; feed controls response) from Pause tracking (camera released). Provide practice entry callback for Task 2, without shipping a fake working practice screen.
4. Keep everyday inversion/floating controls understandable. Technical recognition mode, swipe, camera, pose, flick, continuous controls move to grouped Advanced, including diagnostics and explicit manual external action tests. Preserve every old control/value/key. Group resets alter only draft group. Draft edits never save/pause; Apply commits once, pauses active/starting tracking and tells user to Resume. Unchanged Apply does nothing. Keep drafts while navigating; discard is explicit. Use existing immutable copies and simple state.
5. Simplify overlay to status, Pause, Stop and Open app to resume. Remove overlay manual-test controls; keep touch suppression, saved position, bounds, camera activation safeguards. Material 3 light/dark theme, readable scalable text, labels and >=48dp touch targets; scroll content for large text. Keep disclosure scrollable.
6. Run focused tests/build and git diff --check; self-review; update current UI docs. Commit scoped changes as feat(ui): simplify Scroll Vision setup and controls.
7. Independent spec review, corrections, independent quality review and corrections before Task 2.

## Task 2: Safe live gesture practice

Files: MainActivity.kt; presentation/HandScrollScreen.kt; new presentation/PracticeScreen.kt; one focused local-feedback helper if needed; app tests; current UI docs.

1. Add failing tests for local feedback/repeats, isolation and lifecycle teardown where feasible with existing dependencies.
2. Practice pauses external tracking and cancels pending manual tests before entry. Requires camera only; accessibility consent/connection and overlay permission are unnecessary. Explicit Start practice, live recognized pose/action feedback and local sample feed; never constructs/calls action dispatch, never backgrounds app to test actions.
3. Reuse existing bounded CameraController/GestureCoordinator and saved settings. Serialize camera handoff using SessionStore.cameraReleased; avoid races where service pause hasn't finished. Close camera on exit/background/lock/disposal, discard late callbacks, reset recognition, remain stopped until explicit Start practice. Live failures/denials have useful recovery. Returning to normal tracking requires explicit Resume/Start.
4. Inspect and test unchanged pointing/repeating/thumb/open-hand semantics using existing golden tests; local practice mirrors mode and inversion. No timers, new gestures, presets, dependency/architecture expansion, or expensive animations.
5. Run focused checks, self-review, docs update, commit feat(ui): add isolated gesture practice. Independent spec review then independent quality review, fix all material findings.

## Task 3: Integration and device evidence

1. Run repository-required :gesture:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest.
2. Use emulator Pixel_7_API_34 explicitly (avoid personal phone). Install update preserving app data; run connected instrumentation with ANDROID_SERIAL selecting emulator. Record skips accurately.
3. Inspect screenshots and actual interactions for setup/denial/disclosure, Home, Gestures, Settings, Advanced, practice; dark mode and large text. Exercise draft apply/reset and camera exit/background lifecycle. Capture evidence under artifacts; logs/screenshots follow ignore conventions.
4. Fix findings through implementer and re-review. Document actual validation and remaining physical-hand/feed/performance limits. Final independent overall review.
5. Leave tracked commits on requested branch. Do not push/merge or change personal-device permissions. Summarize changes, evidence, unverified work; defer optional timers, new gestures and presets.
