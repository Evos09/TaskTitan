package com.rork.tasktitan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.rork.tasktitan.data.AppData
import com.rork.tasktitan.data.Catalog
import com.rork.tasktitan.data.ChecklistItem
import com.rork.tasktitan.data.Chore
import com.rork.tasktitan.data.DateUtil
import com.rork.tasktitan.data.Frequency
import com.rork.tasktitan.data.GameRules
import com.rork.tasktitan.ui.components.MemberAvatar
import com.rork.tasktitan.ui.components.roomAccent
import com.rork.tasktitan.ui.components.roomIcon
import com.rork.tasktitan.ui.components.roomTint
import com.rork.tasktitan.ui.theme.TT

/** Create / edit a chore: weight, room, checklist ("what done means"), rotation and options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoreEditorSheet(
    data: AppData,
    initial: Chore,
    isNew: Boolean,
    onSave: (Chore) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val c = TT.c
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(initial.title) }
    var roomId by remember { mutableStateOf(initial.roomId) }
    var weight by remember { mutableIntStateOf(initial.weight) }
    var custom by remember { mutableStateOf(initial.weight > 3) }
    var freq by remember { mutableStateOf(initial.frequency) }
    var interval by remember { mutableIntStateOf(initial.intervalDays.coerceAtLeast(2)) }
    var assignee by remember { mutableStateOf(initial.assigneeId) }
    var rotate by remember { mutableStateOf(initial.rotate) }
    var auto by remember { mutableStateOf(initial.autoAssign) }
    var timerOn by remember { mutableStateOf(initial.timerMinutes != null) }
    var timerMin by remember { mutableIntStateOf(initial.timerMinutes ?: 10) }
    var confirm by remember { mutableStateOf(initial.needsConfirm) }
    var remindOn by remember { mutableStateOf(initial.remindMinuteOfDay != null) }
    var remindMinute by remember { mutableIntStateOf(initial.remindMinuteOfDay ?: 9 * 60) }
    var showTimePicker by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf(initial.checklist) }
    var newStep by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(initial.notes) }
    val reward = GameRules.reward(weight)

    fun addStep() {
        if (newStep.isNotBlank()) {
            steps = steps + ChecklistItem(GameRules.newId(), newStep.trim())
            newStep = ""
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.canvas) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Text(if (isNew) "New Chore" else "Edit Chore", style = MaterialTheme.typography.headlineMedium, color = c.ink)
            Text(if (isNew) "Create a chore for your household" else "Changes apply from today on", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Spacer(Modifier.height(14.dp))

            Label("Chore name")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(40) },
                singleLine = true,
                placeholder = { Text("e.g. Vacuum living room") },
                trailingIcon = { if (title.isNotEmpty()) IconButton(onClick = { title = "" }) { Icon(Icons.Rounded.Cancel, "Clear", tint = c.muted) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            Label("Room")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.rooms, key = { it.id }) { r ->
                    val on = r.id == roomId
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (on) roomTint(r.type) else c.surface)
                            .clickable(role = Role.RadioButton) { roomId = r.id }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(roomIcon(r.type), null, tint = roomAccent(r.type), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(r.name + if (data.floors > 1) " · F${r.floor + 1}" else "", style = MaterialTheme.typography.labelLarge, color = if (on) roomAccent(r.type) else c.ink)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Label("Effort level")
            Row(verticalAlignment = Alignment.CenterVertically) {
                val opts = listOf("Light", "Normal", "Heavy", "Custom")
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    opts.forEachIndexed { i, o ->
                        val sel = if (i == 3) custom else !custom && weight == i + 1
                        SegmentedButton(
                            selected = sel,
                            onClick = { if (i == 3) { custom = true; if (weight < 4) weight = 4 } else { custom = false; weight = i + 1 } },
                            shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = c.tangerineSoft, activeContentColor = c.tangerine),
                            icon = {},
                        ) { Text(o, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weight", style = MaterialTheme.typography.labelMedium, color = c.muted)
                for (i in 1..5) {
                    val on = i <= weight
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (on) c.tangerine else c.surface)
                            .clickable(role = Role.RadioButton, onClickLabel = "Weight $i") { weight = i; custom = i > 3 }
                            .semantics { contentDescription = "Weight $i of 5" },
                        contentAlignment = Alignment.Center,
                    ) { Text("$i", style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else c.muted) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.tangerineSoft).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Star, null, tint = c.sun, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("${GameRules.weightLabel(weight)} = +${reward.xp} XP · +${reward.coins} coins · +${reward.energy} energy", style = MaterialTheme.typography.titleSmall, color = c.ink)
                    Text("Bigger effort, bigger rewards!", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            Spacer(Modifier.height(14.dp))

            Label("How often?")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Frequency.entries.forEachIndexed { i, f ->
                    SegmentedButton(
                        selected = freq == f,
                        onClick = { freq = f },
                        shape = SegmentedButtonDefaults.itemShape(i, Frequency.entries.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = c.skySoft, activeContentColor = c.sky),
                        icon = {},
                    ) { Text(if (f == Frequency.CUSTOM) "Every $interval d" else f.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }
            if (freq == Frequency.CUSTOM) {
                Stepper("Repeat every", "$interval days", onMinus = { interval = (interval - 1).coerceAtLeast(2) }, onPlus = { interval = (interval + 1).coerceAtMost(30) })
            }
            if (freq == Frequency.WEEKLY) {
                Text("Repeats every ${DateUtil.shortDay(initial.startEpochDay)}", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(14.dp))

            Label("Who's on it?")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                items(data.members, key = { it.id }) { m ->
                    FilterChip(
                        selected = assignee == m.id,
                        onClick = { assignee = m.id },
                        enabled = !auto,
                        label = { Text(if (m.isYou) "${m.name} (you)" else m.name) },
                        leadingIcon = { MemberAvatar(m, 24.dp, ring = false) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.tangerineSoft, containerColor = c.surface),
                        border = null,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Label("What does done mean?")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)) {
                steps.forEachIndexed { i, step ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckBox, null, tint = c.tangerine)
                        Spacer(Modifier.width(10.dp))
                        Text(step.text, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
                        IconButton(onClick = { steps = steps - step }) { Icon(Icons.Rounded.Close, "Remove step", tint = c.muted) }
                    }
                    if (i < steps.lastIndex) HorizontalDivider(color = c.outline)
                }
                if (steps.isNotEmpty()) HorizontalDivider(color = c.outline)
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { addStep() }) { Icon(Icons.Rounded.AddBox, "Add step", tint = c.tangerine) }
                    OutlinedTextField(
                        value = newStep,
                        onValueChange = { newStep = it.take(50) },
                        placeholder = { Text("Add a step") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addStep() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Label("Options")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)) {
                OptionRow(Icons.Rounded.Groups, "Rotate between members", "Passes to the next person after each finish", rotate) { rotate = it; if (it) auto = false }
                HorizontalDivider(color = c.outline)
                OptionRow(Icons.Rounded.Person, "Auto-assign by availability", "Gives it to whoever has the lightest load", auto) { auto = it; if (it) rotate = false }
                HorizontalDivider(color = c.outline)
                OptionRow(Icons.Rounded.Schedule, "Optional timer", if (timerOn) "$timerMin minute focus timer" else "Off", timerOn) { timerOn = it }
                if (timerOn) Box(Modifier.padding(horizontal = 12.dp)) {
                    Stepper("Timer", "$timerMin min", onMinus = { timerMin = (timerMin - 5).coerceAtLeast(5) }, onPlus = { timerMin = (timerMin + 5).coerceAtMost(90) })
                }
                HorizontalDivider(color = c.outline)
                OptionRow(Icons.Rounded.ThumbUp, "Ask a member to confirm", "Rewards land after a thumbs-up", confirm) { confirm = it }
                HorizontalDivider(color = c.outline)
                OptionRow(
                    Icons.Rounded.NotificationsActive,
                    "Gentle reminder",
                    if (remindOn) "A friendly nudge at ${formatMinute(remindMinute)}" else "A friendly nudge at a set time",
                    remindOn,
                ) { remindOn = it }
                if (remindOn) Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind at", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(formatMinute(remindMinute), style = MaterialTheme.typography.titleSmall, color = c.tangerine)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(200) },
                label = { Text("Notes (optional)") },
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val room = data.rooms.firstOrNull { it.id == roomId }
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            roomId = roomId,
                            kind = if (title.trim() == initial.title) initial.kind else Catalog.guessKind(title, room?.type),
                            weight = weight,
                            frequency = freq,
                            intervalDays = if (freq == Frequency.CUSTOM) interval else 1,
                            assigneeId = assignee ?: data.members.firstOrNull()?.id,
                            rotate = rotate,
                            autoAssign = auto,
                            timerMinutes = if (timerOn) timerMin else null,
                            needsConfirm = confirm,
                            checklist = steps,
                            notes = notes,
                            remindMinuteOfDay = if (remindOn) remindMinute else null,
                        )
                    )
                },
                enabled = title.isNotBlank() && data.rooms.any { it.id == roomId },
                colors = ButtonDefaults.buttonColors(containerColor = c.tangerine),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(58.dp),
            ) { Text("Save chore", style = MaterialTheme.typography.labelLarge) }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete chore", color = c.bubblegum) }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(remindMinute / 60, remindMinute % 60, is24Hour = false)
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            title = {},
            confirmButton = {
                TextButton(onClick = { remindMinute = timeState.hour * 60 + timeState.minute; showTimePicker = false }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
        ) {
            TimePicker(timeState)
        }
    }
}

/** 540 → "9:00 AM" */
fun formatMinute(minuteOfDay: Int): String {
    val h = (minuteOfDay / 60).coerceIn(0, 23)
    val m = minuteOfDay % 60
    val amPm = if (h < 12) "AM" else "PM"
    val h12 = when (h) { 0 -> 12; 12 -> 12; else -> h - if (h > 12) 12 else 0 }
    return "$h12:${m.toString().padStart(2, '0')} $amPm"
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = TT.c.surface, unfocusedContainerColor = TT.c.surface, unfocusedBorderColor = TT.c.outline,
)

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = TT.c.ink, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = TT.c
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.ink.copy(alpha = 0.7f))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.tangerine))
    }
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = TT.c
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
        TextButton(onClick = onMinus) { Text("−", style = MaterialTheme.typography.titleLarge, color = c.tangerine) }
        Text(value, style = MaterialTheme.typography.titleSmall, color = c.ink)
        TextButton(onClick = onPlus) { Text("+", style = MaterialTheme.typography.titleLarge, color = c.tangerine) }
    }
}
