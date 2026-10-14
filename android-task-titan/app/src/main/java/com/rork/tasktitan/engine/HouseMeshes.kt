package com.rork.tasktitan.engine

import androidx.compose.ui.graphics.Color
import com.rork.tasktitan.data.Catalog
import com.rork.tasktitan.data.ChoreKind
import com.rork.tasktitan.data.Room
import com.rork.tasktitan.data.RoomType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

const val FLOOR_HEIGHT: Float = 1.45f
const val WALL_TALL: Float = 1.15f
const val WALL_LOW: Float = 0.16f
const val SLAB: Float = 0.1f

/** A pending chore's mess, placed in its room. */
data class PropSpec(val choreId: String, val roomId: String, val kind: ChoreKind)

/** World-space layout helpers shared by the house, props and avatar. */
class HouseLayout(val rooms: List<Room>) {
    val minX: Int = rooms.minOfOrNull { it.x } ?: 0
    val maxX: Int = rooms.maxOfOrNull { it.x + it.w } ?: 12
    val minZ: Int = rooms.minOfOrNull { it.y } ?: 0
    val maxZ: Int = rooms.maxOfOrNull { it.y + it.h } ?: 9
    val cx: Float = (minX + maxX) / 2f
    val cz: Float = (minZ + maxZ) / 2f
    val radius: Float = maxOf(maxX - minX, maxZ - minZ, 4) / 2f

    fun wx(gx: Float): Float = gx - cx
    fun wz(gz: Float): Float = gz - cz
    fun base(floor: Int): Float = floor * FLOOR_HEIGHT

    fun center(room: Room): Triple<Float, Float, Float> =
        Triple(wx(room.x + room.w / 2f), base(room.floor) + SLAB, wz(room.y + room.h / 2f))

    /** Where the avatar rests in a room: in front of the sofa or near the middle. */
    fun restSpot(room: Room): Pair<Float, Float> = when (room.type) {
        RoomType.LIVING -> wx(room.x + room.w / 2f) to wz(room.y + minOf(1.45f, room.h - 0.6f))
        else -> wx(room.x + room.w * 0.55f) to wz(room.y + room.h * 0.62f)
    }
}

private fun c(hex: Long): Color = Color(hex)

private val WALL = c(0xFFFFF1DE)
private val WALL_TOP = c(0xFFF3DDBE)
private val WOOD = c(0xFFE7AE70)
private val WOOD_DARK = c(0xFFC98A4E)
private val WHITE = c(0xFFFAF7F2)
private val INK = c(0xFF2B3046)
private val LEAF = c(0xFF4CBF5E)
private val POT = c(0xFFE58A5A)

private fun hash(s: String, salt: Int): Float {
    var h = 17 + salt * 31
    for (ch in s) h = h * 131 + ch.code
    return ((h ushr 1) % 1000) / 1000f
}

private fun touches(a: Room, b: Room, side: Int): Boolean {
    if (a.id == b.id || a.floor != b.floor) return false
    val overlapZ = a.y < b.y + b.h && a.y + a.h > b.y
    val overlapX = a.x < b.x + b.w && a.x + a.w > b.x
    return when (side) {
        0 -> b.y + b.h == a.y && overlapX
        1 -> b.y == a.y + a.h && overlapX
        2 -> b.x + b.w == a.x && overlapZ
        else -> b.x == a.x + a.w && overlapZ
    }
}

/**
 * Builds the static house: ground, stacked lower floors, the active floor's rooms,
 * cutaway walls (tall only on exterior walls facing away from camera) and furniture.
 */
