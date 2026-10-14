package com.rork.tasktitan.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.rork.tasktitan.data.AvatarLook
import com.rork.tasktitan.data.DateUtil
import com.rork.tasktitan.data.EnergyTier
import com.rork.tasktitan.data.Health
import com.rork.tasktitan.data.Room
import com.rork.tasktitan.data.RoomType
import com.rork.tasktitan.ui.theme.TT
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class CameraMode(val label: String) { ORBIT("Orbit"), FOLLOW("Follow"), TOP("Top-down") }

/** Everything the 3D view needs to draw one frame of the house. */
data class HouseScene(
    val rooms: List<Room>,
    val topFloor: Int,
    val props: List<PropSpec> = emptyList(),
    val pendingIds: Set<String> = emptySet(),
    val recentClean: Map<String, Long> = emptyMap(),
    val look: AvatarLook? = null,
    val tier: EnergyTier = EnergyTier.NEUTRAL,
    val health: Health = Health.HEALTHY,
    val decor: List<String> = emptyList(),
    val focus: Pair<String, Long>? = null,
    val roomCondition: Map<String, Int> = emptyMap(),
    val furnish: Boolean = true,
)

private class CamSim {
    var yaw = 0.72f
    var pitch = 0.78f
    var zoom = 1f
    var curYaw = 0.72f
    var curPitch = 0.78f
    var curZoom = 1f
    var tx = 0f
    var ty = 0f
    var tz = 0f
}

private class AvatarSim {
    var x = 0f
    var z = 0f
    var floor = -1
    var tx = 0f
    var tz = 0f
    var facing = 0.7f
    var pose = Pose.RELAX
    var poseStart = 0L
    var placedFor = -1
}

private const val CLEAN_ANIM_MS = 1300L
private const val CAM_DISTANCE = 14f

private fun yawKey(yaw: Float): Int {
    val cs = cos(yaw); val sn = sin(yaw)
    return (if (cs > 0.05f) 1 else if (cs < -0.05f) 2 else 0) * 3 + (if (sn > 0.05f) 1 else if (sn < -0.05f) 2 else 0)
}

private fun star(path: Path, x: Float, y: Float, r: Float) {
    path.reset()
    path.moveTo(x, y - r)
    path.quadraticTo(x, y, x + r, y)
    path.quadraticTo(x, y, x, y + r)
    path.quadraticTo(x, y, x - r, y)
    path.quadraticTo(x, y, x, y - r)
    path.close()
}

/**
 * Real-time stylized 3D house with avatar. Drag to orbit, pinch to zoom, double-tap to reset.
 * With reduced motion enabled the scene renders statically (no idle loop).
 */
