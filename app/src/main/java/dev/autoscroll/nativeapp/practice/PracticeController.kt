package dev.autoscroll.nativeapp.practice

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.LifecycleOwner
import dev.autoscroll.nativeapp.camera.CameraController
import dev.autoscroll.nativeapp.gesture.HandLandmark
import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.session.SessionMachine
import dev.autoscroll.nativeapp.session.SessionStore
import dev.autoscroll.nativeapp.settings.NativeSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class PracticeState(
    val session: GestureSessionState = GestureSessionState.Stopped,
    val sample: PracticeSample = PracticeSample(),
    val message: String? = null,
    val preview: Bitmap? = null,
    val landmarks: List<HandLandmark> = emptyList(),
)

/** Activity/main-thread owned; disposed when its screen is left or Activity stops. */
internal class PracticeController(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val scope: CoroutineScope,
    private val store: SessionStore,
    private val visible: () -> Boolean,
    private val cameraAllowed: () -> Boolean,
) {
    private val machine = SessionMachine()
    private val mutableState = MutableStateFlow(PracticeState())
    val state = mutableState.asStateFlow()
    private var camera: CameraController? = null
    private var waiting: Job? = null
    private var watchdog: Job? = null
    private var feedback: PracticeFeedback? = null
    private var disposed = false

    fun start(settings: NativeSettings) {
        if (disposed || !store.practiceOpen || !visible()) return
        if (!cameraAllowed()) { fail("Allow camera access before starting practice."); return }
        val token = machine.start() ?: return
        val local = PracticeFeedback(settings)
        feedback = local
        mutableState.value = PracticeState(machine.state, message = "Waiting for the previous camera session to close…")
        waiting = scope.launch {
            try {
                store.awaitPracticeCamera()
                if (disposed || machine.generation != token || machine.state != GestureSessionState.Starting) return@launch
                if (!store.practiceOpen || !visible()) { stop("Practice stopped. Press Start practice when ready."); return@launch }
                if (!cameraAllowed()) { fail("Camera access was revoked. Allow access, then press Start practice."); return@launch }
                mutableState.value = PracticeState(machine.state, message = "Starting the front camera and on-device recognition…")
                var lastResultMs = SystemClock.elapsedRealtime()
                val controller = CameraController(context, settings.tracking,
                    onResult = { result ->
                        if (canReceive(token)) {
                            machine.ready(token)
                            lastResultMs = SystemClock.elapsedRealtime()
                            mutableState.value = PracticeState(machine.state, local.process(result),
                                landmarks = result.pose?.landmarks.orEmpty())
                        }
                    },
                    onMetrics = {},
                    onError = { error -> if (canReceive(token)) fail(practiceFailureMessage(error)) },
                    onPreview = { bitmap -> if (canReceive(token)) mutableState.value = mutableState.value.copy(preview = bitmap) },
                )
                camera = controller
                controller.start(owner)
                watchdog = scope.launch {
                    while (machine.generation == token) {
                        delay(250)
                        val elapsed = SystemClock.elapsedRealtime() - lastResultMs
                        when {
                            !cameraAllowed() -> fail("Camera access was revoked. Allow access, then press Start practice.")
                            !visible() -> stop("Practice paused when you left the screen. Press Start practice when ready.")
                            machine.state == GestureSessionState.Starting && elapsed > 15_000 ->
                                fail("Camera did not produce results. Check Android's camera privacy switch and close other camera apps, then retry.")
                            machine.state == GestureSessionState.Active && elapsed > 5_000 ->
                                fail("Camera results stopped. Check camera access, then retry.")
                            machine.state == GestureSessionState.Active && elapsed > settings.tracking.lostAfterMs -> {
                                mutableState.value = PracticeState(machine.state, local.process(
                                    dev.autoscroll.nativeapp.gesture.HandTrackingResult(SystemClock.elapsedRealtime())))
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (canReceive(token)) fail(practiceFailureMessage(error)) }
        }
    }

    private fun canReceive(token: Long) = !disposed && store.practiceOpen && visible() && cameraAllowed() &&
        machine.generation == token && (machine.state == GestureSessionState.Starting || machine.accepts(token))

    fun stop(message: String? = null) {
        machine.stop()
        release()
        mutableState.value = PracticeState(machine.state, message = message)
    }

    private fun fail(message: String) {
        machine.fail(message)
        release()
        mutableState.value = PracticeState(machine.state, message = message)
    }

    private fun release() {
        waiting?.cancel(); waiting = null
        watchdog?.cancel(); watchdog = null
        feedback?.reset(); feedback = null
        camera?.let { previous ->
            camera = null
            val teardown = CompletableDeferred<Unit>()
            store.cameraReleased = teardown
            previous.close { teardown.complete(Unit) }
        }
    }

    fun close() { stop(); disposed = true }
}

internal fun practiceFailureMessage(error: Throwable): String {
    val recovery = "Practice could not use the camera. Check camera permission and turn on Camera access in Android's privacy controls. Close other camera apps, then press Start practice again."
    return recovery + error.message?.takeIf { it.isNotBlank() }?.let { "\nDetails: $it" }.orEmpty()
}
