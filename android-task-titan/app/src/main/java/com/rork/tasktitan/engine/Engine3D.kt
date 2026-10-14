package com.rork.tasktitan.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A single flat-shaded polygon in world space (y is up).
 * [layer] orders big groups (ground < floor slabs < objects) before depth sorting.
 */
class Face(
    val pts: FloatArray,
    val nx: Float,
    val ny: Float,
    val nz: Float,
    val color: Color,
    val layer: Int,
    val bias: Float = 0f,
    val twoSided: Boolean = false,
)

private val LIGHT: FloatArray = run {
    val x = -0.45f; val y = 1f; val z = 0.65f
    val l = sqrt(x * x + y * y + z * z)
    floatArrayOf(x / l, y / l, z / l)
}

/** Shade a base color with the fixed world light plus a global day/night tint. */
fun shade(c: Color, nx: Float, ny: Float, nz: Float, light: Float, tint: Color?): Color {
    val d = max(0f, nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2])
    val k = (0.64f + 0.36f * d) * light
    var r = c.red * k
    var g = c.green * k
    var b = c.blue * k
    if (tint != null) {
        r = r * 0.78f + tint.red * 0.22f * k
        g = g * 0.78f + tint.green * 0.22f * k
        b = b * 0.78f + tint.blue * 0.22f * k
    }
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), c.alpha)
}

/** Collects faces. Supports a simple yaw + translate transform for posable objects. */
class MeshBuilder(private val light: Float = 1f, private val tint: Color? = null) {
    val faces: ArrayList<Face> = ArrayList(512)
    var layer: Int = 1
    private var ox = 0f
    private var oy = 0f
    private var oz = 0f
    private var cy = 1f
    private var sy = 0f
    private var scale = 1f

    fun transform(x: Float, y: Float, z: Float, yaw: Float, s: Float = 1f) {
        ox = x; oy = y; oz = z; cy = cos(yaw); sy = sin(yaw); scale = s
    }

    fun resetTransform() = transform(0f, 0f, 0f, 0f, 1f)

    // Local joint frame applied before the placement transform: p' = jm·p + jt, where jm is a rotation.
    private var jm = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private var jt = floatArrayOf(0f, 0f, 0f)
    private val joints = ArrayList<Pair<FloatArray, FloatArray>>()

    /**
     * Rotates everything drawn until the matching [popJoint] about the pivot (px, py, pz),
     * first by [rz] around z, then by [rx] around x. Joints nest, so an elbow inside a
     * shoulder follows the shoulder. Positive [rx] swings the part below the pivot backwards.
     */
    fun pushJoint(px: Float, py: Float, pz: Float, rx: Float = 0f, rz: Float = 0f) {
        joints.add(jm to jt)
        val cx = cos(rx); val sx = sin(rx); val cz = cos(rz); val sz = sin(rz)
        val r = floatArrayOf(cz, -sz, 0f, cx * sz, cx * cz, -sx, sx * sz, sx * cz, cx)
        val m = FloatArray(9)
        for (row in 0..2) for (col in 0..2) {
            m[row * 3 + col] = jm[row * 3] * r[col] + jm[row * 3 + 1] * r[3 + col] + jm[row * 3 + 2] * r[6 + col]
        }
        val t = FloatArray(3)
        for (row in 0..2) {
            val viaParent = jm[row * 3] * px + jm[row * 3 + 1] * py + jm[row * 3 + 2] * pz
            val viaJoint = m[row * 3] * px + m[row * 3 + 1] * py + m[row * 3 + 2] * pz
            t[row] = viaParent - viaJoint + jt[row]
        }
        jm = m; jt = t
    }

    fun popJoint() {
        val (m, t) = joints.removeAt(joints.lastIndex)
        jm = m; jt = t
    }

    private fun tx(x: Float, z: Float): Float = ox + (x * cy + z * sy) * scale
    private fun tz(x: Float, z: Float): Float = oz + (-x * sy + z * cy) * scale
    private fun ty(y: Float): Float = oy + y * scale

