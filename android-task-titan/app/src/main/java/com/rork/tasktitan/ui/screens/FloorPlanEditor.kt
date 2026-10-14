package com.rork.tasktitan.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.rork.tasktitan.data.GameRules
import com.rork.tasktitan.data.Room
import com.rork.tasktitan.ui.components.roomAccent
import com.rork.tasktitan.ui.components.roomTint
import com.rork.tasktitan.ui.theme.Body
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode { NONE, MOVE, TL, TR, BL, BR }

/**
 * Drag-and-drop floor plan grid. Drag a room to move it (snaps to grid),
 * drag a corner handle of the selected room to resize. Overlaps are rejected.
 */
@Composable
fun FloorPlanEditor(
    rooms: List<Room>,
    floor: Int,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onChange: (Room) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val latestRooms by rememberUpdatedState(rooms)
    val latestSelected by rememberUpdatedState(selectedId)
    var preview by remember { mutableStateOf<Room?>(null) }
    var previewValid by remember { mutableStateOf(true) }

    fun cellSize(width: Float): Float = width / GameRules.GRID_W

    fun hit(pos: Offset, cell: Float): Room? = latestRooms.lastOrNull {
        it.floor == floor && pos.x >= it.x * cell && pos.x <= (it.x + it.w) * cell && pos.y >= it.y * cell && pos.y <= (it.y + it.h) * cell
    }

    Canvas(
        modifier
            .aspectRatio(GameRules.GRID_W.toFloat() / GameRules.GRID_H)
            .semantics { contentDescription = "Floor plan editor. Drag rooms to arrange, drag corners to resize." }
            .pointerInput(floor) {
                detectTapGestures { pos ->
                    onSelect(hit(pos, cellSize(size.width.toFloat()))?.id)
                }
            }
            .pointerInput(floor) {
                var mode = DragMode.NONE
                var start: Room? = null
                var acc = Offset.Zero
                detectDragGestures(
                    onDragStart = { pos ->
                        val cell = cellSize(size.width.toFloat())
                        val sel = latestRooms.firstOrNull { it.id == latestSelected && it.floor == floor }
                        mode = DragMode.NONE
                        if (sel != null) {
                            val r = cell * 0.7f
                            val corners = listOf(
                                DragMode.TL to Offset(sel.x * cell, sel.y * cell),
                                DragMode.TR to Offset((sel.x + sel.w) * cell, sel.y * cell),
                                DragMode.BL to Offset(sel.x * cell, (sel.y + sel.h) * cell),
                                DragMode.BR to Offset((sel.x + sel.w) * cell, (sel.y + sel.h) * cell),
                            )
                            corners.firstOrNull { (_, o) -> abs(o.x - pos.x) < r && abs(o.y - pos.y) < r }?.let { mode = it.first; start = sel }
                        }
                        if (mode == DragMode.NONE) {
                            val h = hit(pos, cell)
                            if (h != null) { mode = DragMode.MOVE; start = h; onSelect(h.id) }
                        }
                        acc = Offset.Zero
                    },
                    onDrag = { change, amount ->
                        val s = start ?: return@detectDragGestures
                        if (mode == DragMode.NONE) return@detectDragGestures
                        change.consume()
                        acc += amount
                        val cell = cellSize(size.width.toFloat())
                        val dx = (acc.x / cell).roundToInt()
                        val dy = (acc.y / cell).roundToInt()
                        val gw = GameRules.GRID_W; val gh = GameRules.GRID_H
                        var x0 = s.x; var y0 = s.y; var x1 = s.x + s.w; var y1 = s.y + s.h
                        when (mode) {
                            DragMode.MOVE -> {
                                val nx = (s.x + dx).coerceIn(0, gw - s.w); val ny = (s.y + dy).coerceIn(0, gh - s.h)
                                x0 = nx; y0 = ny; x1 = nx + s.w; y1 = ny + s.h
                            }
                            DragMode.TL -> { x0 = (s.x + dx).coerceIn(0, x1 - 2); y0 = (s.y + dy).coerceIn(0, y1 - 2) }
                            DragMode.TR -> { x1 = (s.x + s.w + dx).coerceIn(x0 + 2, gw); y0 = (s.y + dy).coerceIn(0, y1 - 2) }
                            DragMode.BL -> { x0 = (s.x + dx).coerceIn(0, x1 - 2); y1 = (s.y + s.h + dy).coerceIn(y0 + 2, gh) }
                            DragMode.BR -> { x1 = (s.x + s.w + dx).coerceIn(x0 + 2, gw); y1 = (s.y + s.h + dy).coerceIn(y0 + 2, gh) }
                            DragMode.NONE -> Unit
                        }
                        val candidate = s.copy(x = x0, y = y0, w = x1 - x0, h = y1 - y0)
                        preview = candidate
                        previewValid = !GameRules.overlaps(latestRooms, candidate)
                    },
                    onDragEnd = {
                        val p = preview
                        if (p != null && previewValid) onChange(p)
                        preview = null; start = null; mode = DragMode.NONE
                    },
                    onDragCancel = { preview = null; start = null; mode = DragMode.NONE },
                )
            }
    ) {
        val cell = size.width / GameRules.GRID_W
        val gridColor = Color(0x1414254A)
        for (gx in 0..GameRules.GRID_W) drawLine(gridColor, Offset(gx * cell, 0f), Offset(gx * cell, size.height), 1.5f)
        for (gy in 0..GameRules.GRID_H) drawLine(gridColor, Offset(0f, gy * cell), Offset(size.width, gy * cell), 1.5f)

        val floorRooms = rooms.filter { it.floor == floor }.map { if (preview?.id == it.id) preview!! else it }
        val gap = cell * 0.06f
        for (r in floorRooms) {
            val selected = r.id == selectedId
            val tl = Offset(r.x * cell + gap, r.y * cell + gap)
            val sz = Size(r.w * cell - gap * 2, r.h * cell - gap * 2)
            val invalid = preview?.id == r.id && !previewValid
            val fill = if (invalid) Color(0xFFFFD4D4) else roomTint(r.type)
            drawRoundRect(fill, tl, sz, CornerRadius(cell * 0.35f))
            val accent = roomAccent(r.type)
            drawRoundRect(
                if (selected) Color(0xFFFF8A1F) else accent.copy(alpha = 0.25f),
                tl, sz, CornerRadius(cell * 0.35f),
                style = Stroke(width = if (selected) 5f else 2.5f),
            )
            val style = TextStyle(fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = accent, textAlign = TextAlign.Center)
            val maxW = (sz.width - 8f).toInt().coerceAtLeast(1)
            val layout = measurer.measure(r.name, style, overflow = TextOverflow.Ellipsis, maxLines = 2, constraints = Constraints(maxWidth = maxW))
            drawText(layout, topLeft = Offset(tl.x + (sz.width - layout.size.width) / 2, tl.y + (sz.height - layout.size.height) / 2))
            if (selected) {
                for (o in listOf(tl, Offset(tl.x + sz.width, tl.y), Offset(tl.x, tl.y + sz.height), Offset(tl.x + sz.width, tl.y + sz.height))) {
                    drawCircle(Color.White, cell * 0.24f, o)
                    drawCircle(Color(0xFFFF8A1F), cell * 0.24f, o, style = Stroke(4f))
                }
            }
        }
    }
}
