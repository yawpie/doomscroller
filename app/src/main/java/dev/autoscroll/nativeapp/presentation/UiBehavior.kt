package dev.autoscroll.nativeapp.presentation

import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.settings.NativeSettings

internal fun readyToStart(camera: Boolean, accessibility: Boolean, consent: Boolean,
    floatingEnabled: Boolean, floatingAllowed: Boolean) =
    camera && accessibility && consent && (!floatingEnabled || floatingAllowed)

internal fun settingsChangePauses(state: GestureSessionState, saved: NativeSettings, draft: NativeSettings) =
    saved != draft && (state == GestureSessionState.Active || state == GestureSessionState.Starting)