@Composable
fun House3DView(
    scene: HouseScene,
    modifier: Modifier = Modifier,
    cameraMode: CameraMode = CameraMode.ORBIT,
    interactive: Boolean = true,
    autoSpin: Boolean = false,
    showLabels: Boolean = false,
    animated: Boolean = true,
    /** Inside a scrolling page: one-finger vertical drags scroll the page instead of tilting the camera. */
    verticalDragScrolls: Boolean = false,
    description: String = "3D view of your home",
) {
    val reduced = TT.reducedMotion || !animated
    val layout = remember(scene.rooms) { HouseLayout(scene.rooms) }
    val hour = remember { DateUtil.hourOfDay() }
    val night = hour < 6 || hour >= 20
    val dusk = hour in 17..19
    val light = if (night) 0.82f else 1f
    val tint: Color? = when {
        night -> Color(0xFF4A5BA8)
        dusk -> Color(0xFFFFB27A)
        else -> null
    }
    val renderer = remember { Renderer() }
    val cam = remember { CamSim() }
    val avatar = remember { AvatarSim() }
    var frame by remember { mutableLongStateOf(0L) }
    var yawK by remember { mutableIntStateOf(yawKey(cam.yaw)) }
    val sparklePath = remember { Path() }
    val measurer = rememberTextMeasurer()

    val pendingProps = remember(scene.props, scene.pendingIds) { scene.props.filter { it.choreId in scene.pendingIds } }
    val staticFaces = remember(layout, scene.topFloor, pendingProps, scene.decor, yawK, night, scene.furnish) {
        val mb = MeshBuilder(light, tint)
        buildHouse(mb, layout, scene.topFloor, cam.curYaw, night, pendingProps, scene.decor, scene.furnish)
        mb.faces
    }
    val floorRooms = remember(scene.rooms, scene.topFloor) { scene.rooms.filter { it.floor == scene.topFloor } }

    val labels: Map<String, TextLayoutResult> = remember(floorRooms, scene.roomCondition, showLabels) {
        if (!showLabels) emptyMap() else floorRooms.associate { r ->
            val pct = scene.roomCondition[r.id] ?: 100
            val text = if (pct >= 100) "${r.name} ✦ sparkling" else "${r.name} · $pct%"
            r.id to measurer.measure(text, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14254A)))
        }
    }

    fun placeAvatar() {
        val room = floorRooms.firstOrNull { it.type == RoomType.LIVING } ?: floorRooms.firstOrNull() ?: return
        val (x, z) = layout.restSpot(room)
        avatar.x = x; avatar.z = z; avatar.tx = x; avatar.tz = z
        avatar.floor = scene.topFloor
        avatar.pose = Pose.RELAX
        avatar.placedFor = scene.topFloor
    }

    LaunchedEffect(scene.topFloor, layout) {
        if (avatar.floor != scene.topFloor || avatar.placedFor != scene.topFloor) placeAvatar()
        frame++
    }

    LaunchedEffect(scene.focus, scene.topFloor) {
        val focus = scene.focus ?: return@LaunchedEffect
        val room = scene.rooms.firstOrNull { it.id == focus.first } ?: return@LaunchedEffect
        if (room.floor != scene.topFloor) return@LaunchedEffect
        if (System.currentTimeMillis() - focus.second > 8000) return@LaunchedEffect
        val (x, z) = layout.restSpot(room)
        avatar.tx = x; avatar.tz = z
        avatar.floor = room.floor
        if (reduced) {
            avatar.x = x; avatar.z = z
            avatar.pose = Pose.CELEBRATE
        } else {
            avatar.pose = Pose.WALK
        }
        avatar.poseStart = System.currentTimeMillis()
        frame++
    }

    LaunchedEffect(reduced, autoSpin, cameraMode) {
        if (reduced) {
            cam.curYaw = cam.yaw; cam.curPitch = if (cameraMode == CameraMode.TOP) 1.42f else cam.pitch
            frame++
            return@LaunchedEffect
        }
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                val ms = System.currentTimeMillis()
                if (autoSpin) cam.yaw += dt * 0.22f
                when (avatar.pose) {
                    Pose.WALK -> {
                        val dx = avatar.tx - avatar.x; val dz = avatar.tz - avatar.z
                        val d = sqrt(dx * dx + dz * dz)
                        if (d < 0.05f) {
                            avatar.pose = Pose.CELEBRATE; avatar.poseStart = ms
                        } else {
                            val step = min(d, dt * 1.7f)
                            avatar.x += dx / d * step; avatar.z += dz / d * step
                            avatar.facing = atan2(dx, dz)
                        }
                    }
                    Pose.CELEBRATE -> {
                        avatar.facing += (cam.curYaw - avatar.facing) * min(1f, dt * 5f)
                        if (ms - avatar.poseStart > 2600) { avatar.pose = Pose.RELAX; avatar.poseStart = ms }
                    }
                    Pose.IDLE -> if (ms - avatar.poseStart > 5000) avatar.pose = Pose.RELAX
                    Pose.RELAX -> avatar.facing += (cam.curYaw - avatar.facing) * min(1f, dt * 3f)
                }
                frame = now
            }
        }
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .then(
                if (interactive) Modifier
                    .pointerInput(verticalDragScrolls) {
                        val onTransform: (Offset, Float) -> Unit = { pan, zoom ->
                            cam.yaw -= pan.x * 0.008f
                            cam.pitch = (cam.pitch + pan.y * 0.005f).coerceIn(0.32f, 1.45f)
                            cam.zoom = (cam.zoom * zoom).coerceIn(0.6f, 2.8f)
                            if (reduced) { cam.curYaw = cam.yaw; cam.curPitch = cam.pitch; cam.curZoom = cam.zoom }
                            val k = yawKey(cam.yaw)
                            if (k != yawK) yawK = k
                            frame++
                        }
                        if (verticalDragScrolls) detectOrbitGestures(onTransform)
                        else detectTransformGestures { _, pan, zoom, _ -> onTransform(pan, zoom) }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            cam.yaw = 0.72f; cam.pitch = 0.78f; cam.zoom = 1f
                            if (reduced) { cam.curYaw = cam.yaw; cam.curPitch = cam.pitch; cam.curZoom = cam.zoom }
                            yawK = yawKey(cam.yaw)
                            frame++
                        })
                    }
                else Modifier
            )
    ) {
        @Suppress("UNUSED_VARIABLE") val tick = frame
        val ms = System.currentTimeMillis()
        val t = if (reduced) 0f else (ms % 100_000L) / 1000f

        val wantPitch = if (cameraMode == CameraMode.TOP) 1.42f else cam.pitch
        val wantZoom = if (cameraMode == CameraMode.FOLLOW) cam.zoom * 1.9f else cam.zoom
        val base = layout.base(scene.topFloor)
        val wantTx = if (cameraMode == CameraMode.FOLLOW) avatar.x else 0f
        val wantTz = if (cameraMode == CameraMode.FOLLOW) avatar.z else 0f
        val wantTy = base + if (cameraMode == CameraMode.FOLLOW) 0.4f else 0.2f
        val k = if (reduced) 1f else 0.14f
        cam.curYaw += (cam.yaw - cam.curYaw) * k
        cam.curPitch += (wantPitch - cam.curPitch) * k
        cam.curZoom += (wantZoom - cam.curZoom) * k
        cam.tx += (wantTx - cam.tx) * k
        cam.ty += (wantTy - cam.ty) * k
        cam.tz += (wantTz - cam.tz) * k
        val key = yawKey(cam.curYaw)
        if (key != yawK) yawK = key

        val camera = Camera(cam.curYaw, cam.curPitch, CAM_DISTANCE, cam.tx, cam.ty, cam.tz, cam.curZoom)
        val baseScale = min(size.width, size.height * 1.25f) / (layout.radius * 2.15f + 1.6f)

        val dyn = MeshBuilder(light, tint)
        dyn.layer = scene.topFloor * 10 + 1
        val bursts = ArrayList<Triple<Float, Float, Float>>()
        for (p in scene.props) {
            val at = scene.recentClean[p.choreId] ?: continue
            if (p.choreId in scene.pendingIds) continue
            val age = ms - at
            if (age > CLEAN_ANIM_MS) continue
            val room = floorRooms.firstOrNull { it.id == p.roomId } ?: continue
            val s = if (reduced) 0f else 1f - (age.toFloat() / CLEAN_ANIM_MS)
            buildMess(dyn, layout, room, p, s * s)
            val (cx, cy, cz) = layout.center(room)
            bursts.add(Triple(cx, cy + 0.3f + (1 - s) * 0.6f, cz))
        }
        val look = scene.look
        if (look != null && avatar.floor == scene.topFloor && floorRooms.isNotEmpty()) {
            dyn.layer = scene.topFloor * 10 + 1
            buildAvatar(dyn, look, scene.tier, avatar.x, base + SLAB, avatar.z, avatar.facing, avatar.pose, t, health = scene.health)
        }

        val all = if (dyn.faces.isEmpty()) staticFaces else staticFaces + dyn.faces
        renderer.draw(this, all, camera, baseScale)

        val tmp = FloatArray(3)
        val sparkleColor = Color(0xFFFFE27A)
        if (!reduced) {
            for ((i, b) in bursts.withIndex()) {
                for (j in 0 until 7) {
                    val a = j * 0.9f + i
                    val r = 0.25f + ((t * 2f + j) % 1f) * 0.5f
                    if (renderer.project(camera, size.width, size.height, b.first + cos(a) * r, b.second + (j % 3) * 0.15f, b.third + sin(a) * r, tmp, baseScale)) {
                        star(sparklePath, tmp[0], tmp[1], 7f + (j % 3) * 3f)
                        drawPath(sparklePath, if (j % 2 == 0) sparkleColor else Color.White)
                    }
                }
            }
            for (r in floorRooms) {
                val pct = scene.roomCondition[r.id] ?: continue
                if (pct < 100) continue
                val (cx, cy, cz) = layout.center(r)
                for (j in 0 until 3) {
                    val ph = (t * 0.5f + j * 0.33f + (r.x * 0.1f)) % 1f
                    val sx = cx + sin(j * 2.1f + r.y.toFloat()) * r.w * 0.3f
                    val sz = cz + cos(j * 1.7f + r.x.toFloat()) * r.h * 0.3f
                    if (renderer.project(camera, size.width, size.height, sx, cy + 0.2f + ph * 0.9f, sz, tmp, baseScale)) {
                        val alpha = sin(ph * 3.14f)
                        star(sparklePath, tmp[0], tmp[1], 5f + alpha * 5f)
                        drawPath(sparklePath, sparkleColor.copy(alpha = alpha.coerceIn(0f, 1f)))
                    }
                }
            }
            if (look != null && scene.tier == EnergyTier.RADIANT && scene.health == Health.HEALTHY && avatar.floor == scene.topFloor) {
                for (j in 0 until 4) {
                    val a = t * 1.6f + j * 1.57f
                    if (renderer.project(camera, size.width, size.height, avatar.x + cos(a) * 0.4f, base + 0.5f + sin(t * 2 + j) * 0.25f, avatar.z + sin(a) * 0.4f, tmp, baseScale)) {
                        star(sparklePath, tmp[0], tmp[1], 6f)
                        drawPath(sparklePath, Color(0xFFFFB0DA))
                    }
                }
            }
        }

        if (showLabels) drawLabels(labels, floorRooms, layout, camera, baseScale, renderer, scene.roomCondition)
    }
}

