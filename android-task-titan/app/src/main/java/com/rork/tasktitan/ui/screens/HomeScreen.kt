package com.rork.tasktitan.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.tasktitan.data.DateUtil
import com.rork.tasktitan.data.GameRules
import com.rork.tasktitan.engine.CameraMode
import com.rork.tasktitan.engine.HouseScene
import com.rork.tasktitan.engine.House3DView
import com.rork.tasktitan.engine.PropSpec
import com.rork.tasktitan.ui.AppViewModel
import com.rork.tasktitan.ui.ChoreStatus
import com.rork.tasktitan.ui.components.BrandMark
import com.rork.tasktitan.ui.components.FatProgress
import com.rork.tasktitan.ui.components.HudStrip
import com.rork.tasktitan.ui.components.IconTile
import com.rork.tasktitan.ui.components.MemberAvatar
import com.rork.tasktitan.ui.components.SectionTitle
import com.rork.tasktitan.ui.components.SoftCard
import com.rork.tasktitan.ui.components.WeightDots
import com.rork.tasktitan.ui.components.roomAccent
import com.rork.tasktitan.ui.components.roomIcon
import com.rork.tasktitan.ui.components.roomTint
import com.rork.tasktitan.ui.theme.TT
import com.rork.tasktitan.data.RoomType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onEditHouse: () -> Unit,
    onSettings: () -> Unit,
    onSeeChores: () -> Unit,
    onRecap: () -> Unit,
) {
    val c = TT.c
    val data by vm.data.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val recent by vm.recentClean.collectAsStateWithLifecycle()
    val focus by vm.avatarFocus.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    var floor by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(CameraMode.ORBIT) }
    var labels by rememberSaveable { mutableStateOf(false) }
    var bell by remember { mutableStateOf(false) }

    val maxFloor = (data.floors - 1).coerceAtLeast(0)
    if (floor > maxFloor) floor = maxFloor

    LaunchedEffect(focus) {
        val f = focus ?: return@LaunchedEffect
        data.rooms.firstOrNull { it.id == f.first }?.let { floor = it.floor }
    }

    val props = remember(stats.todayChores) { stats.todayChores.map { PropSpec(it.chore.id, it.chore.roomId, it.chore.kind) } }
    val pending = remember(stats.todayChores) { stats.todayChores.filter { !it.isDone }.map { it.chore.id }.toSet() }
    val name = stats.you?.name ?: "You"
    val focusRoom = focus?.let { f -> data.rooms.firstOrNull { it.id == f.first } }
    val status = when {
        focusRoom != null && System.currentTimeMillis() - (focus?.second ?: 0) < 4000 -> "$name is celebrating in the ${focusRoom.name.lowercase()}!"
        stats.houseCondition >= 85 -> "$name is relaxing in a sparkling home"
        stats.houseCondition >= 50 -> "$name is relaxing in a tidy home"
        else -> "$name is resting — ready when you are"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.canvas)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Task Titan", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                Text("A happier home together", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
            IconButton(onClick = { bell = true }) {
                Box {
                    Icon(Icons.Rounded.Notifications, "Updates", tint = c.ink)
                    val confirmCount = data.completions.count { !it.confirmed }
                    if (confirmCount > 0) Box(Modifier.size(9.dp).clip(CircleShape).background(c.bubblegum).align(Alignment.TopEnd))
                }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.MoreVert, "Settings", tint = c.ink) }
        }
        Spacer(Modifier.height(12.dp))
        HudStrip(stats, Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(420.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(skyGradient()),
        ) {
            House3DView(
                scene = HouseScene(
                    rooms = data.rooms,
                    topFloor = floor,
                    props = props,
                    pendingIds = pending,
                    recentClean = recent,
                    look = data.profile.look,
                    tier = stats.tier,
                    decor = data.profile.equippedDecor,
                    focus = focus,
                    roomCondition = stats.roomCondition,
                ),
                modifier = Modifier.fillMaxSize(),
                cameraMode = mode,
                showLabels = labels,
                description = "Your 3D home, ${floorLabel(floor)}. $status. House condition ${stats.houseCondition} percent.",
            )
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.92f), modifier = Modifier.weight(1f, fill = false)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Home, null, tint = c.sky, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        AnimatedContent(status, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") {
                            Text(it, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (labels) c.mint else c.mintSoft,
                    modifier = Modifier.clickable(role = Role.Switch, onClickLabel = "Toggle room condition labels") { labels = !labels },
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = if (labels) Color.White else c.mint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("House condition", style = MaterialTheme.typography.labelSmall, color = if (labels) Color.White else c.ink)
                            Text(
                                "${GameRules.conditionLabel(stats.houseCondition)} ${stats.houseCondition}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (labels) Color.White else c.ink,
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                for (i in maxFloor downTo 0) {
                    val on = i == floor
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) c.tangerine else Color.White.copy(alpha = 0.9f))
                            .clickable(role = Role.Tab) { floor = i; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) { Text("Floor ${i + 1}", style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else c.ink) }
                }
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.9f))
                        .clickable(role = Role.Button, onClick = onEditHouse)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Edit, null, tint = c.ink, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit house", style = MaterialTheme.typography.labelLarge, color = c.ink)
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CameraMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(m.label) },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = 0.9f),
                            selectedContainerColor = c.sky,
                            selectedLabelColor = Color.White,
                        ),
                        border = null,
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.9f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = c.tangerine, modifier = Modifier.size(18.dp))
                        Text(if (stats.streak > 0) "${stats.streak}" else "New", style = MaterialTheme.typography.labelLarge, color = c.ink)
                    }
                }
            }
        }
        if (stats.streak == 0) {
            Text(
                "Start a new streak today — any chore counts.",
                style = MaterialTheme.typography.labelMedium, color = c.muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
        } else Spacer(Modifier.height(12.dp))

        SoftCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Groups, c.bubblegum, c.bubblegumSoft, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Household goal: ${stats.householdWeek} of ${data.weeklyGoal} chores", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    FatProgress(stats.householdWeek.toFloat() / data.weeklyGoal, c.tangerine, c.tangerineSoft, Modifier.fillMaxWidth(), 14.dp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    data.members.take(4).forEach { MemberAvatar(it, 40.dp) }
                }
                Spacer(Modifier.width(10.dp))
                Text(contributorsLine(stats.contributors.map { it.name }, data.members.size), style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(18.dp), color = c.bubblegumSoft,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onRecap),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = c.bubblegum, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Weekly recap", style = MaterialTheme.typography.labelLarge, color = c.bubblegum)
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.bubblegum)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        SectionTitle("Today's chores", Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = onSeeChores) {
                Text("See all", color = c.tangerine, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.tangerine)
            }
        }
        val todo = stats.todayChores.filter { !it.isDone }
        val done = stats.todayChores.filter { it.isDone }
        if (stats.todayChores.isEmpty()) {
            SoftCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), color = c.skySoft) {
                Text("Nothing scheduled today", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text("Add chores from the Chores tab — templates make it quick.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(todo.take(6), key = { it.chore.id }) { s ->
                    QuestCard(s) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.completeChore(s.chore.id, stats.you?.id)
                    }
                }
                items(done.take(4), key = { "d" + it.chore.id }) { s -> QuestCard(s, onDone = null) }
                if (todo.isNotEmpty()) item {
                    Column(
                        Modifier
                            .width(130.dp)
                            .height(132.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(c.bubblegumSoft)
                            .clickable(role = Role.Button, onClick = onSeeChores)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(Icons.Rounded.Schedule, null, tint = c.bubblegum, modifier = Modifier.size(30.dp))
                        Text("${todo.size} chores still waiting", style = MaterialTheme.typography.titleSmall, color = c.ink)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (bell) {
        ModalBottomSheet(onDismissRequest = { bell = false }, containerColor = c.canvas) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Ready when you are", style = MaterialTheme.typography.headlineSmall, color = c.ink)
                Spacer(Modifier.height(10.dp))
                val waiting = data.completions.filter { !it.confirmed }
                if (waiting.isEmpty()) {
                    Text("No thumbs-ups waiting. ${stats.waitingCount} chores still open today.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
                waiting.forEach { comp ->
                    val chore = data.chores.firstOrNull { it.id == comp.choreId }
                    val who = data.members.firstOrNull { it.id == comp.memberId }?.name ?: "Someone"
                    SoftCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(chore?.title ?: "Chore", style = MaterialTheme.typography.titleMedium, color = c.ink)
                                Text("$who finished this · ${DateUtil.label(comp.epochDay)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                            TextButton(onClick = { vm.confirmCompletion(comp.id) }) { Text("Looks great", color = c.tangerine) }
                        }
                    }
                }
            }
        }
    }
}

private fun floorLabel(i: Int) = "floor ${i + 1}"

private fun contributorsLine(names: List<String>, total: Int): String = when {
    names.isEmpty() && total <= 1 -> "Invite your household from the Household tab"
    names.isEmpty() -> "Everyone's ready to pitch in this week"
    names.size == 1 -> "${names[0]} is contributing this week"
    else -> names.dropLast(1).joinToString(", ") + " and ${names.last()} are all contributing"
}

@Composable
private fun skyGradient(): Brush {
    val hour = remember { DateUtil.hourOfDay() }
    return when {
        hour < 6 || hour >= 20 -> Brush.verticalGradient(listOf(Color(0xFF2C3A78), Color(0xFF5867B0), Color(0xFF8E8FD0)))
        hour >= 17 -> Brush.verticalGradient(listOf(Color(0xFFFFC9A3), Color(0xFFFFE3CC), Color(0xFFDDEFFF)))
        else -> Brush.verticalGradient(listOf(Color(0xFFCFEBFF), Color(0xFFE6F5FF), Color(0xFFF3FAFF)))
    }
}

/** Horizontal quest card for a chore with tap-to-complete. */
@Composable
fun QuestCard(s: ChoreStatus, onDone: (() -> Unit)?) {
    val c = TT.c
    val type = s.room?.type ?: RoomType.CUSTOM
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "press")
    Column(
        Modifier
            .scale(scale)
            .widthIn(min = 170.dp, max = 190.dp)
            .height(132.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (s.isDone) c.mintSoft else c.surface)
            .then(if (onDone != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Mark done", onClick = onDone) else Modifier)
            .padding(14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${s.chore.title}, ${s.room?.name ?: ""}, ${GameRules.weightLabel(s.chore.weight)}" + if (s.isDone) ", done" else ", tap to mark done"
            },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(roomIcon(type), roomAccent(type), roomTint(type), 46.dp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (s.isDone) c.mint else c.tangerineSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = if (s.isDone) Color.White else c.tangerine, modifier = Modifier.size(20.dp))
            }
        }
        Column {
            Text(s.chore.title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            if (s.awaitingConfirm) Text("Waiting for a thumbs-up", style = MaterialTheme.typography.labelMedium, color = c.sky)
            else if (s.isDone) Text("Done · +${s.completion?.xp ?: 0} XP", style = MaterialTheme.typography.labelMedium, color = c.mint)
            else WeightDots(s.chore.weight)
        }
    }
}
