package dev.autoscroll.nativeapp.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.*

internal fun poseIdLabel(pose: PoseId) = when (pose) {
    PoseId.POINTING_UP -> "Point up"; PoseId.POINTING_DOWN -> "Point down"
    PoseId.POINTING_LEFT -> "Point left"; PoseId.POINTING_RIGHT -> "Point right"
    PoseId.INDEX_ONLY -> "Index finger only (point up)"; PoseId.MIDDLE_ONLY -> "Middle finger only"
    PoseId.INDEX_MIDDLE -> "Index and middle fingers"; PoseId.OPEN_HAND -> "Open hand"
    PoseId.CLOSED_HAND -> "Closed hand"; PoseId.THUMBS_UP -> "Thumbs-up"
}
internal fun poseActionLabel(action: PoseAction) = when (action) {
    PoseAction.NONE -> "No action"; PoseAction.NEXT_ITEM -> "Scroll next once"
    PoseAction.PREVIOUS_ITEM -> "Scroll previous once"; PoseAction.SINGLE_TAP -> "Single tap"
    PoseAction.SCROLL_LEFT -> "Scroll left once"; PoseAction.SCROLL_RIGHT -> "Scroll right once"
    PoseAction.DOUBLE_TAP -> "Double tap"; PoseAction.BACK -> "Back"
    PoseAction.CONTINUOUS_NEXT -> "Scroll next while held"; PoseAction.CONTINUOUS_PREVIOUS -> "Scroll previous while held"
}
private fun actionDescription(action: PoseAction) = when (action) {
    PoseAction.NONE -> "No pose action. In Hybrid, flick and movement can still work."
    PoseAction.NEXT_ITEM, PoseAction.PREVIOUS_ITEM -> "One vertical swipe per appearance."
    PoseAction.SCROLL_LEFT -> "View content to the left with one rightward swipe per appearance."
    PoseAction.SCROLL_RIGHT -> "View content to the right with one leftward swipe per appearance."
    PoseAction.SINGLE_TAP -> "One tap at the center of the content."
    PoseAction.DOUBLE_TAP -> "Two taps at the center; the target decides the result."
    PoseAction.BACK -> "Return to the previous screen, not the previous feed item."
    PoseAction.CONTINUOUS_NEXT, PoseAction.CONTINUOUS_PREVIOUS -> "Scroll while the confirmed pose remains visible."
}
internal fun poseActionsInactiveReason(settings: NativeSettings): String? = when {
    !settings.poseActions.enabled -> "Custom pose actions are off. In Scroll mode, Pose and Hybrid use the default preset. Saved custom actions are retained."
    settings.controlMode == ControlMode.POINTER -> "Custom pose actions are inactive in Pointer mode."
    settings.mode == GestureMode.FLICK || settings.mode == GestureMode.CONTINUOUS -> "Custom pose actions are inactive in ${modeLabel(settings.mode)} mode."
    else -> null
}

@Composable
internal fun PoseActionsPanel(value: NativeSettings, update: (NativeSettings) -> Unit, onPractice: (() -> Unit)?) {
    val actions = value.poseActions
    var selected by remember { mutableStateOf<PoseId?>(null) }
    Text("Hand pose actions", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth().toggleable(actions.enabled, role = Role.Switch,
        onValueChange = { update(value.copy(poseActions = actions.copy(enabled = it))) }).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("Use custom pose actions", modifier = Modifier.weight(1f)); Switch(actions.enabled, onCheckedChange = null)
    }
    poseActionsInactiveReason(value)?.let { Text(it) }
    run {
        Text(if (actions.enabled) "Choose one recognition profile. Both groups remain editable; common poses are shared." else "Saved custom profile and actions are inactive until enabled.")
        PoseRecognitionProfile.entries.forEach { profile ->
            Row(Modifier.fillMaxWidth().selectable(actions.profile == profile, enabled = actions.enabled, role = Role.RadioButton,
                onClick = { update(value.copy(poseActions = actions.copy(profile = profile))) }).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(actions.profile == profile, onClick = null, enabled = actions.enabled)
                Text(if (profile == PoseRecognitionProfile.DIRECTION) "Pointing directions" else "Finger combinations")
            }
        }
        listOf(PoseRecognitionProfile.DIRECTION, PoseRecognitionProfile.FINGER_COMBINATIONS, null).forEach { profile ->
            val title = when (profile) { PoseRecognitionProfile.DIRECTION -> "Pointing directions"; PoseRecognitionProfile.FINGER_COMBINATIONS -> "Finger combinations"; null -> "Common poses" }
            Text(title + if (profile != null && profile != actions.profile) " · inactive profile" else "", style = MaterialTheme.typography.titleSmall)
            PoseId.entries.filter { it.profile == profile }.forEach { pose ->
                OutlinedButton(onClick = { selected = pose }, enabled = actions.enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                PoseIllustration(pose)
                Text("${poseIdLabel(pose)}: ${poseActionLabel(actions.bindings.getValue(pose))}")
            }
        }
                }
            }
        }
        Text("Point the index finger up for the index-only action. Index and middle together has its own action; it does not run either single-finger action. Keep the other fingers and thumb folded. Open hand means all five fingers open; closed hand keeps the thumb close to the fist. Thumbs-up extends the thumb with four fingers curled.")
        if (PoseId.entries.filter { it.profile == null || it.profile == actions.profile }.all { actions.actionFor(it) == PoseAction.NONE })
            Text("No pose actions are active in this profile.")
    }
    OutlinedButton(onClick = { update(value.reset(SettingsGroup.POSE_ACTIONS)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Reset pose actions") }
    if (onPractice != null) TextButton(onClick = onPractice, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Try in Practice") }
    selected?.let { pose ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(poseIdLabel(pose)) }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                PoseAction.entries.forEach { action ->
                    Row(Modifier.fillMaxWidth().selectable(actions.bindings[pose] == action, role = Role.RadioButton,
                        onClick = { update(value.copy(poseActions = actions.withBinding(pose, action))); selected = null }).heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(actions.bindings[pose] == action, onClick = null)
                        Column { Text(poseActionLabel(action)); Text(actionDescription(action), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { selected = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } })
    }
}

@Composable
internal fun SavedPoseActionsGuide(settings: NativeSettings) {
    val actions = settings.poseActions
    Text("Custom pose actions · ${if (actions.profile == PoseRecognitionProfile.DIRECTION) "Pointing directions" else "Finger combinations"}", style = MaterialTheme.typography.titleMedium)
    poseActionsInactiveReason(settings)?.let { Text(it) }
    PoseId.entries.filter { it.profile == null || it.profile == actions.profile }.forEach { pose ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                PoseIllustration(pose)
                Text("${poseIdLabel(pose)}: ${poseActionLabel(actions.bindings.getValue(pose))}")
            }
        }
    }
    Text("No action means this pose sends no action; in Hybrid, flick and movement can still work.")
    Text("Single actions run once per appearance. Continuous actions run while the confirmed pose is held. Index and middle together has its own action. Open hand uses all five fingers; closed hand keeps the thumb close to the fist; thumbs-up extends the thumb with the other fingers curled.")
    Text("Custom pose scroll follows the selected action; reverse direction still affects flick and hand movement.")
}
