package com.rork.tasktitan.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.rork.tasktitan.data.AvatarLook
import com.rork.tasktitan.data.EnergyTier
import com.rork.tasktitan.engine.AvatarView
import com.rork.tasktitan.engine.Pose
import com.rork.tasktitan.ui.AppViewModel
import com.rork.tasktitan.ui.components.IconTile
import com.rork.tasktitan.ui.components.SoftCard
import com.rork.tasktitan.ui.theme.TT

/** First launch: name, age group and COPPA/GDPR-style consent. */
@Composable
fun ConsentScreen(vm: AppViewModel, onContinue: () -> Unit) {
    val c = TT.c
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("adult") }
    var agreed by remember { mutableStateOf(false) }
    var parent by remember { mutableStateOf(false) }
    var style by remember { mutableStateOf("boy") }
    val needsParent = age == "child"
    val canGo = agreed && (!needsParent || parent)

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.tangerineSoft, c.canvas, c.canvas)))
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            AvatarView(AvatarLook(style = style), EnergyTier.THRIVING, Modifier.fillMaxSize(), pose = Pose.CELEBRATE, spin = true)
        }
        // The pedestal renders below the avatar box; keep it clear of the title.
        Spacer(Modifier.height(48.dp))
        Text("Task Titan", style = MaterialTheme.typography.displaySmall, color = c.ink)
        Text("Real chores, a happier 3D home. Your avatar only ever levels up.", style = MaterialTheme.typography.bodyLarge, color = c.muted)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = { Text("Your name or nickname") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, unfocusedBorderColor = c.outline),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text("Your character", style = MaterialTheme.typography.titleMedium, color = c.ink)
        Spacer(Modifier.height(8.dp))
        val styles = listOf("boy" to "Boy", "girl" to "Girl")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            styles.forEachIndexed { i, (key, label) ->
                SegmentedButton(
                    selected = style == key,
                    onClick = { style = key },
                    shape = SegmentedButtonDefaults.itemShape(i, styles.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = c.tangerineSoft, activeContentColor = c.tangerine),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Who's using this device?", style = MaterialTheme.typography.titleMedium, color = c.ink)
        Spacer(Modifier.height(8.dp))
        val options = listOf("adult" to "18+", "teen" to "13–17", "child" to "Under 13")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (key, label) ->
                SegmentedButton(
                    selected = age == key,
                    onClick = { age = key },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = c.tangerineSoft, activeContentColor = c.tangerine),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(16.dp))

        SoftCard(Modifier.fillMaxWidth()) {
            Text("Our privacy promise", style = MaterialTheme.typography.titleLarge, color = c.ink)
            Spacer(Modifier.height(10.dp))
            Promise(Icons.Rounded.LocationOff, "No address or location — ever")
            Promise(Icons.Rounded.NoPhotography, "No camera and no photo uploads")
            Promise(Icons.Rounded.Lock, "Your data is encrypted on this device")
            Promise(Icons.Rounded.VisibilityOff, "Sharing with friends is off until you turn it on")
        }
        Spacer(Modifier.height(12.dp))

        ConsentRow(agreed, "I agree to the Terms and Privacy Policy, and understand I can export or delete my data anytime in Settings.") { agreed = it }
        if (needsParent) {
            ConsentRow(parent, "I'm a parent or guardian and I approve this account. Social features stay off for kids.") { parent = it }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                vm.giveConsent(name, age, parent, style)
                if (needsParent) vm.updateSettings { it.copy(shareActivity = false, shareProgress = false, interHouseholdChallenge = false) }
                onContinue()
            },
            enabled = canGo,
            colors = ButtonDefaults.buttonColors(containerColor = c.tangerine),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) { Text("Build my home", style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Promise(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val c = TT.c
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, c.sky, c.skySoft, 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.ink)
    }
}

@Composable
private fun ConsentRow(checked: Boolean, text: String, onChange: (Boolean) -> Unit) {
    val c = TT.c
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.tangerine), modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.ink)
    }
}
