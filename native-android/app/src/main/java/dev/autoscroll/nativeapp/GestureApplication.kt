package dev.autoscroll.nativeapp

import android.app.Application
import dev.autoscroll.nativeapp.session.SessionStore
import dev.autoscroll.nativeapp.settings.SettingsStore

class GestureApplication : Application() {
    val session = SessionStore()
    lateinit var settings: SettingsStore
        private set
    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
    }
}
