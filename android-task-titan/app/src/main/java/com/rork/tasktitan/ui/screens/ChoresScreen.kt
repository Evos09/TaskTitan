package com.rork.tasktitan.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.tasktitan.data.AppData
import com.rork.tasktitan.data.Catalog
import com.rork.tasktitan.data.Chore
import com.rork.tasktitan.data.ChoreKind
import com.rork.tasktitan.data.DateUtil
import com.rork.tasktitan.data.Frequency
import com.rork.tasktitan.data.GameRules
import com.rork.tasktitan.data.RoomType
import com.rork.tasktitan.reminders.ReminderScheduler
import com.rork.tasktitan.ui.AppViewModel
import com.rork.tasktitan.ui.components.IconTile
import com.rork.tasktitan.ui.components.MemberAvatar
import com.rork.tasktitan.ui.components.SoftCard
import com.rork.tasktitan.ui.components.WeightDots
import com.rork.tasktitan.ui.components.roomAccent
import com.rork.tasktitan.ui.components.roomIcon
import com.rork.tasktitan.ui.components.roomTint
import com.rork.tasktitan.ui.theme.TT
import kotlinx.coroutines.delay

private sealed interface ChoreSheet {
    data object Templates : ChoreSheet
    data class Editor(val chore: Chore, val isNew: Boolean) : ChoreSheet
    data class Detail(val choreId: String) : ChoreSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoresScreen(vm: AppViewModel) {
    val c = TT.c
    val data by vm.data.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val today = stats.today
    var day by remember { mutableLongStateOf(today) }
    var showAll by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ChoreSheet?>(null) }
    val weekStart = DateUtil.weekStart(today)

