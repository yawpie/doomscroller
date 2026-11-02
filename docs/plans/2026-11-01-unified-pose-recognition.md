# Unified Pose Recognition Implementation Plan

> Execute with subagent-driven-development in this chat: one implementer at a time,
> then independent specification and code-quality reviews.

**Goal:** Replace the fixed legacy pose pipeline with one canonical, configurable
pose pipeline, using CustomPoseClassifier geometry within HandPoseClassifier.

**Architecture:** HandPoseClassifier owns aspect-correct canonical geometry.
One HandPoseDetector and GestureCoordinator apply confirmation, release, latch,
held output and arbitration. Both default presets and saved custom bindings use
that pipeline; disabling customization selects defaults rather than legacy code.

**Tech stack:** Existing Kotlin/JVM gesture module and Android Kotlin/Compose app;
Gradle Wrapper, JDK 17 and pinned Android SDK. No added dependencies or permissions.

## Baseline and intentional change

- Start from local develop `7d396f6`, including its unpublished horizontal actions.
- Preserve preference names, saved bindings, tuning, consent and Pointer behavior.
- Keep custom off as the saved default, using default direction-profile bindings.
  Preserve direction reversal for this preset; saved custom scroll directions remain
  final. Recognition geometry and timing intentionally adopt canonical custom rules:
  discrete poses trigger once per appearance, including default pointing/thumbs-up.
- Preserve `parity-fixtures/gesture-goldens.json` byte-for-byte. If legacy pose
  replay becomes obsolete, explicitly document its retired coverage and retain
  unaffected motion/flick replay. Do not alter old expectations to mask regressions.
- Preserve Back/hold freshness, target/hold generations, one operation, no backlog,
  and explicit session lifecycle. Do not retain a production legacy fallback.

## Task 1: Consolidate classifier ownership

**Files:** `gesture/.../HandPoseClassifier.kt`, delete
`gesture/.../CustomPoseClassifier.kt`, update `CustomPoseDetector.kt` and native
classifier tests. The old classifier overload may remain only for this transition;
Task 2 removes it along with its production callers.

1. Add a failing native test for canonical classification through HandPoseClassifier.
2. Run that test, recording the missing canonical behavior/API.
3. Move the existing custom geometry into HandPoseClassifier, preserve threshold
   validation, aspect correction, exact finger identity, profiles and hysteresis.
4. Redirect custom callers and remove the redundant classifier class.
5. Run affected tests and the full validation command below.
6. Independent spec review, then quality review; fix and re-review any findings.
7. Commit this complete classifier consolidation with its tests and this plan.

## Task 2: Replace the legacy runtime end to end

**Files:** gesture detector/coordinator/models/config; app settings/resolver,
Practice, session wiring, action coordination, UI guidance, affected tests and docs.

1. Add failing tests proving custom-off presets use canonical geometry and
   once-per-appearance triggers in both Practice and external recognition.
2. Promote the canonical detector into HandPoseDetector, remove the legacy
   classifier overload, fixed-pose state machine and obsolete production types/config.
3. Use one canonical result and shared binding resolver across all callers. Custom
   off chooses default bindings/profile without destroying saved custom values.
4. Preserve inversion for default preset and movement/flick, custom final directions,
   all horizontal actions, NONE arbitration, Pointer isolation and dispatch guards.
5. Update saved/draft guidance, reset/Apply/Discard and cooldown explanations.
6. Replace obsolete native expectations with explicit canonical behavior tests;
   explain historical golden coverage honestly. Update behavior docs and receipt.
7. Run targeted tests, full gate and relevant emulator UI/dispatch/lifecycle checks.
8. Independent spec review, then quality review; fix and re-review findings.
9. Commit this functional replacement and its tests/docs together.

## Validation and completion

From the project root with JDK 17:

```powershell
.\gradlew.bat :gesture:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
git diff --check
```

Execute instrumentation separately on explicitly selected `emulator-5554` only;
never change a personal device's permissions. Preserve initial failed-run evidence.
Physical recognition accuracy and sustained/feed calibration remain separate and
unmeasured unless actual trials can be performed.

Finish with a fresh overall review, verify clean task changes and preserved base
commit, push the feature branch and open a PR to develop. Do not merge automatically.
