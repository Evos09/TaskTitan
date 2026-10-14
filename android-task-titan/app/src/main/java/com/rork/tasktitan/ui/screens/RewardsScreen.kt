package com.rork.tasktitan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.tasktitan.data.AppData
import com.rork.tasktitan.data.Catalog
import com.rork.tasktitan.data.Cosmetic
import com.rork.tasktitan.data.ItemCategory
import com.rork.tasktitan.engine.AvatarView
import com.rork.tasktitan.engine.Pose
import com.rork.tasktitan.ui.AppViewModel
import com.rork.tasktitan.ui.components.FatProgress
import com.rork.tasktitan.ui.components.SectionTitle
import com.rork.tasktitan.ui.components.SoftCard
import com.rork.tasktitan.ui.theme.Display
import com.rork.tasktitan.ui.theme.TT
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(vm: AppViewModel, onPro: () -> Unit) {
    val c = TT.c
    val data by vm.data.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Look") + ItemCategory.entries.map { it.label }
    val look = data.profile.look

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Rewards", style = MaterialTheme.typography.headlineLarge, color = c.ink, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(18.dp), color = c.surface) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MonetizationOn, null, tint = c.sun)
                        Spacer(Modifier.width(6.dp))
                        Text("%,d".format(stats.coins), fontFamily = Display, fontSize = 18.sp, color = c.ink)
                    }
                }
            }

            Box(
                Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFE3F0), Color(0xFFFFF1E2), Color(0xFFFFF8EC)))),
            ) {
                AvatarView(look, stats.tier, Modifier.fillMaxSize().padding(top = 24.dp), pose = Pose.IDLE, description = "Your avatar, ${stats.health.label.lowercase()}, energy ${stats.tier.label}", health = stats.health)
                Column(Modifier.padding(16.dp)) {
                    Text("${stats.you?.name ?: "You"} · Lv ${stats.level.level}", style = MaterialTheme.typography.titleLarge, color = c.ink)
                    Text("${stats.health.label} · Energy ${stats.tier.label}", style = MaterialTheme.typography.labelLarge, color = if (stats.health == com.rork.tasktitan.data.Health.HEALTHY) c.mint else c.bubblegum)
                }
                Text(
                    "Drag to spin",
                    style = MaterialTheme.typography.labelSmall, color = c.muted,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                )
            }
            Text(
                "Health follows today's chores: finish yours and you feel great again. Energy and levels only ever go up.",
                style = MaterialTheme.typography.bodySmall, color = c.muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            SectionTitle("Daily quests", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            SoftCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                stats.quests.forEach { q ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TaskAlt, null, tint = if (q.progress >= 1f) c.mint else c.muted)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(q.title, style = MaterialTheme.typography.titleSmall, color = c.ink)
                            Spacer(Modifier.height(4.dp))
                            FatProgress(q.progress, c.sky, c.skySoft, Modifier.fillMaxWidth(), 8.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        when {
                            q.claimed -> Text("Claimed", style = MaterialTheme.typography.labelMedium, color = c.mint)
                            q.progress >= 1f -> Button(
                                onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.claimQuest(q.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = c.tangerine), shape = RoundedCornerShape(14.dp),
                            ) { Text("+${q.coins}") }
                            else -> Text("+${q.coins}", style = MaterialTheme.typography.labelLarge, color = c.muted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            SectionTitle("Closet", Modifier.padding(horizontal = 16.dp))
            PrimaryScrollableTabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = c.tangerine,
                edgePadding = 12.dp,
                divider = {},
                indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab, matchContentSize = true), color = c.tangerine) },
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, style = MaterialTheme.typography.labelLarge) }, selectedContentColor = c.tangerine, unselectedContentColor = c.muted)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (tab == 0) {
                LookEditor(data, onChange = { vm.setLook(it) })
            } else {
                val cat = ItemCategory.entries[tab - 1]
                val items = Catalog.cosmetics.filter { it.category == cat && (!it.seasonal || it.id in data.profile.owned) }
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { item ->
                                ItemTile(item, data, stats.level.level, Modifier.weight(1f)) {
                                    if (item.id in data.profile.owned) {
                                        vm.equip(item.id); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else if (item.pro && !data.isPro) {
                                        onPro()
                                    } else {
                                        val err = vm.buy(item.id)
                                        if (err != null) scope.launch { snack.showSnackbar(err) } else haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (cat == ItemCategory.DECOR) {
                        Text("Decor shows up in your living room in 3D. Seasonal decor unlocks through household events.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            SectionTitle("Milestones", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            SoftCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Catalog.achievements.forEach { a ->
                    val unlocked = a.id in stats.unlockedAchievements
                    val claimed = a.id in data.profile.claimedAchievements
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(if (unlocked) c.tangerineSoft else Color(0xFFF1ECE4)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = if (unlocked) c.tangerine else c.muted.copy(alpha = 0.5f))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.title, style = MaterialTheme.typography.titleSmall, color = c.ink)
                            Text(a.detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        when {
                            claimed -> Icon(Icons.Rounded.Check, "Claimed", tint = c.mint)
                            unlocked -> Button(onClick = { vm.claimAchievement(a.id) }, colors = ButtonDefaults.buttonColors(containerColor = c.tangerine), shape = RoundedCornerShape(14.dp)) { Text("+${a.coins}") }
                            else -> Text("+${a.coins}", style = MaterialTheme.typography.labelMedium, color = c.muted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ItemTile(item: Cosmetic, data: AppData, level: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = TT.c
    val owned = item.id in data.profile.owned
    val look = data.profile.look
    val equipped = when (item.category) {
        ItemCategory.HAIR -> look.hair == item.id
        ItemCategory.OUTFIT -> look.outfit == item.id
        ItemCategory.SHOES -> look.shoes == item.id
        ItemCategory.ACCESSORY -> look.accessory == item.id
        ItemCategory.PET -> look.pet == item.id
        ItemCategory.AURA -> look.aura == item.id
        ItemCategory.DECOR -> item.id in data.profile.equippedDecor
    }
    val locked = !owned && level < item.unlockLevel
    val swatch = if (item.colorHex != 0L) Color(item.colorHex) else Color(Catalog.hairColors.getOrElse(look.hairColor) { 0xFF6B3E1F })
    val state = when {
        equipped -> "Equipped"
        owned -> "Owned"
        item.pro && !data.isPro -> "Pro"
        locked -> "Lv ${item.unlockLevel}"
        else -> "${item.price} coins"
    }
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .then(if (equipped) Modifier.border(2.5.dp, c.tangerine, RoundedCornerShape(22.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${item.name}, $state" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(16.dp)).background(swatch.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(38.dp).clip(if (item.category == ItemCategory.AURA) CircleShape else RoundedCornerShape(12.dp)).background(swatch.copy(alpha = if (locked) 0.35f else 1f)))
            if (locked || (item.pro && !data.isPro && !owned)) {
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Lock, null, tint = c.muted, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.name, style = MaterialTheme.typography.labelMedium, color = c.ink, maxLines = 1, textAlign = TextAlign.Center)
        Text(
            state,
            style = MaterialTheme.typography.labelSmall,
            color = when { equipped -> c.tangerine; owned -> c.mint; item.pro && !data.isPro -> c.bubblegum; else -> c.muted },
        )
    }
}

@Composable
private fun LookEditor(data: AppData, onChange: (com.rork.tasktitan.data.AvatarLook) -> Unit) {
    val c = TT.c
    val look = data.profile.look
    Column(Modifier.padding(horizontal = 12.dp)) {
        SoftCard(Modifier.fillMaxWidth()) {
            Text("Character", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val styles = listOf("boy" to "Boy", "girl" to "Girl")
                styles.forEachIndexed { i, (key, label) ->
                    SegmentedButton(
                        selected = look.style == key, onClick = { onChange(look.copy(style = key)) },
                        shape = SegmentedButtonDefaults.itemShape(i, styles.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = c.tangerineSoft, activeContentColor = c.tangerine),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Body type", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Catalog.bodyLabels.forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = look.body == i, onClick = { onChange(look.copy(body = i)) },
                        shape = SegmentedButtonDefaults.itemShape(i, Catalog.bodyLabels.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = c.tangerineSoft, activeContentColor = c.tangerine),
                    ) { Text(l) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Skin tone", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Swatches(Catalog.skinTones, look.skin, "Skin tone") { onChange(look.copy(skin = it)) }
            Spacer(Modifier.height(14.dp))
            Text("Hair color", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Swatches(Catalog.hairColors, look.hairColor, "Hair color") { onChange(look.copy(hairColor = it)) }
        }
    }
}

@Composable
private fun Swatches(colors: List<Long>, selected: Int, label: String, onPick: (Int) -> Unit) {
    val c = TT.c
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEachIndexed { i, col ->
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (i == selected) c.tangerine else Color.Transparent)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(col))
                    .clickable(role = Role.RadioButton) { onPick(i) }
                    .semantics { contentDescription = "$label ${i + 1}" + if (i == selected) ", selected" else "" },
            )
        }
    }
}