fun buildHouse(
    mb: MeshBuilder,
    layout: HouseLayout,
    topFloor: Int,
    camYaw: Float,
    night: Boolean,
    pending: List<PropSpec>,
    decor: List<String>,
    furnish: Boolean = true,
) {
    val rooms = layout.rooms
    val pad = 1.2f
    mb.layer = -20
    mb.box(layout.wx(layout.minX - pad), -0.3f, layout.wz(layout.minZ - pad), layout.wx(layout.maxX + pad), 0f, layout.wz(layout.maxZ + pad), c(0xFF7FCB6A), c(0xFF9BDB7E))
    mb.layer = -19
    val trees = listOf(layout.minX - 0.6f to layout.maxZ + 0.5f, layout.maxX + 0.6f to layout.minZ - 0.4f, layout.maxX + 0.6f to layout.maxZ + 0.5f)
    for ((tx, tz) in trees) {
        val x = layout.wx(tx); val z = layout.wz(tz)
        mb.block(x, 0f, z, 0.14f, 0.3f, 0.14f, WOOD_DARK)
        mb.block(x, 0.3f, z, 0.55f, 0.4f, 0.55f, LEAF, c(0xFF63D474))
        mb.block(x, 0.7f, z, 0.32f, 0.28f, 0.32f, LEAF, c(0xFF7BE08A))
    }

    val camX = sin(camYaw); val camZ = cos(camYaw)

    for (f in 0 until topFloor) {
        mb.layer = f * 10 + 1
        val b = layout.base(f)
        for (r in rooms.filter { it.floor == f }) {
            val x0 = layout.wx(r.x.toFloat()); val x1 = layout.wx((r.x + r.w).toFloat())
            val z0 = layout.wz(r.y.toFloat()); val z1 = layout.wz((r.y + r.h).toFloat())
            mb.box(x0, b, z0, x1, b + FLOOR_HEIGHT, z1, WALL, WALL_TOP)
            val win = if (night) c(0xFFFFD86B) else c(0xFFBFE6FF)
            var wx = x0 + 0.5f
            while (wx + 0.5f <= x1 - 0.2f) {
                mb.decalZ(wx, b + 0.55f, wx + 0.45f, b + 1.05f, z1 + 0.001f, win)
                wx += 1.1f
            }
            var wz = z0 + 0.5f
            while (wz + 0.5f <= z1 - 0.2f) {
                mb.decalX(wz, b + 0.55f, wz + 0.45f, b + 1.05f, x1 + 0.001f, win)
                wz += 1.1f
            }
        }
    }

    val floorRooms = rooms.filter { it.floor == topFloor }
    val base = layout.base(topFloor)
    for (r in floorRooms) {
        val x0 = layout.wx(r.x.toFloat()); val x1 = layout.wx((r.x + r.w).toFloat())
        val z0 = layout.wz(r.y.toFloat()); val z1 = layout.wz((r.y + r.h).toFloat())
        mb.layer = topFloor * 10
        val floorColor = c(Catalog.roomColors[r.type] ?: 0xFFF1EDE6)
        mb.box(x0, base, z0, x1, base + SLAB, z1, WALL_TOP, floorColor)
        mb.layer = topFloor * 10 + 1
        val sides = listOf(
            Triple(0, 0f to -1f, floatArrayOf(x0, z0 - 0.04f, x1, z0 + 0.04f)),
            Triple(1, 0f to 1f, floatArrayOf(x0, z1 - 0.04f, x1, z1 + 0.04f)),
            Triple(2, -1f to 0f, floatArrayOf(x0 - 0.04f, z0, x0 + 0.04f, z1)),
            Triple(3, 1f to 0f, floatArrayOf(x1 - 0.04f, z0, x1 + 0.04f, z1)),
        )
        for ((side, n, rect) in sides) {
            val interior = floorRooms.any { touches(r, it, side) }
            val facingAway = n.first * camX + n.second * camZ < -0.05f
            val tall = !interior && facingAway
            val h = if (tall) WALL_TALL else WALL_LOW
            mb.box(rect[0], base + SLAB, rect[1], rect[2], base + SLAB + h, rect[3], WALL, WALL_TOP)
            if (tall) {
                val win = if (night) c(0xFFFFD86B) else c(0xFFBFE6FF)
                val frame = WHITE
                val len = if (side < 2) x1 - x0 else z1 - z0
                if (len >= 2f) {
                    val mid = if (side < 2) (x0 + x1) / 2 else (z0 + z1) / 2
                    val y0 = base + 0.5f; val y1 = base + 0.95f
                    when (side) {
                        0 -> { mb.decalZ(mid - 0.38f, y0 - 0.04f, mid + 0.38f, y1 + 0.04f, z0 + 0.041f, frame); mb.decalZ(mid - 0.32f, y0, mid + 0.32f, y1, z0 + 0.042f, win, -0.03f) }
                        2 -> { mb.decalX(mid - 0.38f, y0 - 0.04f, mid + 0.38f, y1 + 0.04f, x0 + 0.041f, frame); mb.decalX(mid - 0.32f, y0, mid + 0.32f, y1, x0 + 0.042f, win, -0.03f) }
                        1 -> { mb.decalNegZ(mid - 0.38f, y0 - 0.04f, mid + 0.38f, y1 + 0.04f, z1 - 0.041f, frame); mb.decalNegZ(mid - 0.32f, y0, mid + 0.32f, y1, z1 - 0.042f, win, -0.03f) }
                        else -> { mb.decalNegX(mid - 0.38f, y0 - 0.04f, mid + 0.38f, y1 + 0.04f, x1 - 0.041f, frame); mb.decalNegX(mid - 0.32f, y0, mid + 0.32f, y1, x1 - 0.042f, win, -0.03f) }
                    }
                }
            }
        }
        if (furnish) furnishRoom(mb, r, x0, z0, x1, z1, base + SLAB)
    }

    mb.layer = topFloor * 10 + 1
    if (furnish) {
        for (p in pending) {
            val room = floorRooms.firstOrNull { it.id == p.roomId } ?: continue
            buildMess(mb, layout, room, p, 1f)
        }
        val decorRoom = floorRooms.firstOrNull { it.type == RoomType.LIVING } ?: floorRooms.firstOrNull()
        if (decorRoom != null) buildDecor(mb, layout, decorRoom, decor, night)
    }
    mb.resetTransform()
}