    val roomsById = remember(data.rooms) { data.rooms.associateBy { it.id } }
    val listed: List<Chore> = remember(data.chores, day, showAll) {
        if (showAll) data.chores else data.chores.filter { GameRules.isDue(it, day) }
    }
    val doneIds = remember(data.completions, day) { data.completions.filter { it.epochDay == day }.map { it.choreId }.toSet() }
    val grouped = remember(listed, data.rooms) {
        data.rooms.mapNotNull { r -> listed.filter { it.roomId == r.id }.takeIf { it.isNotEmpty() }?.let { r to it } }
    }

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Chores", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                val doneToday = stats.todayChores.count { it.isDone }
                Text(
                    if (stats.todayChores.isEmpty()) "Nothing due today" else "$doneToday of ${stats.todayChores.size} done today · ${stats.waitingCount} still waiting",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0..6) {
                        val d = weekStart + i
                        val on = d == day && !showAll
                        val bg by animateColorAsState(if (on) c.tangerine else c.surface, label = "day")
                        val dueCount = data.chores.count { GameRules.isDue(it, d) }
                        val doneCount = data.completions.count { it.epochDay == d }
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(bg)
                                .clickable(role = Role.Tab) { day = d; showAll = false }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(DateUtil.shortDay(d), style = MaterialTheme.typography.labelSmall, color = if (on) Color.White else c.muted)
                            Text("${DateUtil.dayOfMonth(d)}", style = MaterialTheme.typography.titleLarge, color = if (on) Color.White else if (d == today) c.tangerine else c.ink)
                            Box(
                                Modifier.size(6.dp).clip(CircleShape).background(
                                    when {
                                        dueCount == 0 -> Color.Transparent
                                        doneCount >= dueCount && d <= today -> if (on) Color.White else c.mint
                                        else -> if (on) Color.White.copy(alpha = 0.6f) else c.outline
                                    }
                                )
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showAll, onClick = { showAll = false },
                        label = { Text(if (day == today) "Today" else DateUtil.label(day)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.tangerineSoft, containerColor = c.surface),
                        border = null,
                    )
                    FilterChip(
                        selected = showAll, onClick = { showAll = true },
                        label = { Text("All chores (${data.chores.size})") },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.tangerineSoft, containerColor = c.surface),
                        border = null,
                    )
                }
            }
            if (!showAll && day > today) {
                item { Text("Ready when you are — these open up on ${DateUtil.shortDay(day)}.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
            }
            if (grouped.isEmpty()) {
                item {
                    SoftCard(Modifier.fillMaxWidth(), color = c.skySoft) {
                        Text(if (showAll) "No chores yet" else "A free day!", style = MaterialTheme.typography.titleLarge, color = c.ink)
                        Text("Tap Add chore to pick from room templates or write your own.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    }
                }
            }
            grouped.forEach { (room, chores) ->
                item(key = "h" + room.id) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(roomIcon(room.type), null, tint = roomAccent(room.type), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(room.name, style = MaterialTheme.typography.titleLarge, color = c.ink)
                        if (data.floors > 1) Text("  · Floor ${room.floor + 1}", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    }
                }
                items(chores, key = { it.id }) { chore ->
                    val done = chore.id in doneIds && !showAll
                    ChoreRow(
                        data = data,
                        chore = chore,
                        done = done,
                        canComplete = !showAll && day == today,
                        onToggle = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (done) vm.undoCompletion(chore.id) else vm.completeChore(chore.id, stats.you?.id)
                        },
                        onOpen = { sheet = ChoreSheet.Detail(chore.id) },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { sheet = ChoreSheet.Templates },
            containerColor = c.tangerine,
            contentColor = Color.White,
            shape = RoundedCornerShape(24.dp),
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Add chore", style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    when (val s = sheet) {
        is ChoreSheet.Templates -> TemplatePickerSheet(
            data = data,
            onPick = { tpl, room ->
                sheet = ChoreSheet.Editor(vm.fromTemplate(tpl, room, stats.you?.id), true)
            },
            onScratch = {
                val room = data.rooms.firstOrNull()
                if (room != null) {
                    sheet = ChoreSheet.Editor(
                        Chore(GameRules.newId(), "", room.id, ChoreKind.GENERAL, 2, Frequency.DAILY, 1, today, stats.you?.id), true
                    )
                }
            },
            onDismiss = { sheet = null },
        )
        is ChoreSheet.Editor -> ChoreEditorSheet(
            data = data,
            initial = s.chore,
            isNew = s.isNew,
            onSave = { vm.saveChore(it); sheet = null },
            onDelete = if (s.isNew) null else ({ vm.deleteChore(s.chore.id); sheet = null }),
            onDismiss = { sheet = null },
        )
        is ChoreSheet.Detail -> {
            val chore = data.chores.firstOrNull { it.id == s.choreId }
            if (chore == null) sheet = null else ChoreDetailSheet(
                vm = vm, data = data, chore = chore, today = today,
                onEdit = { sheet = ChoreSheet.Editor(chore, false) },
                onDismiss = { sheet = null },
            )
        }
        null -> Unit
    }
}

@Composable
private fun ChoreRow(data: AppData, chore: Chore, done: Boolean, canComplete: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = TT.c
    val assignee = data.members.firstOrNull { it.id == chore.assigneeId }
    val checkScale by animateFloatAsState(if (done) 1.1f else 1f, spring(dampingRatio = 0.4f), label = "check")
    val reward = GameRules.reward(chore.weight)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (done) c.mintSoft else c.surface)
            .clickable(role = Role.Button, onClickLabel = "Open details", onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .scale(checkScale)
                .size(44.dp)
                .clip(CircleShape)
                .background(if (done) c.mint else c.tangerineSoft)
                .then(if (canComplete) Modifier.clickable(role = Role.Checkbox, onClickLabel = if (done) "Undo" else "Mark done", onClick = onToggle) else Modifier)
                .semantics { contentDescription = if (done) "Done" else "Not done yet" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, null, tint = if (done) Color.White else c.tangerine.copy(alpha = if (canComplete) 1f else 0.4f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                chore.title,
                style = MaterialTheme.typography.titleMedium,
                color = c.ink,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeightDots(chore.weight)
                Spacer(Modifier.width(8.dp))
                Text("+${reward.xp} XP", style = MaterialTheme.typography.labelMedium, color = c.sky)
                if (chore.rotate) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Groups, "Rotates", tint = c.muted, modifier = Modifier.size(16.dp)) }
                if (chore.timerMinutes != null) { Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.Schedule, "Has timer", tint = c.muted, modifier = Modifier.size(16.dp)) }
                if (chore.needsConfirm) { Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.ThumbUp, "Needs confirmation", tint = c.muted, modifier = Modifier.size(15.dp)) }
            }
            Text(
                frequencyText(chore) + if (chore.checklist.isNotEmpty()) " · ${chore.checklist.size} steps" else "",
                style = MaterialTheme.typography.labelSmall, color = c.muted,
            )
            nextNudgeLabel(chore, data)?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = c.sky)
            }
        }
        if (assignee != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MemberAvatar(assignee, 34.dp)
                Text(assignee.name, style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1)
            }
        }
    }
}

