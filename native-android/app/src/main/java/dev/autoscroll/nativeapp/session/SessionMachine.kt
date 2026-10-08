package dev.autoscroll.nativeapp.session

sealed interface GestureSessionState {
    data object Stopped : GestureSessionState
    data object Starting : GestureSessionState
    data object Active : GestureSessionState
    data object Paused : GestureSessionState
    data class Error(val reason: String) : GestureSessionState
}

/** Main-thread owner. Every invalidation also invalidates callbacks from the old camera. */
class SessionMachine {
    var state: GestureSessionState = GestureSessionState.Stopped
        private set
    var generation: Long = 0
        private set

    fun start(prerequisiteError: String? = null): Long? {
        if (state == GestureSessionState.Active || state == GestureSessionState.Starting) return null
        generation++
        state = prerequisiteError?.let(GestureSessionState::Error) ?: GestureSessionState.Starting
        return if (prerequisiteError == null) generation else null
    }

    fun ready(token: Long): Boolean {
        if (generation != token || state != GestureSessionState.Starting) return false
        state = GestureSessionState.Active
        return true
    }

    fun accepts(token: Long) = token == generation && state == GestureSessionState.Active

    fun pause() {
        if (state != GestureSessionState.Active && state != GestureSessionState.Starting) return
        generation++
        state = GestureSessionState.Paused
    }

    fun stop() {
        if (state == GestureSessionState.Stopped) return
        generation++
        state = GestureSessionState.Stopped
    }

    fun fail(reason: String) {
        generation++
        state = GestureSessionState.Error(reason)
    }
}