private fun furnishRoom(mb: MeshBuilder, r: Room, x0: Float, z0: Float, x1: Float, z1: Float, y: Float) {
    val w = x1 - x0; val d = z1 - z0
    val cx = (x0 + x1) / 2; val cz = (z0 + z1) / 2
    val accent = c(Catalog.roomAccents[r.type] ?: 0xFF8A6D4C)
    mb.layer = r.floor * 10
    when (r.type) {
        RoomType.KITCHEN -> {
            mb.flat(x0 + 0.3f, z0 + 1.0f, x1 - 0.3f, z1 - 0.3f, y + 0.003f, c(0xFFFFF4E6))
            mb.layer = r.floor * 10 + 1
            val counterEnd = x1 - 0.95f
            mb.box(x0 + 0.1f, y, z0 + 0.1f, counterEnd, y + 0.55f, z0 + 0.72f, WHITE, c(0xFFEFC495))
            mb.box(x0 + 0.5f, y + 0.55f, z0 + 0.25f, x0 + 1.0f, y + 0.565f, z0 + 0.6f, c(0xFF9FB4C7), bias = -0.01f)
            mb.box(x0 + 0.72f, y + 0.55f, z0 + 0.15f, x0 + 0.78f, y + 0.75f, z0 + 0.22f, c(0xFFB9C3CC))
            mb.box(x1 - 0.85f, y, z0 + 0.1f, x1 - 0.12f, y + 1.25f, z0 + 0.8f, c(0xFFE9F2F8), c(0xFFF7FBFF))
            mb.decalZ(x1 - 0.8f, y + 0.7f, x1 - 0.74f, y + 1.0f, z0 + 0.801f, c(0xFFB0BCC8))
            if (d >= 3f) {
                mb.block(cx, y, cz + 0.6f, 0.16f, 0.42f, 0.16f, WOOD_DARK)
                mb.block(cx, y + 0.42f, cz + 0.6f, 1.1f, 0.07f, 0.75f, WOOD)
            }
        }
        RoomType.LIVING -> {
            mb.flat(cx - w * 0.32f, cz - d * 0.18f, cx + w * 0.32f, cz + d * 0.3f, y + 0.004f, c(0xFFBFE4FF))
            mb.layer = r.floor * 10 + 1
            val sofa = c(0xFFFFFFFF)
            val sw = minOf(2f, w - 0.8f) / 2
            mb.box(cx - sw, y, z0 + 0.2f, cx + sw, y + 0.28f, z0 + 0.85f, c(0xFFEDEBF2), sofa)
            mb.box(cx - sw, y + 0.28f, z0 + 0.2f, cx + sw, y + 0.62f, z0 + 0.42f, c(0xFFEDEBF2), sofa)
            mb.box(cx - sw - 0.16f, y, z0 + 0.2f, cx - sw, y + 0.42f, z0 + 0.85f, c(0xFFE2E0EA))
            mb.box(cx + sw, y, z0 + 0.2f, cx + sw + 0.16f, y + 0.42f, z0 + 0.85f, c(0xFFE2E0EA))
            mb.block(cx - sw * 0.55f, y + 0.28f, z0 + 0.5f, 0.32f, 0.26f, 0.12f, c(0xFFFF8FC4))
            mb.block(cx + sw * 0.55f, y + 0.28f, z0 + 0.5f, 0.32f, 0.26f, 0.12f, c(0xFF7CCBFF))
            if (d >= 2.8f) {
                mb.block(cx, y, z1 - 0.45f, minOf(1.4f, w - 1f), 0.3f, 0.4f, WOOD)
                mb.box(cx - 0.5f, y + 0.3f, z1 - 0.5f, cx + 0.5f, y + 0.85f, z1 - 0.44f, INK)
                mb.block(cx, y, cz + 0.2f, 0.7f, 0.22f, 0.45f, WOOD, c(0xFFF0BE86))
            }
            plant(mb, x0 + 0.35f, y, z0 + 0.35f)
        }
        RoomType.DINING -> {
            mb.layer = r.floor * 10 + 1
            mb.block(cx, y, cz, 0.14f, 0.45f, 0.14f, WOOD_DARK)
            mb.block(cx, y + 0.45f, cz, minOf(1.5f, w - 0.9f), 0.07f, minOf(0.95f, d - 1.2f), WOOD)
            for ((ox, oz) in listOf(-0.45f to -0.75f, 0.45f to -0.75f, -0.45f to 0.75f, 0.45f to 0.75f)) {
                mb.block(cx + ox, y, cz + oz, 0.34f, 0.28f, 0.34f, c(0xFF7CD37F), c(0xFF94E096))
                val back = if (oz < 0) cz + oz - 0.17f else cz + oz + 0.11f
                mb.box(cx + ox - 0.17f, y + 0.28f, back, cx + ox + 0.17f, y + 0.6f, back + 0.06f, c(0xFF62C266))
            }
            plant(mb, x1 - 0.35f, y, z0 + 0.35f)
        }
        RoomType.BEDROOM -> {
            mb.flat(x0 + 0.3f, cz, x1 - 0.3f, z1 - 0.3f, y + 0.004f, c(0xFFF4EEFF))
            mb.layer = r.floor * 10 + 1
            val bx0 = x0 + 0.2f; val bx1 = minOf(x0 + 1.5f, x1 - 0.8f)
            val bz1 = minOf(z0 + 1.9f, z1 - 0.3f)
            mb.box(bx0, y, z0 + 0.1f, bx1, y + 0.24f, bz1, WOOD, WOOD)
            mb.box(bx0 - 0.02f, y, z0 + 0.08f, bx1 + 0.02f, y + 0.62f, z0 + 0.2f, WOOD_DARK)
            mb.box(bx0 + 0.04f, y + 0.24f, z0 + 0.2f, bx1 - 0.04f, y + 0.36f, bz1 - 0.03f, WHITE)
            mb.box(bx0 + 0.15f, y + 0.36f, z0 + 0.26f, bx1 - 0.15f, y + 0.46f, z0 + 0.55f, WHITE, c(0xFFFFFFFF))
            mb.box(bx0 + 0.02f, y + 0.36f, z0 + 0.75f, bx1 - 0.02f, y + 0.42f, bz1 - 0.02f, accent.copy(alpha = 1f), c(0xFFB7A4FF))
            mb.block(bx1 + 0.3f, y, z0 + 0.4f, 0.4f, 0.38f, 0.4f, WOOD, c(0xFFF0BE86))
            mb.block(bx1 + 0.3f, y + 0.38f, z0 + 0.4f, 0.12f, 0.16f, 0.12f, c(0xFFFFE08A))
            if (w >= 3.5f) mb.box(x1 - 0.75f, y, z0 + 0.1f, x1 - 0.12f, y + 1.15f, z0 + 0.6f, c(0xFFF7E3C8), c(0xFFFFEED6))
        }
        RoomType.BATHROOM -> {
            mb.layer = r.floor * 10 + 1
            val tubEnd = minOf(x0 + 1.6f, x1 - 0.9f)
            mb.box(x0 + 0.1f, y, z0 + 0.1f, tubEnd, y + 0.45f, z0 + 0.8f, WHITE, WHITE)
            mb.box(x0 + 0.2f, y + 0.36f, z0 + 0.2f, tubEnd - 0.1f, y + 0.455f, z0 + 0.7f, c(0xFF8FD7F5), bias = -0.01f)
            mb.block(x1 - 0.45f, y, z0 + 0.45f, 0.36f, 0.32f, 0.45f, WHITE)
            mb.box(x1 - 0.65f, y + 0.32f, z0 + 0.12f, x1 - 0.25f, y + 0.62f, z0 + 0.28f, WHITE)
            if (d >= 2.5f) {
                mb.box(x0 + 0.1f, y, cz + 0.3f, x0 + 0.6f, y + 0.55f, cz + 1.0f, c(0xFF8FD3DB), WHITE)
                mb.box(x0 + 0.06f, y + 0.75f, cz + 0.35f, x0 + 0.1f, y + 1.15f, cz + 0.95f, c(0xFFD8F3FA))
            }
            mb.flat(cx - 0.4f, cz + 0.1f, cx + 0.4f, cz + 0.6f, y + 0.004f, c(0xFFFFC7E1))
        }
        RoomType.LAUNDRY -> {
            mb.layer = r.floor * 10 + 1
            for (i in 0..1) {
                val lx = x0 + 0.15f + i * 0.72f
                mb.box(lx, y, z0 + 0.1f, lx + 0.65f, y + 0.75f, z0 + 0.75f, WHITE, c(0xFFFFFFFF))
                mb.decalZ(lx + 0.15f, y + 0.18f, lx + 0.5f, y + 0.53f, z0 + 0.751f, c(0xFF7FB6D9))
                mb.decalZ(lx + 0.08f, y + 0.62f, lx + 0.25f, y + 0.68f, z0 + 0.751f, c(0xFFFF8A1F))
            }
            mb.block(x1 - 0.5f, y, cz + 0.3f, 0.55f, 0.35f, 0.45f, c(0xFFE7C493), c(0xFFF2D7AE))
        }
        RoomType.TOY -> {
            mb.flat(x0 + 0.3f, cz - 0.3f, x1 - 0.3f, z1 - 0.3f, y + 0.004f, c(0xFF8FD0FF))
            mb.disc(cx, y + 0.006f, cz + 0.4f, 0.3f, 5, c(0xFFFFFFFF), bias = -0.01f)
            mb.layer = r.floor * 10 + 1
            val bins = listOf(c(0xFF4DB6FF), c(0xFFFF7FBF), c(0xFFFFC94D))
            for ((i, bc) in bins.withIndex()) {
                val bx = x0 + 0.25f + i * 0.62f
                if (bx + 0.5f > x1 - 0.1f) break
                mb.box(bx, y, z0 + 0.15f, bx + 0.52f, y + 0.36f, z0 + 0.6f, bc, bc)
                mb.box(bx + 0.06f, y + 0.36f, z0 + 0.22f, bx + 0.46f, y + 0.4f, z0 + 0.53f, c(0xFFFFFFFF).copy(alpha = 1f), shade(bc, 0f, 1f, 0f, 1.08f, null))
            }
            mb.box(x1 - 0.5f, y, z0 + 0.1f, x1 - 0.12f, y + 0.9f, z0 + 0.6f, WOOD, c(0xFFF0BE86))
            mb.decalNegX(z0 + 0.18f, y + 0.5f, z0 + 0.52f, y + 0.72f, x1 - 0.501f, c(0xFF7CD37F))
        }
        RoomType.OFFICE -> {
            mb.layer = r.floor * 10 + 1
            mb.box(cx - 0.8f, y, z0 + 0.1f, cx + 0.8f, y + 0.5f, z0 + 0.7f, WOOD, c(0xFFF0BE86))
            mb.box(cx - 0.35f, y + 0.5f, z0 + 0.25f, cx + 0.35f, y + 0.92f, z0 + 0.3f, INK)
            mb.decalZ(cx - 0.31f, y + 0.55f, cx + 0.31f, y + 0.88f, z0 + 0.301f, c(0xFF7FD3FF))
            mb.block(cx, y, z0 + 1.05f, 0.42f, 0.3f, 0.42f, c(0xFFFF8A1F))
            mb.box(cx - 0.21f, y + 0.3f, z0 + 1.22f, cx + 0.21f, y + 0.68f, z0 + 1.28f, c(0xFFE8720C))
            plant(mb, x1 - 0.35f, y, z1 - 0.4f)
        }
        RoomType.GARAGE -> {
            mb.layer = r.floor * 10 + 1
            mb.box(cx - 0.55f, y + 0.12f, cz - 0.9f, cx + 0.55f, y + 0.5f, cz + 0.9f, c(0xFFFF6F61), c(0xFFFF8A7E))
            mb.box(cx - 0.45f, y + 0.5f, cz - 0.4f, cx + 0.45f, y + 0.85f, cz + 0.45f, c(0xFFBFE6FF), c(0xFFFF8A7E))
            for ((ox, oz) in listOf(-0.55f to -0.55f, 0.48f to -0.55f, -0.55f to 0.55f, 0.48f to 0.55f)) {
                mb.box(cx + ox, y, cz + oz - 0.17f, cx + ox + 0.08f, y + 0.3f, cz + oz + 0.17f, INK)
            }
            mb.box(x0 + 0.1f, y, z0 + 0.1f, x0 + 0.5f, y + 1.0f, z0 + 1.3f, c(0xFFB9C3CC))
        }
        RoomType.CUSTOM -> {
            mb.disc(cx, y + 0.004f, cz, minOf(w, d) * 0.3f, 8, c(0xFFFFD9A8))
            mb.layer = r.floor * 10 + 1
            mb.block(cx, y, z0 + 0.55f, 0.7f, 0.3f, 0.6f, c(0xFFFF9F5A))
            mb.box(cx - 0.35f, y + 0.3f, z0 + 0.25f, cx + 0.35f, y + 0.65f, z0 + 0.38f, c(0xFFFF8A3F))
            plant(mb, x1 - 0.35f, y, z0 + 0.35f)
        }
    }
    mb.layer = r.floor * 10 + 1
}

