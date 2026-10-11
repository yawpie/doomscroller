package dev.autoscroll.nativeapp.accessibility

/** Only a package name is retained; no node, event text, or screen content is inspected. */
internal data class TargetWindowState(val packageName: String? = null, val allowed: Boolean = false)

internal class TargetWindowPolicy(private val ownPackage: String) {
    var state = TargetWindowState()
        private set
    var allowed: Boolean = false
        private set

    fun update(packageName: String?) {
        allowed = packageName != null && packageName != ownPackage &&
            packageName != "android" && !packageName.contains("systemui", ignoreCase = true) &&
            !packageName.contains("settings", ignoreCase = true) &&
            !packageName.contains("permissioncontroller", ignoreCase = true) &&
            !packageName.contains("packageinstaller", ignoreCase = true) &&
            !packageName.contains("launcher", ignoreCase = true) &&
            !packageName.contains("inputmethod", ignoreCase = true)
        state = TargetWindowState(packageName, allowed)
    }
}