    private fun addQuad(p: FloatArray, lnx0: Float, lny0: Float, lnz0: Float, color: Color, bias: Float, twoSided: Boolean, unlit: Boolean) {
        var lnx = lnx0; var lny = lny0; var lnz = lnz0
        val pts = if (joints.isEmpty()) p else FloatArray(p.size).also { q ->
            var j = 0
            while (j < p.size) {
                val x = p[j]; val y = p[j + 1]; val z = p[j + 2]
                q[j] = jm[0] * x + jm[1] * y + jm[2] * z + jt[0]
                q[j + 1] = jm[3] * x + jm[4] * y + jm[5] * z + jt[1]
                q[j + 2] = jm[6] * x + jm[7] * y + jm[8] * z + jt[2]
                j += 3
            }
            lnx = jm[0] * lnx0 + jm[1] * lny0 + jm[2] * lnz0
            lny = jm[3] * lnx0 + jm[4] * lny0 + jm[5] * lnz0
            lnz = jm[6] * lnx0 + jm[7] * lny0 + jm[8] * lnz0
        }
        val out = FloatArray(pts.size)
        var i = 0
        while (i < pts.size) {
            out[i] = tx(pts[i], pts[i + 2]); out[i + 1] = ty(pts[i + 1]); out[i + 2] = tz(pts[i], pts[i + 2])
            i += 3
        }
        val wnx = lnx * cy + lnz * sy
        val wnz = -lnx * sy + lnz * cy
        val col = if (unlit) color else shade(color, wnx, lny, wnz, light, tint)
        faces.add(Face(out, wnx, lny, wnz, col, layer, bias, twoSided))
    }

    /** Axis-aligned (in local space) box; bottom face omitted. */
    fun box(
        x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
        color: Color, top: Color = color, bias: Float = 0f, unlit: Boolean = false,
    ) {
        addQuad(floatArrayOf(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1), 0f, 1f, 0f, top, bias, false, unlit)
        addQuad(floatArrayOf(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1), 0f, 0f, 1f, color, bias, false, unlit)
        addQuad(floatArrayOf(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0), 0f, 0f, -1f, color, bias, false, unlit)
        addQuad(floatArrayOf(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1), 1f, 0f, 0f, color, bias, false, unlit)
        addQuad(floatArrayOf(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0), -1f, 0f, 0f, color, bias, false, unlit)
    }

    /** Box centered on (cx, cz) with its base at y. */
    fun block(cx: Float, y: Float, cz: Float, sx: Float, sy: Float, sz: Float, color: Color, top: Color = color, bias: Float = 0f) =
        box(cx - sx / 2, y, cz - sz / 2, cx + sx / 2, y + sy, cz + sz / 2, color, top, bias)

    /** Upward-facing flat rectangle (rugs, floors, puddles). */
    fun flat(x0: Float, z0: Float, x1: Float, z1: Float, y: Float, color: Color, bias: Float = 0f) =
        addQuad(floatArrayOf(x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1), 0f, 1f, 0f, color, bias, true, false)

    /** Flat regular polygon (round rugs, aura rings). */
    fun disc(cx: Float, y: Float, cz: Float, r: Float, segments: Int, color: Color, unlit: Boolean = false, bias: Float = 0f) {
        val p = FloatArray(segments * 3)
        for (i in 0 until segments) {
            val a = (i.toFloat() / segments) * 6.2831855f
            p[i * 3] = cx + cos(a) * r; p[i * 3 + 1] = y; p[i * 3 + 2] = cz + sin(a) * r
        }
        addQuad(p, 0f, 1f, 0f, color, bias, true, unlit)
    }

    /** Vertical decal on a face pointing +z (eyes, doors, windows). */
    fun decalZ(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, color: Color, bias: Float = -0.02f, unlit: Boolean = true) =
        addQuad(floatArrayOf(x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z), 0f, 0f, 1f, color, bias, false, unlit)

    fun decalNegZ(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, color: Color, bias: Float = -0.02f, unlit: Boolean = true) =
        addQuad(floatArrayOf(x1, y0, z, x0, y0, z, x0, y1, z, x1, y1, z), 0f, 0f, -1f, color, bias, false, unlit)

    fun decalX(z0: Float, y0: Float, z1: Float, y1: Float, x: Float, color: Color, bias: Float = -0.02f, unlit: Boolean = true) =
        addQuad(floatArrayOf(x, y0, z1, x, y0, z0, x, y1, z0, x, y1, z1), 1f, 0f, 0f, color, bias, false, unlit)

    fun decalNegX(z0: Float, y0: Float, z1: Float, y1: Float, x: Float, color: Color, bias: Float = -0.02f, unlit: Boolean = true) =
        addQuad(floatArrayOf(x, y0, z0, x, y0, z1, x, y1, z1, x, y1, z0), -1f, 0f, 0f, color, bias, false, unlit)
}