private fun plant(mb: MeshBuilder, x: Float, y: Float, z: Float) {
    mb.block(x, y, z, 0.24f, 0.22f, 0.24f, POT)
    mb.block(x, y + 0.22f, z, 0.32f, 0.3f, 0.32f, LEAF, c(0xFF6AD67A))
    mb.block(x + 0.04f, y + 0.52f, z - 0.03f, 0.18f, 0.16f, 0.18f, c(0xFF5BCB6B), c(0xFF7FE08D))
}

/** Mess props for a pending chore. [s] scales them down as they vanish. */
fun buildMess(mb: MeshBuilder, layout: HouseLayout, room: Room, p: PropSpec, s: Float) {
    if (s <= 0.01f) return
    val base = layout.base(room.floor) + SLAB
    val h1 = hash(p.choreId, 1); val h2 = hash(p.choreId, 2)
    var px = layout.wx(room.x + 0.8f + h1 * (room.w - 1.6f))
    var pz = layout.wz(room.y + 1.1f + h2 * (room.h - 1.6f))
    var py = base
    if (p.kind == ChoreKind.DISHES && room.type == RoomType.KITCHEN) {
        px = layout.wx(room.x + 1.4f + h1 * maxOf(0.1f, room.w - 2.6f)); pz = layout.wz(room.y + 0.42f); py = base + 0.55f
    }
    if (p.kind == ChoreKind.BED && room.type == RoomType.BEDROOM) {
        px = layout.wx(room.x + 0.85f); pz = layout.wz(room.y + 1.2f); py = base + 0.42f
    }
    mb.transform(px, py, pz, h1 * 6f, s)
    when (p.kind) {
        ChoreKind.DISHES -> {
            for (i in 0..3) mb.box(-0.16f + i * 0.01f, i * 0.045f, -0.16f, 0.16f + i * 0.01f, i * 0.045f + 0.035f, 0.16f, c(0xFFFFFFFF), if (i == 3) c(0xFFCFE6F5) else c(0xFFFFFFFF))
            mb.box(0.22f, 0f, -0.06f, 0.34f, 0.14f, 0.06f, c(0xFF7CC8FF))
        }
        ChoreKind.TOYS -> {
            val cols = listOf(0xFFFF5A5A, 0xFF4DB6FF, 0xFFFFC94D, 0xFF7CD37F, 0xFFB98CFF, 0xFFFF7FBF)
            for (i in 0..5) {
                val a = i * 1.1f + h2 * 3f
                val r = 0.18f + (i % 3) * 0.13f
                val sz = 0.1f + (i % 2) * 0.06f
                mb.block(cos(a) * r, 0f, sin(a) * r, sz, sz, sz, c(cols[i]))
            }
            mb.block(0.05f, 0f, -0.32f, 0.22f, 0.08f, 0.14f, c(0xFFFF8A1F))
        }
        ChoreKind.LAUNDRY -> {
            val cols = listOf(0xFF8FD0FF, 0xFFFF9FCB, 0xFFFFD36B, 0xFFFFFFFF)
            for (i in 0..3) mb.block(i * 0.03f - 0.04f, i * 0.06f, (i % 2) * 0.05f, 0.42f - i * 0.05f, 0.06f, 0.32f - i * 0.03f, c(cols[i]))
        }
        ChoreKind.FLOOR -> {
            for (i in 0..4) {
                val a = i * 1.3f; val r = 0.12f + i * 0.08f
                mb.flat(cos(a) * r - 0.07f, sin(a) * r - 0.05f, cos(a) * r + 0.07f, sin(a) * r + 0.05f, 0.005f, c(0xFFB3AA9C))
            }
            mb.block(0.25f, 0f, 0.2f, 0.06f, 0.05f, 0.06f, c(0xFF9C8E7A))
        }
        ChoreKind.TRASH -> {
            mb.block(0f, 0f, 0f, 0.32f, 0.36f, 0.3f, c(0xFF3F8F5A), c(0xFF4FA56A))
            mb.block(0f, 0.36f, 0f, 0.12f, 0.1f, 0.12f, c(0xFF3F8F5A))
            mb.block(0.3f, 0f, 0.1f, 0.12f, 0.1f, 0.08f, c(0xFFE8E2D4))
        }
        ChoreKind.SURFACES -> {
            mb.flat(-0.25f, -0.15f, 0.05f, 0.1f, 0.006f, c(0xFFC9B79C))
            mb.flat(0.05f, 0.05f, 0.3f, 0.25f, 0.006f, c(0xFFD2C3AA))
            mb.block(0.32f, 0f, -0.18f, 0.1f, 0.24f, 0.1f, c(0xFF2BB5FF))
            mb.block(0.32f, 0.24f, -0.16f, 0.06f, 0.06f, 0.12f, c(0xFFFFFFFF))
        }
        ChoreKind.BED -> {
            mb.block(0f, 0f, 0f, 0.6f, 0.1f, 0.45f, c(0xFFB7A4FF))
            mb.block(0.1f, 0.1f, 0.05f, 0.4f, 0.08f, 0.3f, c(0xFFD9CCFF))
            mb.block(-0.15f, 0.18f, -0.05f, 0.3f, 0.07f, 0.2f, c(0xFFFFFFFF))
        }
        ChoreKind.PLANTS -> {
            mb.block(0f, 0f, 0f, 0.26f, 0.2f, 0.18f, c(0xFF2BB5FF))
            mb.box(0.13f, 0.1f, -0.03f, 0.34f, 0.15f, 0.03f, c(0xFF2BB5FF))
            mb.box(-0.08f, 0.2f, -0.02f, 0.08f, 0.24f, 0.02f, c(0xFF1C8FD6))
        }
        ChoreKind.GENERAL -> {
            mb.block(0f, 0f, 0f, 0.38f, 0.3f, 0.32f, c(0xFFD9A86A), c(0xFFE8BF85))
            mb.box(-0.19f, 0.3f, -0.16f, 0.0f, 0.33f, 0.16f, c(0xFFC99556))
        }
    }
    mb.resetTransform()
}

