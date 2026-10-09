package dev.autoscroll.nativeapp.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.autoscroll.nativeapp.settings.NativeSettings

/** Activity-owned draft survives rotation without persisting unapplied values. */
internal class SettingsDraft : ViewModel() {
    var value by mutableStateOf<NativeSettings?>(null)
}
