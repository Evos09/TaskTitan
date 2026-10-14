package com.rork.tasktitan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.tasktitan.data.GameRules
import com.rork.tasktitan.data.HomeSize
import com.rork.tasktitan.data.Room
import com.rork.tasktitan.data.RoomType
import com.rork.tasktitan.engine.HouseScene
import com.rork.tasktitan.engine.House3DView
import com.rork.tasktitan.ui.AppViewModel
import com.rork.tasktitan.ui.components.BrandMark
import com.rork.tasktitan.ui.components.SoftCard
import com.rork.tasktitan.ui.components.roomAccent
import com.rork.tasktitan.ui.components.roomIcon
import com.rork.tasktitan.ui.components.roomTint
import com.rork.tasktitan.ui.theme.TT
import kotlinx.coroutines.launch

private fun floorName(i: Int) = if (i == 0) "Ground" else "Floor ${i + 1}"

/** Full-screen house designer: up to 3 floors, unlimited rooms, live 3D preview. */
@Composable
fun HouseBuilderScreen(vm: AppViewModel, onboarding: Boolean, onDone: () -> Unit, onClose: (() -> Unit)?) {
    val c = TT.c
    val data by vm.data.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val initialRooms = remember { if (data.rooms.isEmpty()) GameRules.layoutFor(data.homeSize) else data.rooms }
    var rooms by remember { mutableStateOf(initialRooms) }
    var size by remember { mutableStateOf(data.homeSize) }
    var floors by remember { mutableIntStateOf(maxOf(data.floors, (initialRooms.maxOfOrNull { it.floor } ?: 0) + 1)) }
    var floor by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var pendingSize by remember { mutableStateOf<HomeSize?>(null) }
    var confirmDelete by remember { mutableStateOf<Room?>(null) }

    fun toast(msg: String) { scope.launch { snack.showSnackbar(msg) } }

    fun addRoom(type: RoomType) {
        val spot = GameRules.nextFreeSpot(rooms, floor, 3, 3) ?: GameRules.nextFreeSpot(rooms, floor, 2, 2)
        if (spot == null) {
            toast(if (floors < GameRules.MAX_FLOORS) "${floorName(floor)} is full — add another floor or resize rooms" else "This floor is full — try resizing rooms")
            return
        }
        val w = if (GameRules.nextFreeSpot(rooms, floor, 3, 3) != null) 3 else 2
        val sameType = rooms.count { it.type == type }
        val name = if (sameType == 0) type.label else "${type.label} ${sameType + 1}"
        val room = Room(GameRules.newId(), name, type, floor, spot.first, spot.second, w, w)
        rooms = rooms + room
        selected = room.id
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun applySize(s: HomeSize) {
        size = s
        rooms = GameRules.layoutFor(s)
        floors = (rooms.maxOfOrNull { it.floor } ?: 0) + 1
        floor = 0
        selected = null
    }

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Build Your Home", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                    Text("Design your space, your way", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close", tint = c.ink) }
                }
            }
            Spacer(Modifier.height(14.dp))

            Text("Quick start", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                HomeSize.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = size == s,
                        onClick = { if (onboarding && rooms == initialRooms) applySize(s) else pendingSize = s },
                        shape = SegmentedButtonDefaults.itemShape(i, HomeSize.entries.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = c.tangerineSoft, activeContentColor = c.tangerine),
                        icon = {},
                    ) { Text(s.label, fontSize = 13.sp, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.surface).padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until floors) {
                    val on = i == floor
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (on) c.tangerine else Color.Transparent)
                            .clickable(role = Role.Tab) { floor = i; selected = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(floorName(i), style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else c.ink)
                    }
                }
                if (floors < GameRules.MAX_FLOORS) {
                    TextButton(onClick = { floors++; floor = floors - 1; selected = null; haptics.performHapticFeedback(HapticFeedbackType.LongPress) }) {
                        Icon(Icons.Rounded.AddCircleOutline, null, tint = c.sky)
                        Spacer(Modifier.width(4.dp))
                        Text("Add floor", color = c.sky, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$floors of ${GameRules.MAX_FLOORS} floors · ${rooms.size} rooms",
                    style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.weight(1f),
                )
                if (floor > 0 && floor == floors - 1) {
                    TextButton(onClick = {
                        rooms = rooms.filter { it.floor != floor }
                        floors--; floor = floors - 1; selected = null
                    }) { Text("Remove this floor", color = c.bubblegum, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Spacer(Modifier.height(6.dp))

            SoftCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                Text("Drag to arrange, pull corners to resize", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Spacer(Modifier.height(8.dp))
                FloorPlanEditor(
                    rooms = rooms,
                    floor = floor,
                    selectedId = selected,
                    onSelect = { selected = it },
                    onChange = { r -> rooms = rooms.map { if (it.id == r.id) r else it }; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (rooms.none { it.floor == floor }) {
                    Text("This floor is empty — tap a room template below to add one.", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp))
                }
                val sel = rooms.firstOrNull { it.id == selected }
                if (sel != null) {
                    Spacer(Modifier.height(10.dp))
                    SelectedRoomPanel(
                        room = sel,
                        onRename = { n -> rooms = rooms.map { if (it.id == sel.id) it.copy(name = n) else it } },
                        onType = { t -> rooms = rooms.map { if (it.id == sel.id) it.copy(type = t, name = if (it.name == it.type.label) t.label else it.name) else it } },
                        onDelete = { if (data.chores.any { it.roomId == sel.id }) confirmDelete = sel else { rooms = rooms - sel; selected = null } },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            SoftCard(Modifier.fillMaxWidth(), color = c.surface, padding = 0.dp) {
                Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFEFF7FF))) {
                    House3DView(
                        HouseScene(rooms = rooms, topFloor = floor.coerceAtMost(floors - 1), look = data.profile.look),
                        Modifier.fillMaxSize(),
                        autoSpin = true,
                        verticalDragScrolls = true,
                        description = "3D preview of ${floorName(floor)}",
                    )
                    Column(Modifier.padding(14.dp)) {
                        Text("3D preview", style = MaterialTheme.typography.titleLarge, color = c.ink)
                        Text("$floors floors · ${rooms.size} rooms", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    }
                    Column(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in floors - 1 downTo 0) {
                            val on = i == floor
                            Box(
                                Modifier.clip(CircleShape).background(if (on) c.tangerine else Color.White.copy(alpha = 0.9f))
                                    .clickable(role = Role.Button) { floor = i; selected = null }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { Text(floorName(i), style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else c.ink) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Room templates", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.weight(1f))
                Text("Tap to add to ${floorName(floor)}", style = MaterialTheme.typography.labelMedium, color = c.muted)
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                items(RoomType.entries.filter { it != RoomType.CUSTOM } + RoomType.CUSTOM) { t ->
                    Column(
                        Modifier
                            .width(86.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(roomTint(t))
                            .clickable(role = Role.Button, onClickLabel = "Add ${t.label}") { addRoom(t) }
                            .padding(vertical = 14.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(roomIcon(t), null, tint = roomAccent(t), modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(t.label, style = MaterialTheme.typography.labelMedium, color = roomAccent(t), maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { addRoom(RoomType.CUSTOM) },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).height(58.dp).border(2.dp, c.sky, RoundedCornerShape(24.dp)),
                    border = null,
                ) {
                    Icon(Icons.Rounded.AddCircleOutline, null, tint = c.sky)
                    Spacer(Modifier.width(6.dp))
                    Text("Add room", color = c.sky, style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = {
                        if (rooms.isEmpty()) { toast("Add at least one room first"); return@Button }
                        val usedFloors = (rooms.maxOfOrNull { it.floor } ?: 0) + 1
                        vm.saveHouse(rooms, maxOf(usedFloors, 1).coerceAtMost(floors), size)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDone()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.tangerine),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1.2f).height(58.dp),
                ) {
                    Text(if (onboarding) "Enter my home" else "Save house", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                }
            }
            Spacer(Modifier.height(14.dp))
            SoftCard(Modifier.fillMaxWidth(), color = c.skySoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, null, tint = c.sky, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("No address, camera or location needed", style = MaterialTheme.typography.titleSmall, color = c.ink)
                        Text("Your home is a playful sketch that only you control.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    pendingSize?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingSize = null },
            title = { Text("Start from ${s.label}?") },
            text = { Text("This replaces your current floor plan with a fresh ${s.label} layout. Chores in removed rooms will be cleared when you save.") },
            confirmButton = { TextButton(onClick = { applySize(s); pendingSize = null }) { Text("Use ${s.label}") } },
            dismissButton = { TextButton(onClick = { pendingSize = null }) { Text("Keep mine") } },
        )
    }
    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove ${r.name}?") },
            text = { Text("Chores in this room will be removed when you save your house.") },
            confirmButton = { TextButton(onClick = { rooms = rooms - r; selected = null; confirmDelete = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SelectedRoomPanel(room: Room, onRename: (String) -> Unit, onType: (RoomType) -> Unit, onDelete: () -> Unit) {
    val c = TT.c
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = room.name,
            onValueChange = { onRename(it.take(22)) },
            singleLine = true,
            label = { Text("Room name") },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.outline),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Box {
            IconButton(onClick = { menu = true }) { Icon(roomIcon(room.type), "Change room type", tint = roomAccent(room.type)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                RoomType.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.label) },
                        leadingIcon = { Icon(roomIcon(t), null, tint = roomAccent(t)) },
                        onClick = { onType(t); menu = false },
                    )
                }
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Remove room", tint = c.bubblegum) }
    }
}
