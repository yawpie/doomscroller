package dev.autoscroll.nativeapp.presentation

import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.settings.NativeSettings
import dev.autoscroll.nativeapp.settings.ControlMode

internal fun readyToStart(camera: Boolean, accessibility: Boolean, consent: Boolean,
    floatingEnabled: Boolean, floatingAllowed: Boolean, mode: ControlMode = ControlMode.SCROLL,
    pointerConsent: Boolean = false, calibrated: Boolean = false) =
    camera && accessibility && consent &&
        (if (mode == ControlMode.POINTER) floatingAllowed && pointerConsent && calibrated else !floatingEnabled || floatingAllowed)

internal fun settingsChangePauses(state: GestureSessionState, saved: NativeSettings, draft: NativeSettings) =
    saved != draft && (state == GestureSessionState.Active || state == GestureSessionState.Starting)
