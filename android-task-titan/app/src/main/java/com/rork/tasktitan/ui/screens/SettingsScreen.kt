package com.rork.tasktitan.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.tasktitan.data.AppSettings
import com.rork.tasktitan.ui.AppViewModel
import com.rork.tasktitan.data.DateUtil
import com.rork.tasktitan.ui.components.IconTile
import com.rork.tasktitan.ui.components.SoftCard
import com.rork.tasktitan.ui.theme.TT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onPro: () -> Unit, onEditHouse: () -> Unit, onDeleted: () -> Unit) {
    val c = TT.c
    val data by vm.data.collectAsStateWithLifecycle()
    val s = data.settings
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val kid = s.ageGroup == "child"

    fun set(block: (AppSettings) -> AppSettings) = vm.updateSettings(block)

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = c.canvas),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 12.dp),
        ) {
            Group("Home") {
                NavRow(Icons.Rounded.Home, "Edit house & floors", "Up to 3 floors, as many rooms as you like", onEditHouse)
                HorizontalDivider(color = c.outline)
                NavRow(Icons.Rounded.Edit, "Household name", data.householdName) { renaming = true }
                HorizontalDivider(color = c.outline)
                ToggleRow(Icons.Rounded.Notifications, "Gentle reminders", "A friendly nudge at each chore's scheduled time", s.reminders) { v -> vm.setReminders(v) }
                if (s.reminders) {
                    HorizontalDivider(color = c.outline)
                    val paused = s.reminderPausedUntil > 0L && DateUtil.today() <= s.reminderPausedUntil
                    if (paused) {
                        NavRow(Icons.Rounded.PlayArrow, "Resume reminders", "Paused until ${DateUtil.label(s.reminderPausedUntil)} — resumes automatically anyway") { vm.resumeReminders() }
                    } else {
                        NavRow(Icons.Rounded.Pause, "Pause for 1 week", "No nudges until ${DateUtil.label(DateUtil.today() + 7)}, then they're back") { vm.pauseRemindersForWeek() }
                    }
                }
            }
            Group("Accessibility") {
                ToggleRow(Icons.Rounded.Contrast, "High contrast", "Stronger text and colors", s.highContrast) { v -> set { it.copy(highContrast = v) } }
                HorizontalDivider(color = c.outline)
                ToggleRow(Icons.Rounded.MotionPhotosOff, "Reduce motion", "Pauses 3D animations and confetti", s.reducedMotion) { v -> set { it.copy(reducedMotion = v) } }
                HorizontalDivider(color = c.outline)
                ToggleRow(Icons.Rounded.TextFields, "Larger text", "Bumps text size across the app", s.largeText) { v -> set { it.copy(largeText = v) } }
            }
            Group("What friends can see") {
                if (kid) Text("Sharing is locked for under-13 accounts.", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                ToggleRow(Icons.Rounded.Person, "My avatar", null, s.shareAvatar, enabled = !kid) { v -> set { it.copy(shareAvatar = v) } }
                HorizontalDivider(color = c.outline)
                ToggleRow(Icons.Rounded.Home, "My 3D house", null, s.shareHouse, enabled = !kid) { v -> set { it.copy(shareHouse = v) } }
                HorizontalDivider(color = c.outline)
                ToggleRow(Icons.Rounded.Insights, "My progress & level", null, s.shareProgress, enabled = !kid) { v -> set { it.copy(shareProgress = v) } }
                HorizontalDivider(color = c.outline)
                ToggleRow(Icons.Rounded.Groups, "My chore activity", null, s.shareActivity, enabled = !kid) { v -> set { it.copy(shareActivity = v) } }
            }
            Group("Privacy & data") {
                InfoRow(Icons.Rounded.Shield, "No address, camera or location", "Task Titan never asks for these permissions. Data is AES-encrypted on this device.")
                HorizontalDivider(color = c.outline)
                NavRow(Icons.Rounded.Download, "Export my data", "Share a copy as JSON") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, "Task Titan data export")
                        putExtra(Intent.EXTRA_TEXT, vm.exportJson())
                    }
                    context.startActivity(Intent.createChooser(send, "Export data"))
                }
                HorizontalDivider(color = c.outline)
                NavRow(Icons.Rounded.DeleteForever, "Delete all my data", "Removes everything from this device", tint = c.bubblegum) { confirmDelete = true }
            }
            Group("Plan") {
                NavRow(Icons.Rounded.WorkspacePremium, if (data.isPro) "Task Titan Pro" else "Upgrade to Pro", "Core chores are always free", onPro)
            }
            Text(
                "Task Titan · v1.0 · Consent recorded for ${when (s.ageGroup) { "child" -> "under 13 (parent approved)"; "teen" -> "13–17"; else -> "18+" }}",
                style = MaterialTheme.typography.labelSmall, color = c.muted,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete everything?") },
            text = { Text("Your house, chores, avatar and progress will be permanently erased from this device. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteEverything(); onDeleted() }) { Text("Delete", color = c.bubblegum) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep my data") } },
        )
    }
    if (renaming) {
        var name by remember { mutableStateOf(data.householdName) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Household name") },
            text = { OutlinedTextField(name, { name = it.take(24) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.renameHousehold(name); renaming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val c = TT.c
    Text(title, style = MaterialTheme.typography.titleMedium, color = c.muted, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp))
    SoftCard(Modifier.fillMaxWidth(), padding = 4.dp) { content() }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = TT.c
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, c.tangerine, c.tangerineSoft, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Switch(checked, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = c.tangerine))
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) = NavRow(icon, title, subtitle, null, onClick)

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, tint: Color?, onClick: () -> Unit) {
    val c = TT.c
    val t = tint ?: c.tangerine
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, t, if (tint != null) c.bubblegumSoft else c.tangerineSoft, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (tint != null) tint else c.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.muted)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, subtitle: String) {
    val c = TT.c
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, c.sky, c.skySoft, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
    Spacer(Modifier.height(2.dp))
}
