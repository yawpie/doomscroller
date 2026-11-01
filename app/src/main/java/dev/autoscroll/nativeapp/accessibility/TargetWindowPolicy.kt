package dev.autoscroll.nativeapp.accessibility

/** Only a package name is retained; no node, event text, or screen content is inspected. */
internal data class TargetWindowState(val packageName: String? = null, val allowed: Boolean = false, val generation: Long = 0)

internal class TargetWindowPolicy(private val ownPackage: String, initialGeneration: Long = 0) {
    var state = TargetWindowState(generation = initialGeneration)
        private set
    var allowed: Boolean = false
        private set

    fun update(packageName: String?) {
        val nextAllowed = packageName != null && packageName != ownPackage &&
            packageName != "android" && !packageName.contains("systemui", ignoreCase = true) &&
            !packageName.contains("settings", ignoreCase = true) &&
            !packageName.contains("permissioncontroller", ignoreCase = true) &&
            !packageName.contains("packageinstaller", ignoreCase = true) &&
            !packageName.contains("launcher", ignoreCase = true) &&
            !packageName.contains("inputmethod", ignoreCase = true)
        if (state.packageName != packageName || state.allowed != nextAllowed) {
            state = TargetWindowState(packageName, nextAllowed, state.generation + 1)
        }
        allowed = nextAllowed
    }
}