/** Orbit camera around a target point. */
data class Camera(
    val yaw: Float,
    val pitch: Float,
    val distance: Float,
    val tx: Float,
    val ty: Float,
    val tz: Float,
    val zoom: Float,
)

/** Projects and paints faces back-to-front. Reuses buffers between frames. */
class Renderer {
    private val paths = ArrayList<Path>()
    private var screen = FloatArray(0)
    private var depth = FloatArray(0)
    private var visible = IntArray(0)

    var lastScale: Float = 1f
        private set

    fun project(cam: Camera, w: Float, h: Float, x: Float, y: Float, z: Float, out: FloatArray, baseScale: Float): Boolean {
        val cyw = cos(cam.yaw); val syw = sin(cam.yaw)
        val cp = cos(cam.pitch); val sp = sin(cam.pitch)
        val px = x - cam.tx; val py = y - cam.ty; val pz = z - cam.tz
        val xr = px * cyw - pz * syw
        val zr = px * syw + pz * cyw
        val y2 = py * cp - zr * sp
        val z2 = py * sp + zr * cp
        val d = cam.distance - z2
        if (d < 0.2f) return false
        val s = baseScale * cam.zoom * cam.distance / d
        out[0] = w / 2 + xr * s
        out[1] = h * 0.54f - y2 * s
        out[2] = d
        return true
    }

    fun draw(scope: DrawScope, faces: List<Face>, cam: Camera, baseScale: Float) {
        val w = scope.size.width
        val h = scope.size.height
        val n = faces.size
        var totalPts = 0
        for (f in faces) totalPts += f.pts.size / 3
        if (screen.size < totalPts * 2) screen = FloatArray(totalPts * 2 + 64)
        if (depth.size < n) { depth = FloatArray(n + 64); visible = IntArray(n + 64) }
        while (paths.size < n) paths.add(Path())

        val cyw = cos(cam.yaw); val syw = sin(cam.yaw)
        val cp = cos(cam.pitch); val sp = sin(cam.pitch)
        val camX = cam.tx + cam.distance * cp * syw
        val camY = cam.ty + cam.distance * sp
        val camZ = cam.tz + cam.distance * cp * cyw
        val scale = baseScale * cam.zoom
        lastScale = scale

        val offsets = IntArray(n)
        var vCount = 0
        var cursor = 0
        for (i in 0 until n) {
            val f = faces[i]
            val p = f.pts
            offsets[i] = cursor
            val cnt = p.size / 3
            if (!f.twoSided) {
                val dot = f.nx * (camX - p[0]) + f.ny * (camY - p[1]) + f.nz * (camZ - p[2])
                if (dot <= 0f) { cursor += cnt * 2; continue }
            }
            var dsum = 0f
            var ok = true
            for (k in 0 until cnt) {
                val px = p[k * 3] - cam.tx; val py = p[k * 3 + 1] - cam.ty; val pz = p[k * 3 + 2] - cam.tz
                val xr = px * cyw - pz * syw
                val zr = px * syw + pz * cyw
                val y2 = py * cp - zr * sp
                val z2 = py * sp + zr * cp
                val d = cam.distance - z2
                if (d < 0.2f) { ok = false; break }
                val s = scale * cam.distance / d
                screen[cursor + k * 2] = w / 2 + xr * s
                screen[cursor + k * 2 + 1] = h * 0.54f - y2 * s
                dsum += d
            }
            if (ok) {
                depth[i] = dsum / cnt + f.bias
                visible[vCount++] = i
            }
            cursor += cnt * 2
        }

        val order = (0 until vCount).map { visible[it] }.sortedWith { a, b ->
            val la = faces[a].layer; val lb = faces[b].layer
            if (la != lb) la.compareTo(lb) else depth[b].compareTo(depth[a])
        }

        for ((slot, i) in order.withIndex()) {
            val f = faces[i]
            val cnt = f.pts.size / 3
            val o = offsets[i]
            val path = paths[slot]
            path.reset()
            path.moveTo(screen[o], screen[o + 1])
            for (k in 1 until cnt) path.lineTo(screen[o + k * 2], screen[o + k * 2 + 1])
            path.close()
            scope.drawPath(path, f.color)
        }
    }
}