fun frequencyText(chore: Chore): String = when (chore.frequency) {
    Frequency.DAILY -> "Daily"
    Frequency.WEEKLY -> "Weekly · ${DateUtil.shortDay(chore.startEpochDay)}"
    Frequency.MONTHLY -> "Monthly · day ${DateUtil.dayOfMonth(chore.startEpochDay)}"
    Frequency.CUSTOM -> "Every ${chore.intervalDays} days"
}

/** "Next nudge 9:00 AM" under chore rows with a reminder; null when silenced (toggle or pause). */
fun nextNudgeLabel(chore: Chore, data: AppData): String? {
    val minute = chore.remindMinuteOfDay ?: return null
    if (!ReminderScheduler.active(data)) return null
    val doneDays = data.completions.filter { it.choreId == chore.id }.map { it.epochDay }.toSet()
    var day = DateUtil.today()
    repeat(371) {
        if (GameRules.isDue(chore, day) && day !in doneDays) return "Next nudge ${formatMinute(minute)}"
        day++
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePickerSheet(
    data: AppData,
    onPick: (com.rork.tasktitan.data.ChoreTemplate, com.rork.tasktitan.data.Room) -> Unit,
    onScratch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = TT.c
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var roomIdx by remember { mutableIntStateOf(0) }
    val room = data.rooms.getOrNull(roomIdx)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.canvas) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text("Chore templates", style = MaterialTheme.typography.headlineMedium, color = c.ink)
            Text("Pick a room, tap a template, tweak and save.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.tangerine).clickable(role = Role.Button, onClick = onScratch).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Edit, null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Write my own chore", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            Spacer(Modifier.height(14.dp))
            if (data.rooms.isEmpty()) {
                Text("Add rooms to your house first.", color = c.muted)
            } else {
                Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.rooms.forEachIndexed { i, r ->
                        FilterChip(
                            selected = i == roomIdx,
                            onClick = { roomIdx = i },
                            label = { Text(r.name) },
                            leadingIcon = { Icon(roomIcon(r.type), null, tint = roomAccent(r.type), modifier = Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = roomTint(r.type), containerColor = c.surface),
                            border = null,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (room != null) {
                    val list = Catalog.templatesFor(room.type).ifEmpty { Catalog.templatesFor(RoomType.CUSTOM) }
                    val existing = data.chores.filter { it.roomId == room.id }.map { it.title }.toSet()
                    list.forEach { t ->
                        val r = GameRules.reward(t.weight)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp)).background(c.surface)
                                .clickable(role = Role.Button) { onPick(t, room) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconTile(roomIcon(room.type), roomAccent(room.type), roomTint(room.type), 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    WeightDots(t.weight)
                                    Text("  · ${t.frequency.label} · +${r.xp} XP", style = MaterialTheme.typography.labelSmall, color = c.muted)
                                }
                            }
                            if (t.title in existing) Text("Added", style = MaterialTheme.typography.labelMedium, color = c.mint)
                            else Icon(Icons.Rounded.Add, "Add", tint = c.tangerine)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Modifier.horizontalScrollRow(): Modifier = this.fillMaxWidth().horizontalScroll(rememberScrollState())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoreDetailSheet(vm: AppViewModel, data: AppData, chore: Chore, today: Long, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val c = TT.c
    val haptics = LocalHapticFeedback.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val room = data.rooms.firstOrNull { it.id == chore.roomId }
    val completion = data.completions.firstOrNull { it.choreId == chore.id && it.epochDay == today }
    val due = GameRules.isDue(chore, today)
    var ticked by remember { mutableStateOf(setOf<String>()) }
    var who by remember { mutableStateOf(data.members.firstOrNull { it.isYou }?.id) }
    val totalSec = (chore.timerMinutes ?: 0) * 60
    var remaining by remember { mutableIntStateOf(totalSec) }
    var running by remember { mutableStateOf(false) }
    val reward = GameRules.reward(chore.weight)

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining--
        }
        if (remaining == 0 && running) { running = false; haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.canvas) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val type = room?.type ?: RoomType.CUSTOM
                IconTile(roomIcon(type), roomAccent(type), roomTint(type), 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(chore.title, style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    Text("${room?.name ?: ""} · ${frequencyText(chore)}", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit chore", tint = c.ink) }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.tangerineSoft).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WeightDots(chore.weight)
                Spacer(Modifier.weight(1f))
                Text("+${reward.xp} XP · +${reward.coins} coins · +${reward.energy} energy", style = MaterialTheme.typography.labelLarge, color = c.ink)
            }
            if (chore.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(chore.notes, style = MaterialTheme.typography.bodyMedium, color = c.ink)
            }
            if (chore.checklist.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("What done means", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)) {
                    chore.checklist.forEach { item ->
                        val on = item.id in ticked || completion != null
                        Row(
                            Modifier.fillMaxWidth()
                                .toggleable(on, enabled = completion == null, role = Role.Checkbox) { ticked = if (it) ticked + item.id else ticked - item.id }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (on) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = c.tangerine)
                            Spacer(Modifier.width(10.dp))
                            Text(item.text, style = MaterialTheme.typography.bodyLarge, color = c.ink)
                        }
                    }
                }
            }
            if (chore.timerMinutes != null && completion == null) {
                Spacer(Modifier.height(14.dp))
                SoftCard(Modifier.fillMaxWidth(), color = c.skySoft) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Optional timer", style = MaterialTheme.typography.labelMedium, color = c.muted)
                            Text("%d:%02d".format(remaining / 60, remaining % 60), style = MaterialTheme.typography.displaySmall, color = c.sky)
                        }
                        IconButton(onClick = { if (remaining == 0) remaining = totalSec; running = !running }) {
                            Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (running) "Pause timer" else "Start timer", tint = c.sky, modifier = Modifier.size(36.dp))
                        }
                    }
                    if (remaining == 0) Text("Time's up — nice focus!", style = MaterialTheme.typography.labelMedium, color = c.mint)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (completion != null) {
                val doer = data.members.firstOrNull { it.id == completion.memberId }?.name ?: "Someone"
                SoftCard(Modifier.fillMaxWidth(), color = c.mintSoft) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = c.mint)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Done by $doer", style = MaterialTheme.typography.titleMedium, color = c.ink)
                            Text(if (completion.confirmed) "+${completion.xp} XP earned" else "Waiting for a thumbs-up", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        if (!completion.confirmed && completion.memberId != data.members.firstOrNull { it.isYou }?.id) {
                            TextButton(onClick = { vm.confirmCompletion(completion.id) }) { Text("Looks great", color = c.tangerine) }
                        }
                    }
                }
                TextButton(onClick = { vm.undoCompletion(chore.id) }, modifier = Modifier.fillMaxWidth()) { Text("Undo", color = c.muted) }
            } else if (due) {
                Text("Who did it?", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.members.forEach { m ->
                        FilterChip(
                            selected = who == m.id, onClick = { who = m.id },
                            label = { Text(if (m.isYou) "${m.name} (you)" else m.name) },
                            leadingIcon = { MemberAvatar(m, 24.dp, ring = false) },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.tangerineSoft, containerColor = c.surface),
                            border = null,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.completeChore(chore.id, who)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.tangerine),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Mark done", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Text("Not due today — ready when you are on its next day.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
