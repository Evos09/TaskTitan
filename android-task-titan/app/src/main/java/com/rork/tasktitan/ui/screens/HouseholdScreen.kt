package com.rork.tasktitan.ui.screens

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.tasktitan.data.Catalog
import com.rork.tasktitan.data.DateUtil
import com.rork.tasktitan.data.Memory
import com.rork.tasktitan.data.Room
import com.rork.tasktitan.engine.HouseScene
import com.rork.tasktitan.engine.House3DView
import com.rork.tasktitan.engine.PropSpec
import com.rork.tasktitan.ui.AppViewModel
import com.rork.tasktitan.ui.Celebration
import com.rork.tasktitan.ui.components.BrandMark
import com.rork.tasktitan.ui.components.FatProgress
import com.rork.tasktitan.ui.components.IconTile
import com.rork.tasktitan.ui.components.MemberAvatar
import com.rork.tasktitan.ui.components.SectionTitle
import com.rork.tasktitan.ui.components.SoftCard
import com.rork.tasktitan.ui.theme.Display
import com.rork.tasktitan.ui.theme.TT

/** Flame-streak weeks that earn a confetti moment. */
private val STREAK_MILESTONES = listOf(4, 8, 12)

/** Cooperative household view: one shared goal, recap, events, memories. No rankings. */
@Composable
fun HouseholdScreen(vm: AppViewModel, onRecap: () -> Unit, onSettings: () -> Unit, onPro: () -> Unit) {
    val c = TT.c
    val data by vm.data.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var addMember by remember { mutableStateOf(false) }
    var goalDialog by remember { mutableStateOf(false) }
    var streakParty by remember { mutableStateOf<Celebration?>(null) }
    val lastStreak = remember { mutableIntStateOf(stats.weekStreak) }
    LaunchedEffect(stats.weekStreak) {
        if (stats.weekStreak in STREAK_MILESTONES && stats.weekStreak > lastStreak.intValue) {
            streakParty = Celebration(
                "${stats.weekStreak} weeks on fire!",
                "Your home hit its weekly goal ${stats.weekStreak} weeks in a row. Amazing teamwork!",
            )
        }
        lastStreak.intValue = stats.weekStreak
    }
    val goalReward = when {
        stats.householdWeek >= data.weeklyGoal -> "Goal reached — the whole home earned it!"
        data.weeklyGoal - stats.householdWeek <= 10 -> "Almost at this week's goal"
        else -> "Every chore fills the bar for everyone"
    }

    fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(send, "Share"))
    }

    Box(Modifier.fillMaxSize().background(c.canvas)) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(data.householdName, style = MaterialTheme.typography.headlineMedium, color = c.ink)
                Text("A happier home together", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.MoreVert, "Settings", tint = c.ink) }
        }
        Spacer(Modifier.height(14.dp))

        SoftCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), color = c.skySoft) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(stats.householdWeek, data.weeklyGoal, Modifier.size(132.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Our week: ${stats.householdWeek} of ${data.weeklyGoal} chores", style = MaterialTheme.typography.titleLarge, color = c.ink)
                    Text(goalReward, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    if (stats.weekStreak > 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.sun)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${stats.weekStreak} week${if (stats.weekStreak == 1) "" else "s"} on fire",
                                style = MaterialTheme.typography.labelMedium, color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        data.members.take(5).forEach { MemberAvatar(it, 44.dp) }
                    }
                    TextButton(onClick = { goalDialog = true }, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Rounded.Tune, null, tint = c.sky, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Adjust goal", color = c.sky, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        SoftCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clickable(role = Role.Button, onClick = onRecap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.CalendarMonth, c.bubblegum, c.bubblegumSoft, 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Weekly recap", style = MaterialTheme.typography.titleLarge, color = c.ink)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = c.sun, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Home stayed sparkling ${stats.sparklingDaysThisWeek} day${if (stats.sparklingDaysThisWeek == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Groups, null, tint = c.ink, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (stats.contributors.size >= data.members.size && data.members.size > 1) "Everyone pitched in" else "${stats.contributors.size} helper${if (stats.contributors.size == 1) "" else "s"} so far",
                            style = MaterialTheme.typography.bodyMedium, color = c.ink,
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.muted)
            }
        }
        Spacer(Modifier.height(12.dp))

        SoftCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), color = Color(stats.season.tintHex)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Eco, null, tint = c.tangerine, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("${stats.season.name} event · ${stats.seasonDaysLeft} days left", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text(stats.season.reward, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    Spacer(Modifier.height(8.dp))
                    FatProgress(stats.seasonProgress.toFloat() / stats.seasonGoal, c.tangerine, Color.White.copy(alpha = 0.7f), Modifier.fillMaxWidth(), 10.dp)
                    Text("${minOf(stats.seasonProgress, stats.seasonGoal)} of ${stats.seasonGoal} household chores", style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
            val owned = stats.season.decorId in data.profile.owned
            if (stats.seasonUnlocked && !owned) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = { vm.claimSeason() }, colors = ButtonDefaults.buttonColors(containerColor = c.tangerine), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Claim ${Catalog.cosmetic(stats.season.decorId)?.name}")
                }
            } else if (owned) {
                Text("Unlocked — find it in Rewards › Decor", style = MaterialTheme.typography.labelMedium, color = c.mint, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(18.dp))

        SectionTitle("House memories", Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        if (data.memories.isEmpty()) {
            SoftCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Text("Finish every chore in a room to save a before & after memory here.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(data.memories.take(8), key = { it.id }) { m ->
                    val room = data.rooms.firstOrNull { it.id == m.roomId }
                    if (room != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MemoryCard(m, room, before = true)
                            MemoryCard(m, room, before = false)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        SectionTitle("Members", Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = { addMember = true }) {
                Icon(Icons.Rounded.PersonAdd, null, tint = c.tangerine)
                Spacer(Modifier.width(4.dp))
                Text("Add", color = c.tangerine)
            }
        }
        SoftCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            data.members.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(m, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (m.isYou) "${m.name} (you)" else m.name, style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Text(
                            if (m.isYou) "Your week: ${stats.yourWeek} chores · only you can see this" else "Contributing to the shared goal",
                            style = MaterialTheme.typography.bodySmall, color = c.muted,
                        )
                    }
                    if (!m.isYou) IconButton(onClick = { vm.removeMember(m.id) }) { Icon(Icons.Rounded.Close, "Remove ${m.name}", tint = c.muted) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.tangerineSoft).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Invite code", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    Text(data.inviteCode.ifBlank { "—" }, fontFamily = Display, fontSize = 26.sp, letterSpacing = 4.sp, color = c.ink)
                }
                Button(
                    onClick = { share("Join our home on Task Titan! Use invite code ${data.inviteCode} — https://tasktitan.app/join/${data.inviteCode}") },
                    colors = ButtonDefaults.buttonColors(containerColor = c.tangerine),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share link")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        SoftCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = c.sky)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Friendly challenges with other homes", style = MaterialTheme.typography.titleSmall, color = c.ink)
                    Text("Opt-in only, never inside your own home. Off by default.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Switch(
                    checked = data.settings.interHouseholdChallenge,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(interHouseholdChallenge = on) } },
                    enabled = data.settings.ageGroup != "child",
                    colors = SwitchDefaults.colors(checkedTrackColor = c.tangerine),
                )
            }
            if (data.settings.interHouseholdChallenge) {
                Text(
                    "You're opted in. Household-vs-household sprints will appear here once another home shares a challenge code with you.",
                    style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!data.isPro) {
            Surface(
                Modifier.padding(horizontal = 12.dp).fillMaxWidth().clickable(role = Role.Button, onClick = onPro),
                shape = RoundedCornerShape(24.dp), color = c.surface,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✦", fontSize = 22.sp, color = c.sun)
                    Spacer(Modifier.width(10.dp))
                    Text("Manage more than one household with Pro", style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.muted)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = {
                share(
                    "🏠 ${data.householdName} on Task Titan\n" +
                        "This week: ${stats.householdWeek} of ${data.weeklyGoal} chores together\n" +
                        "Sparkling days: ${stats.sparklingDaysThisWeek} · ${stats.season.name}: ${minOf(stats.seasonProgress, stats.seasonGoal)}/${stats.seasonGoal}\n" +
                        "Real chores, a happier 3D home ✨"
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = c.bubblegumSoft, contentColor = c.bubblegum),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(60.dp),
        ) {
            Icon(Icons.Rounded.Share, null)
            Spacer(Modifier.width(8.dp))
            Text("Share progress card", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(28.dp))
    }
    }

    streakParty?.let { party -> CelebrationOverlay(party) { streakParty = null } }

    if (addMember) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addMember = false },
            title = { Text("Add a household member") },
            text = {
                Column {
                    Text("Great for shared devices and kids. Members only see the shared goal — never each other's stats.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(name, { name = it.take(20) }, singleLine = true, label = { Text("Name") })
                }
            },
            confirmButton = { TextButton(onClick = { vm.addMember(name); addMember = false }, enabled = name.isNotBlank()) { Text("Add") } },
            dismissButton = { TextButton(onClick = { addMember = false }) { Text("Cancel") } },
        )
    }
    if (goalDialog) {
        var goal by remember { mutableIntStateOf(data.weeklyGoal) }
        AlertDialog(
            onDismissRequest = { goalDialog = false },
            title = { Text("Weekly household goal") },
            text = { Stepper("Chores per week", "$goal", onMinus = { goal = (goal - 5).coerceAtLeast(5) }, onPlus = { goal = (goal + 5).coerceAtMost(300) }) },
            confirmButton = { TextButton(onClick = { vm.setWeeklyGoal(goal); goalDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { goalDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProgressRing(value: Int, goal: Int, modifier: Modifier = Modifier) {
    val c = TT.c
    val p by animateFloatAsState((value.toFloat() / goal).coerceIn(0f, 1f), tween(if (TT.reducedMotion) 0 else 900), label = "ring")
    Box(modifier.semantics { contentDescription = "$value of $goal chores this week" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.12f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color(0xFFCFE2EE), 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(c.tangerine, 135f, 270f * p, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$value", fontFamily = Display, fontSize = 34.sp, color = c.tangerine)
                Text(" / $goal", fontFamily = Display, fontSize = 16.sp, color = c.ink, modifier = Modifier.padding(bottom = 5.dp))
            }
            Text("chores", style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
    }
}

@Composable
private fun MemoryCard(m: Memory, room: Room, before: Boolean) {
    val c = TT.c
    val solo = remember(room) { listOf(room.copy(floor = 0, x = 0, y = 0)) }
    val props = remember(m) { m.messKinds.mapIndexed { i, k -> PropSpec("${m.id}_$i", solo[0].id, k) } }
    val pending = remember(m, before) { if (before) props.map { it.choreId }.toSet() else emptySet() }
    Column(Modifier.width(150.dp).clip(RoundedCornerShape(22.dp)).background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(120.dp).background(if (before) Color(0xFFF4EDE1) else Color(0xFFE6F5FF))) {
            House3DView(
                HouseScene(rooms = solo, topFloor = 0, props = props, pendingIds = pending, roomCondition = if (before) emptyMap() else mapOf(solo[0].id to 100)),
                Modifier.fillMaxSize(),
                interactive = false,
                animated = false,
                description = "${room.name} ${if (before) "before" else "after"}",
            )
            if (!before) Box(Modifier.padding(8.dp).clip(CircleShape).background(c.mint).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("✦", color = Color.White, fontSize = 12.sp)
            }
        }
        Text(
            if (before) "${m.roomName}: Before" else "After · ${DateUtil.label(m.epochDay)}",
            style = MaterialTheme.typography.labelLarge, color = c.ink,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
