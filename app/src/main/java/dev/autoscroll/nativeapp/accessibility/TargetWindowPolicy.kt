package dev.autoscroll.nativeapp.accessibility

/** Only a package name is retained; no node, event text, or screen content is inspected. */
internal class TargetWindowPolicy(private val ownPackage: String) {
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
    }
}