private fun DrawScope.drawLabels(
    labels: Map<String, TextLayoutResult>,
    rooms: List<Room>,
    layout: HouseLayout,
    camera: Camera,
    baseScale: Float,
    renderer: Renderer,
    condition: Map<String, Int>,
) {
    val tmp = FloatArray(3)
    for (r in rooms) {
        val tl = labels[r.id] ?: continue
        val (cx, cy, cz) = layout.center(r)
        if (!renderer.project(camera, size.width, size.height, cx, cy + 0.9f, cz, tmp, baseScale)) continue
        val w = tl.size.width + 20f
        val h = tl.size.height + 10f
        val pct = condition[r.id] ?: 100
        val bg = if (pct >= 100) Color(0xFFE2F7E6) else Color(0xF2FFFFFF)
        drawRoundRect(bg, Offset(tmp[0] - w / 2, tmp[1] - h / 2), Size(w, h), CornerRadius(h / 2))
        drawText(tl, topLeft = Offset(tmp[0] - tl.size.width / 2f, tmp[1] - tl.size.height / 2f))
    }
}

/**
 * Like [detectTransformGestures], but a one-finger drag that starts out vertical is left
 * unconsumed so an enclosing scroll container can take it. Horizontal drags and any
 * two-finger gesture still drive the camera.
 */
private suspend fun PointerInputScope.detectOrbitGestures(onTransform: (pan: Offset, zoom: Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var claimed = false
        var travelled = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            if (!claimed) {
                if (event.changes.count { it.pressed } >= 2) {
                    claimed = true
                } else {
                    travelled += event.calculatePan()
                    if (travelled.getDistance() > viewConfiguration.touchSlop) {
                        if (abs(travelled.x) > abs(travelled.y)) claimed = true else break
                    }
                }
            }
            if (claimed) {
                onTransform(event.calculatePan(), event.calculateZoom())
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
            if (event.changes.none { it.pressed }) break
        }
    }
}
