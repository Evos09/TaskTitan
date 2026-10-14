package com.rork.tasktitan.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.tasktitan.data.DateUtil
import com.rork.tasktitan.ui.AppViewModel
import com.rork.tasktitan.ui.components.IconTile
import com.rork.tasktitan.ui.components.SoftCard
import com.rork.tasktitan.ui.components.roomAccent
import com.rork.tasktitan.ui.components.roomIcon
import com.rork.tasktitan.ui.components.roomTint
import com.rork.tasktitan.ui.theme.TT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = TT.c
    val data by vm.data.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val weekStart = DateUtil.weekStart(stats.today)
    val confirmed = data.completions.filter { it.confirmed && it.epochDay in weekStart..stats.today }
    val perDay = (0..6).map { i -> confirmed.count { it.epochDay == weekStart + i } }
    val maxDay = (perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val choreRoom = data.chores.associate { it.id to it.roomId }
    val topRooms = confirmed.groupingBy { choreRoom[it.choreId] }.eachCount()
        .entries.sortedByDescending { it.value }.mapNotNull { e -> data.rooms.firstOrNull { it.id == e.key }?.let { it to e.value } }.take(3)
    val memories = data.memories.count { it.epochDay >= weekStart }
    val yourXp = confirmed.filter { it.memberId == stats.you?.id }.sumOf { it.xp }

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Weekly recap", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = c.canvas),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SoftCard(Modifier.fillMaxWidth(), color = c.skySoft) {
                Text("Week of ${DateUtil.label(weekStart)}", style = MaterialTheme.typography.labelLarge, color = c.sky)
                Text("${stats.householdWeek} chores, together", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                Text(
                    if (stats.householdWeek >= data.weeklyGoal) "You hit the household goal of ${data.weeklyGoal}! 🎉" else "${data.weeklyGoal - stats.householdWeek} to go for the household goal — no pressure.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    perDay.forEachIndexed { i, n ->
                        val h by animateFloatAsState(n.toFloat() / maxDay, tween(if (TT.reducedMotion) 0 else 700 + i * 60), label = "bar")
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text("$n", style = MaterialTheme.typography.labelSmall, color = c.ink)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((h * 0.75f).coerceAtLeast(0.04f))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (weekStart + i == stats.today) c.tangerine else c.sky.copy(alpha = 0.6f))
                                    .semantics { contentDescription = "${DateUtil.shortDay(weekStart + i)}: $n chores" },
                            )
                            Text(DateUtil.shortDay(weekStart + i).take(1), style = MaterialTheme.typography.labelSmall, color = c.muted)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.AutoAwesome, "${stats.sparklingDaysThisWeek}", "Sparkling days", Modifier.weight(1f))
                StatTile(Icons.Rounded.PhotoAlbum, "$memories", "New memories", Modifier.weight(1f))
            }
            if (topRooms.isNotEmpty()) {
                SoftCard(Modifier.fillMaxWidth()) {
                    Text("Most cared-for rooms", style = MaterialTheme.typography.titleLarge, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    topRooms.forEach { (room, n) ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(roomIcon(room.type), roomAccent(room.type), roomTint(room.type), 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(room.name, style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                            Text("$n chores", style = MaterialTheme.typography.labelLarge, color = c.muted)
                        }
                    }
                }
            }
            SoftCard(Modifier.fillMaxWidth(), color = c.tangerineSoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = c.tangerine, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Just for you — nobody else sees this", style = MaterialTheme.typography.labelLarge, color = c.tangerine)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat("${stats.yourWeek}", "chores", Modifier.weight(1f))
                    MiniStat("+$yourXp", "XP", Modifier.weight(1f))
                    MiniStat(if (stats.streak > 0) "${stats.streak}" else "New", "streak", Modifier.weight(1f))
                }
                if (stats.streak == 0) Text("Start a new streak today — any chore counts.", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp))
            }
            SoftCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, null, tint = c.sun)
                    Spacer(Modifier.width(8.dp))
                    Text("Coming up", style = MaterialTheme.typography.titleMedium, color = c.ink)
                }
                val upcoming = (1..7).sumOf { d -> data.chores.count { com.rork.tasktitan.data.GameRules.isDue(it, stats.today + d) } }
                Text("$upcoming chores scheduled over the next 7 days. Ready when you are.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    val c = TT.c
    SoftCard(modifier) {
        Icon(icon, null, tint = c.bubblegum)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = c.ink)
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier) {
    val c = TT.c
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (label == "streak") Icon(Icons.Rounded.LocalFireDepartment, null, tint = c.tangerine, modifier = Modifier.size(18.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = c.ink)
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
}
