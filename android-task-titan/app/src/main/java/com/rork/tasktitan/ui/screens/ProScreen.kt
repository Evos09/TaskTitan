package com.rork.tasktitan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rork.tasktitan.ui.components.SoftCard
import com.rork.tasktitan.ui.theme.TT
import kotlinx.coroutines.launch

/** Pro plan overview. Core chore features are never behind the paywall. */
@Composable
fun ProScreen(onBack: () -> Unit) {
    val c = TT.c
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var plan by remember { mutableIntStateOf(1) }
    val plans = listOf(Triple("Monthly", "$5.99 / month", ""), Triple("Yearly", "$47.99 / year", "Just $3.99 a month"))

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.tangerineSoft, c.canvas)))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "Close", tint = c.ink) }
            }
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(c.tangerine), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(50.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Task Titan Pro", style = MaterialTheme.typography.displaySmall, color = c.ink)
            Text("More home, more style. Chores themselves are always free.", style = MaterialTheme.typography.bodyLarge, color = c.muted)
            Spacer(Modifier.height(18.dp))
            SoftCard(Modifier.fillMaxWidth()) {
                listOf(
                    "Premium outfits, pets, auras & decor",
                    "Multiple households (family + roommates)",
                    "Advanced private stats & trends",
                    "Early access to seasonal events",
                ).forEach { Feature(it, true) }
            }
            Spacer(Modifier.height(12.dp))
            SoftCard(Modifier.fillMaxWidth(), color = c.mintSoft) {
                Text("Free forever", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Spacer(Modifier.height(6.dp))
                listOf("Unlimited chores, reminders & checklists", "Your 3D avatar and 3D house", "Shared household goal & recaps").forEach { Feature(it, true) }
            }
            Spacer(Modifier.height(16.dp))
            plans.forEachIndexed { i, (name, price, note) ->
                val on = plan == i
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.surface)
                        .border(if (on) 2.5.dp else 0.dp, if (on) c.tangerine else Color.Transparent, RoundedCornerShape(22.dp))
                        .clickable(role = Role.RadioButton) { plan = i }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, color = c.ink)
                        if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.labelMedium, color = c.mint)
                    }
                    Text(price, style = MaterialTheme.typography.titleSmall, color = c.ink)
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { scope.launch { snack.showSnackbar("Subscriptions open once Google Play Billing is connected.") } },
                colors = ButtonDefaults.buttonColors(containerColor = c.tangerine),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(58.dp),
            ) { Text("Start Pro", style = MaterialTheme.typography.labelLarge) }
            Text("Cancel anytime in Google Play.", style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp).align(Alignment.CenterHorizontally))
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun Feature(text: String, included: Boolean) {
    val c = TT.c
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (included) c.tangerine else c.outline), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.ink)
    }
}