private fun buildDecor(mb: MeshBuilder, layout: HouseLayout, room: Room, decor: List<String>, night: Boolean) {
    val base = layout.base(room.floor) + SLAB
    val x0 = layout.wx(room.x.toFloat()); val x1 = layout.wx((room.x + room.w).toFloat())
    val z0 = layout.wz(room.y.toFloat()); val z1 = layout.wz((room.y + room.h).toFloat())
    for (id in decor) {
        when (id) {
            "decor_plant" -> plant(mb, x1 - 0.35f, base, z0 + 0.35f)
            "decor_lamp" -> {
                mb.block(x0 + 0.35f, base, z1 - 0.9f, 0.2f, 0.04f, 0.2f, INK)
                mb.block(x0 + 0.35f, base + 0.04f, z1 - 0.9f, 0.04f, 0.75f, 0.04f, INK)
                mb.block(x0 + 0.35f, base + 0.75f, z1 - 0.9f, 0.3f, 0.22f, 0.3f, if (night) c(0xFFFFF0A0) else c(0xFFFFE08A))
            }
            "decor_rug" -> mb.disc((x0 + x1) / 2 + 0.6f, base + 0.008f, (z0 + z1) / 2 + 0.3f, 0.55f, 10, c(0xFFFFB25C))
            "decor_pumpkin" -> {
                val px = x0 + 0.45f; val pz = z1 - 0.45f
                mb.block(px, base, pz, 0.36f, 0.26f, 0.36f, c(0xFFFF7A1A), c(0xFFFF9440))
                mb.block(px, base, pz, 0.44f, 0.2f, 0.26f, c(0xFFF06A10))
                mb.block(px, base + 0.26f, pz, 0.06f, 0.1f, 0.06f, c(0xFF5E8F3A))
            }
            "decor_snowman" -> {
                val px = x0 + 0.45f; val pz = z1 - 0.45f
                mb.block(px, base, pz, 0.34f, 0.3f, 0.34f, c(0xFFFFFFFF))
                mb.block(px, base + 0.3f, pz, 0.24f, 0.22f, 0.24f, c(0xFFFFFFFF))
                mb.block(px, base + 0.4f, pz + 0.12f, 0.05f, 0.04f, 0.1f, c(0xFFFF8A1F))
            }
            "decor_tulips" -> {
                val px = x0 + 0.45f; val pz = z1 - 0.45f
                mb.block(px, base, pz, 0.24f, 0.2f, 0.24f, c(0xFFFFFFFF))
                for (i in 0..2) mb.block(px - 0.07f + i * 0.07f, base + 0.2f + (i % 2) * 0.05f, pz, 0.07f, 0.16f, 0.07f, c(if (i == 1) 0xFFFFC94D else 0xFFFF6FB5))
            }
            "decor_beachball" -> {
                val px = x0 + 0.45f; val pz = z1 - 0.45f
                mb.block(px, base, pz, 0.3f, 0.3f, 0.3f, c(0xFF2BB5FF))
                mb.block(px, base + 0.05f, pz, 0.32f, 0.2f, 0.12f, c(0xFFFFFFFF))
                mb.block(px, base + 0.05f, pz, 0.12f, 0.2f, 0.32f, c(0xFFFF6FB5))
            }
            "decor_lights" -> {
                var lx = x0 + 0.2f
                while (lx < x1 - 0.2f) {
                    mb.block(lx, base + WALL_TALL - 0.15f, z0 + 0.08f, 0.07f, 0.07f, 0.07f, c(0xFFFFE45C), bias = -0.02f)
                    lx += 0.35f
                }
            }
        }
    }
}

/** Distance helper used by the avatar sim. */
fun dist(ax: Float, az: Float, bx: Float, bz: Float): Float = abs(ax - bx) + abs(az - bz)
