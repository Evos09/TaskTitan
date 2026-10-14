package com.rork.tasktitan.engine

import androidx.compose.ui.graphics.Color
import com.rork.tasktitan.data.AvatarLook
import com.rork.tasktitan.data.Catalog
import com.rork.tasktitan.data.EnergyTier
import com.rork.tasktitan.data.Health
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

enum class Pose { IDLE, WALK, CELEBRATE, RELAX }

private fun c(hex: Long): Color = Color(hex)

private fun mix(a: Color, b: Color, f: Float): Color =
    Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, a.alpha)

private fun darken(a: Color, f: Float): Color = Color(a.red * f, a.green * f, a.blue * f, a.alpha)

private val WHITE = c(0xFFF7F4EE)
private val INK = c(0xFF1E2236)
private val SICK_GREEN = c(0xFF9CCB6B)
private const val FACE_Z = 0.1362f
private const val HEAD_Y = 0.70f
private const val HEAD_TOP = 0.99f

/**
 * Builds the jointed block avatar (boy or girl, from [AvatarLook.style]). Energy tier adds
 * flair (glow ring, bounce); [health] shows how today's chores are going: a pale green tint,
 * tired eyes and a sweat drop when some are left, plus a thermometer, slouch and a green
 * cloud overhead when most are left. Finishing them makes the avatar healthy again.
 */
fun buildAvatar(
    mb: MeshBuilder,
    look: AvatarLook,
    tier: EnergyTier,
    x: Float,
    y: Float,
    z: Float,
    facing: Float,
    pose: Pose,
    t: Float,
    scale: Float = 1f,
    withPet: Boolean = true,
    health: Health = Health.HEALTHY,
) {
    val girl = look.style == "girl"
    val sick = health == Health.SICK
    val unwell = health != Health.HEALTHY
    val baseSkin = c(Catalog.skinTones.getOrElse(look.skin) { Catalog.skinTones[2] })
    val skin = when (health) {
        Health.HEALTHY -> baseSkin
        Health.QUEASY -> mix(baseSkin, SICK_GREEN, 0.2f)
        Health.SICK -> mix(baseSkin, SICK_GREEN, 0.38f)
    }
    val skinDark = darken(skin, 0.88f)
    val hairCol = c(Catalog.hairColors.getOrElse(look.hairColor) { Catalog.hairColors[1] })
    val outfit = c(Catalog.cosmetic(look.outfit)?.colorHex ?: 0xFFFF8A1F)
    val outfitDark = darken(outfit, 0.78f)
    val shoes = c(Catalog.cosmetic(look.shoes)?.colorHex ?: 0xFF2BB5FF)
    val pants = c(0xFF34437A)
    val pantsDark = c(0xFF27325E)
    val bw = when (look.body) { 0 -> 0.88f; 2 -> 1.14f; else -> 1f }
    val tw = (if (girl) 0.15f else 0.16f) * bw
    val motion = when (health) { Health.HEALTHY -> 1f; Health.QUEASY -> 0.7f; Health.SICK -> 0.4f }

    val bounceAmp = if (unwell) 0f else when (tier) {
        EnergyTier.NEUTRAL -> 0.006f
        EnergyTier.ENERGIZED -> 0.012f
        EnergyTier.THRIVING -> 0.018f
        EnergyTier.RADIANT -> 0.026f
    }
    val hop = when (pose) {
        Pose.CELEBRATE -> abs(sin(t * 7f)) * 0.22f * motion
        Pose.WALK -> abs(sin(t * 9f * motion)) * 0.03f
        else -> (sin(t * 2.4f) * 0.5f + 0.5f) * bounceAmp
    }
    val shiver = if (sick) sin(t * 40f) * 0.006f else 0f

    // Ground: energy aura, or a plain shadow while sick
    val auraItem = Catalog.cosmetic(look.aura)
    val auraColor = if (sick) null else auraItem?.let { c(it.colorHex) } ?: when (tier) {
        EnergyTier.NEUTRAL -> null
        EnergyTier.ENERGIZED -> c(0xFFBFE6FF)
        EnergyTier.THRIVING -> c(0xFFFFD36B)
        EnergyTier.RADIANT -> c(0xFFFFB0DA)
    }
    mb.transform(x, y, z, 0f, scale)
    if (auraColor != null) {
        val pulse = 1f + sin(t * 3f) * 0.06f
        mb.disc(0f, 0.012f, 0f, 0.38f * pulse, 14, auraColor.copy(alpha = 0.55f), unlit = true, bias = 0.02f)
        mb.disc(0f, 0.014f, 0f, 0.26f * pulse, 14, auraColor.copy(alpha = 0.8f), unlit = true, bias = 0.01f)
    } else {
        mb.disc(0f, 0.012f, 0f, 0.22f, 12, c(0x33000000), unlit = true, bias = 0.02f)
    }

    if (withPet && look.pet != null) {
        val petHop = if (pose == Pose.CELEBRATE) abs(sin(t * 7f + 1f)) * 0.14f else abs(sin(t * 3f)) * 0.02f
        mb.transform(x, y + petHop * scale, z, facing, scale)
        buildPet(mb, look.pet)
    }

    val sitting = pose == Pose.RELAX
    if (sitting) {
        mb.transform(x, y, z, facing, scale)
        mb.box(-0.26f * bw, 0f, -0.28f, 0.26f * bw, 0.2f, 0.16f, c(0xFFFF8FC4), c(0xFFFFA6D1))
        mb.box(-0.26f * bw, 0.2f, -0.3f, 0.26f * bw, 0.42f, -0.16f, c(0xFFFF7FBF))
    }
    val lower = if (sitting) -0.14f else 0f
    mb.transform(x + shiver * scale, y + (hop + lower) * scale, z, facing, scale)

    // Legs: hip -> knee -> cuff or socks -> sneaker
    for ((i, sx) in listOf(-1f, 1f).withIndex()) {
        val lx = sx * 0.075f
        val stride = if (pose == Pose.WALK) sin(t * 9f * motion) * (if (i == 0) 1f else -1f) else 0f
        val hipRx = if (sitting) -PI.toFloat() / 2f else stride * 0.45f
        val kneeRx = if (sitting) PI.toFloat() / 2f else max(0f, stride) * 0.75f
        mb.pushJoint(lx, 0.36f, 0f, rx = hipRx)
        mb.box(lx - 0.056f, 0.21f, -0.056f, lx + 0.056f, 0.36f, 0.056f, pants)
        mb.pushJoint(lx, 0.21f, 0f, rx = kneeRx)
        mb.box(lx - 0.053f, 0.11f, -0.053f, lx + 0.053f, 0.21f, 0.053f, pants)
        if (girl) mb.box(lx - 0.056f, 0.075f, -0.056f, lx + 0.056f, 0.135f, 0.056f, WHITE)
        else mb.box(lx - 0.06f, 0.075f, -0.06f, lx + 0.06f, 0.11f, 0.06f, pantsDark)
        mb.box(lx - 0.062f, 0.025f, -0.066f, lx + 0.062f, 0.085f, 0.095f, shoes)
        mb.box(lx - 0.066f, 0f, -0.07f, lx + 0.066f, 0.025f, 0.11f, WHITE)
        mb.box(lx - 0.058f, 0.025f, 0.07f, lx + 0.058f, 0.06f, 0.108f, WHITE)
        mb.box(lx - 0.028f, 0.085f, 0f, lx + 0.028f, 0.091f, 0.06f, WHITE)
        mb.popJoint()
        mb.popJoint()
    }

    if (girl) {
        // A-line skirt over the leggings
        mb.box(-tw - 0.008f, 0.30f, -0.105f, tw + 0.008f, 0.37f, 0.105f, pants)
        mb.box(-tw - 0.03f, 0.24f, -0.125f, tw + 0.03f, 0.30f, 0.125f, pants)
        mb.box(-tw - 0.032f, 0.225f, -0.127f, tw + 0.032f, 0.24f, 0.127f, pantsDark)
    }

    // Torso leans forward when unwell; arms and head ride along
    val slouch = when (health) { Health.HEALTHY -> 0f; Health.QUEASY -> 0.07f; Health.SICK -> 0.16f }
    mb.pushJoint(0f, 0.36f, 0f, rx = slouch)
    mb.box(-tw, 0.36f, -0.095f, tw, 0.67f, 0.095f, outfit)
    mb.box(-tw - 0.006f, 0.36f, -0.1f, tw + 0.006f, 0.395f, 0.1f, outfitDark)
    mb.box(-0.09f, 0.41f, 0.095f, 0.09f, 0.485f, 0.104f, outfitDark)
    mb.box(-0.122f, 0.64f, -0.115f, 0.122f, 0.69f, 0.045f, outfitDark)
    mb.box(-0.13f, 0.58f, -0.135f, 0.13f, 0.69f, -0.09f, outfitDark)
    mb.box(-0.047f, 0.55f, 0.095f, -0.035f, 0.64f, 0.101f, WHITE)
    mb.box(0.035f, 0.55f, 0.095f, 0.047f, 0.64f, 0.101f, WHITE)
    heartBadge(mb, 0.085f, 0.56f)
    mb.box(-0.05f, 0.67f, -0.04f, 0.05f, 0.705f, 0.04f, skin)

    for ((i, sx) in listOf(-1f, 1f).withIndex()) {
        var srx = 0f; var srz = 0f; var erx = 0f; var erz = 0f
        when {
            pose == Pose.CELEBRATE -> {
                srz = sx * (if (sick) PI.toFloat() * 0.55f else PI.toFloat() - 0.42f) + sin(t * 10f + i) * 0.12f * motion
                erz = sx * 0.25f
            }
            pose == Pose.WALK -> { srx = -sin(t * 9f * motion) * (if (i == 0) 1f else -1f) * 0.5f; erx = -0.45f }
            sitting -> { srx = -0.55f; erx = -0.7f }
            sick && i == 1 -> { srx = -0.35f; srz = sx * 0.05f; erx = -1.35f } // holding the tummy
            else -> { srx = sin(t * 2f + i) * 0.05f; srz = sx * 0.05f; erx = -0.12f }
        }
        val ax = sx * (tw + 0.046f)
        mb.pushJoint(ax, 0.65f, 0f, rx = srx, rz = srz)
        mb.box(ax - 0.046f, 0.52f, -0.046f, ax + 0.046f, 0.67f, 0.046f, outfit)
        mb.pushJoint(ax, 0.52f, 0f, rx = erx, rz = erz)
        mb.box(ax - 0.044f, 0.44f, -0.044f, ax + 0.044f, 0.52f, 0.044f, outfit)
        mb.box(ax - 0.049f, 0.415f, -0.049f, ax + 0.049f, 0.44f, 0.049f, outfitDark)
        mb.box(ax - 0.045f, 0.35f, -0.045f, ax + 0.045f, 0.415f, 0.045f, skin)
        mb.box(ax - sx * 0.045f - 0.012f, 0.375f, 0f, ax - sx * 0.045f + 0.012f, 0.405f, 0.04f, skin)
        mb.popJoint()
        mb.popJoint()
    }

    // Head
    val headRx = when { pose == Pose.CELEBRATE && !sick -> -0.12f; sick -> 0.12f; else -> 0f }
    val headRz = when { sick -> sin(t * 0.9f) * 0.08f; pose == Pose.IDLE -> sin(t * 1.3f) * 0.05f; else -> 0f }
    mb.pushJoint(0f, HEAD_Y, 0f, rx = headRx, rz = headRz)
    mb.box(-0.15f, HEAD_Y, -0.135f, 0.15f, HEAD_TOP, 0.135f, skin)
    mb.box(-0.172f, 0.81f, -0.02f, -0.15f, 0.87f, 0.035f, skinDark)
    mb.box(0.15f, 0.81f, -0.02f, 0.172f, 0.87f, 0.035f, skinDark)
    buildFace(mb, girl, health, pose, t, hairCol, skinDark)
    buildHair(mb, look, girl, sick, pose, t, hairCol, outfit, outfitDark, motion)
    buildAccessory(mb, look)
    mb.popJoint()
    mb.popJoint()

    // A little green cloud floats overhead while sick, so it reads from across the 3D house
    if (sick) {
        val by = HEAD_TOP + 0.16f + sin(t * 2f) * 0.03f
        val cloud = c(0xFF8CCB5E)
        mb.block(-0.06f, by, 0f, 0.09f, 0.07f, 0.07f, cloud)
        mb.block(0f, by + 0.03f, 0f, 0.1f, 0.08f, 0.08f, cloud)
        mb.block(0.06f, by, 0f, 0.08f, 0.06f, 0.07f, cloud)
        mb.block(0.01f, by - 0.07f, 0.01f, 0.025f, 0.04f, 0.025f, c(0xFF8FD6FF))
    }
    mb.resetTransform()
}

private fun heartBadge(mb: MeshBuilder, hx: Float, hy: Float) {
    val z0 = 0.095f; val z1 = 0.103f
    mb.box(hx - 0.03f, hy + 0.015f, z0, hx - 0.004f, hy + 0.037f, z1, WHITE)
    mb.box(hx + 0.004f, hy + 0.015f, z0, hx + 0.03f, hy + 0.037f, z1, WHITE)
    mb.box(hx - 0.022f, hy, z0, hx + 0.022f, hy + 0.02f, z1, WHITE)
    mb.box(hx - 0.008f, hy - 0.012f, z0, hx + 0.008f, hy + 0.003f, z1, WHITE)
}

private fun buildFace(mb: MeshBuilder, girl: Boolean, health: Health, pose: Pose, t: Float, hairCol: Color, skinDark: Color) {
    val z = FACE_Z
    val blink = (t % 3.7f) < 0.12f
    for (sx in listOf(-1f, 1f)) {
        val x0 = if (sx < 0) -0.088f else 0.04f
        val x1 = if (sx < 0) -0.04f else 0.088f
        when {
            health == Health.SICK -> {
                mb.decalZ(x0, 0.82f, x1, 0.832f, z, INK)
                mb.decalZ(x0 + 0.004f, 0.80f, x1 - 0.004f, 0.81f, z, skinDark)
            }
            blink -> mb.decalZ(x0, 0.83f, x1, 0.84f, z, INK)
            health == Health.QUEASY -> mb.decalZ(x0, 0.81f, x1, 0.842f, z, INK)
            else -> {
                mb.decalZ(x0, 0.803f, x1, 0.867f, z, INK)
                mb.decalZ(x0 + 0.01f, 0.841f, x0 + 0.026f, 0.857f, z, Color.White, bias = -0.03f)
            }
        }
        if (girl) {
            val ox = if (sx < 0) x0 else x1
            mb.decalZ(minOf(ox, ox + sx * 0.018f), 0.852f, maxOf(ox, ox + sx * 0.018f), 0.874f, z, INK)
        }
        // Brows: worried (inner ends raised) while unwell
        val inner = if (health != Health.HEALTHY) 0.012f else 0f
        val bx0 = if (sx < 0) -0.094f else 0.036f
        val bx1 = if (sx < 0) -0.036f else 0.094f
        val mid = (bx0 + bx1) / 2f
        val outerX0 = if (sx < 0) bx0 else mid
        val outerX1 = if (sx < 0) mid else bx1
        val innerX0 = if (sx < 0) mid else bx0
        val innerX1 = if (sx < 0) bx1 else mid
        mb.decalZ(outerX0, 0.888f, outerX1, 0.903f, z, hairCol)
        mb.decalZ(innerX0, 0.888f + inner, innerX1, 0.903f + inner, z, hairCol)
        if (health == Health.HEALTHY) {
            val px0 = if (sx < 0) -0.128f else 0.088f
            mb.decalZ(px0, 0.768f, px0 + 0.04f, 0.793f, z, c(0xFFFF9BB8))
        }
    }
    mb.box(-0.016f, 0.785f, 0.135f, 0.016f, 0.812f, 0.151f, skinDark)

    val mouth = c(0xFFC2415E)
    when {
        health == Health.SICK -> {
            mb.decalZ(-0.03f, 0.755f, 0.03f, 0.767f, z, mouth)
            mb.decalZ(-0.044f, 0.742f, -0.03f, 0.756f, z, mouth)
            mb.decalZ(0.03f, 0.742f, 0.044f, 0.756f, z, mouth)
            // thermometer
            mb.box(0.02f, 0.748f, 0.13f, 0.15f, 0.766f, 0.175f, WHITE)
            mb.box(0.15f, 0.744f, 0.13f, 0.18f, 0.77f, 0.178f, c(0xFFE0413E))
        }
        health == Health.QUEASY -> mb.decalZ(-0.03f, 0.75f, 0.03f, 0.762f, z, mouth)
        pose == Pose.CELEBRATE -> {
            mb.decalZ(-0.04f, 0.728f, 0.04f, 0.774f, z, c(0xFF7A1F35))
            mb.decalZ(-0.024f, 0.728f, 0.024f, 0.745f, z, c(0xFFFF8FA8), bias = -0.03f)
        }
        else -> {
            mb.decalZ(-0.034f, 0.745f, 0.034f, 0.761f, z, mouth)
            mb.decalZ(-0.048f, 0.756f, -0.034f, 0.772f, z, mouth)
            mb.decalZ(0.034f, 0.756f, 0.048f, 0.772f, z, mouth)
        }
    }
    if (health != Health.HEALTHY) {
        val drop = c(0xFF8FD6FF)
        mb.decalZ(-0.135f, 0.90f, -0.115f, 0.935f, z, drop, bias = -0.03f)
        mb.decalZ(-0.131f, 0.89f, -0.119f, 0.90f, z, drop, bias = -0.03f)
    }
}

private fun buildHair(
    mb: MeshBuilder, look: AvatarLook, girl: Boolean, sick: Boolean, pose: Pose, t: Float,
    hc: Color, outfit: Color, outfitDark: Color, motion: Float,
) {
    val top = HEAD_TOP
    val fz = 0.135f
    val shine = mix(hc, Color.White, 0.28f)
    mb.box(-0.168f, top - 0.07f, -0.152f, 0.168f, top + 0.045f, 0.15f, hc)
    mb.box(-0.11f, top + 0.045f, 0.01f, 0.05f, top + 0.055f, 0.12f, shine)

    fun shortCut(tuft: Boolean) {
        mb.box(-0.168f, 0.79f, -0.152f, 0.168f, top, -0.07f, hc)
        mb.box(-0.172f, 0.84f, -0.07f, -0.142f, top, 0.095f, hc)
        mb.box(0.142f, 0.84f, -0.07f, 0.172f, top, 0.095f, hc)
        mb.box(-0.152f, top - 0.075f, fz - 0.01f, -0.05f, top + 0.01f, fz + 0.02f, hc)
        mb.box(-0.05f, top - 0.055f, fz - 0.01f, 0.065f, top + 0.01f, fz + 0.022f, hc)
        mb.box(0.065f, top - 0.095f, fz - 0.01f, 0.152f, top + 0.01f, fz + 0.02f, hc)
        if (tuft) {
            mb.box(-0.01f, top + 0.045f, -0.02f, 0.03f, top + 0.085f, 0.02f, hc)
            mb.box(0.01f, top + 0.075f, -0.02f, 0.05f, top + 0.105f, 0.02f, hc)
        }
    }

    fun longLocks(bottom: Float) {
        mb.box(-0.168f, bottom, -0.152f, 0.168f, top, -0.07f, hc)
        mb.box(-0.174f, bottom + 0.01f, -0.07f, -0.142f, top, 0.112f, hc)
        mb.box(0.142f, bottom + 0.01f, -0.07f, 0.174f, top, 0.112f, hc)
        mb.box(-0.152f, top - 0.105f, fz - 0.01f, -0.03f, top + 0.01f, fz + 0.022f, hc)
        mb.box(-0.03f, top - 0.075f, fz - 0.01f, 0.07f, top + 0.01f, fz + 0.02f, hc)
        mb.box(0.07f, top - 0.05f, fz - 0.01f, 0.152f, top + 0.01f, fz + 0.018f, hc)
    }

    when (look.hair) {
        "hair_short" -> shortCut(tuft = false)
        "hair_spiky" -> {
            shortCut(tuft = false)
            for (i in -1..1) mb.block(i * 0.08f, top + 0.03f, 0f, 0.07f, 0.1f + (1 - abs(i)) * 0.04f, 0.07f, hc)
        }
        "hair_long" -> longLocks(0.62f)
        "hair_bun" -> {
            longLocks(0.80f)
            mb.block(0f, top + 0.03f, -0.03f, 0.13f, 0.12f, 0.13f, hc)
        }
        else -> if (!girl) shortCut(tuft = true) else {
            longLocks(0.69f)
            // Ponytail with a bow; it trails behind and swings with the motion
            val swing = when {
                sick -> 0f
                pose == Pose.CELEBRATE -> abs(sin(t * 7f)) * 0.45f
                pose == Pose.WALK -> sin(t * 9f * motion) * 0.12f
                else -> sin(t * 2f) * 0.05f
            }
            val tailRz = if (pose == Pose.WALK) sin(t * 4.5f) * 0.12f else sin(t * 1.3f) * 0.06f
            val py = top - 0.02f
            val pz = -0.152f
            mb.pushJoint(0f, py, pz, rx = (if (sick) 0.1f else 0.3f) + swing, rz = tailRz * motion)
            val bow = mix(outfit, Color.White, 0.48f)
            mb.box(-0.04f, py - 0.03f, pz - 0.04f, 0.04f, py + 0.03f, pz, outfitDark)
            mb.box(-0.05f, py - 0.14f, pz - 0.06f, 0.05f, py - 0.03f, pz, hc)
            mb.box(-0.045f, py - 0.25f, pz - 0.055f, 0.045f, py - 0.14f, pz, hc)
            mb.box(-0.03f, py - 0.31f, pz - 0.045f, 0.03f, py - 0.25f, pz - 0.005f, hc)
            mb.box(-0.095f, py - 0.03f, pz - 0.06f, -0.022f, py + 0.045f, pz - 0.03f, bow)
            mb.box(0.022f, py - 0.03f, pz - 0.06f, 0.095f, py + 0.045f, pz - 0.03f, bow)
            mb.box(-0.024f, py - 0.018f, pz - 0.066f, 0.024f, py + 0.032f, pz - 0.03f, outfitDark)
            mb.popJoint()
        }
    }
}

private fun buildAccessory(mb: MeshBuilder, look: AvatarLook) {
    val top = HEAD_TOP
    val acc = Catalog.cosmetic(look.accessory)
    when (look.accessory) {
        "acc_cap" -> {
            mb.box(-0.175f, top - 0.01f, -0.165f, 0.175f, top + 0.08f, 0.16f, c(acc?.colorHex ?: 0xFFFF6FB5))
            mb.box(-0.12f, top - 0.01f, 0.16f, 0.12f, top + 0.02f, 0.28f, c(0xFFE0418F))
        }
        "acc_glasses" -> {
            val frame = c(0xFF2B2B3A)
            val z = FACE_Z + 0.004f
            for (sx in listOf(-1f, 1f)) {
                val x0 = if (sx < 0) -0.1f else 0.028f
                val x1 = if (sx < 0) -0.028f else 0.1f
                mb.decalZ(x0, 0.875f, x1, 0.888f, z, frame, bias = -0.04f)
                mb.decalZ(x0, 0.79f, x1, 0.803f, z, frame, bias = -0.04f)
                mb.decalZ(x0, 0.79f, x0 + 0.012f, 0.888f, z, frame, bias = -0.04f)
                mb.decalZ(x1 - 0.012f, 0.79f, x1, 0.888f, z, frame, bias = -0.04f)
            }
            mb.decalZ(-0.028f, 0.845f, 0.028f, 0.856f, z, frame, bias = -0.04f)
        }
        "acc_crown" -> {
            mb.box(-0.1f, top + 0.03f, -0.1f, 0.1f, top + 0.08f, 0.1f, c(0xFFFFC21A))
            for (i in -1..1) mb.block(i * 0.07f, top + 0.08f, 0.07f, 0.04f, 0.05f, 0.04f, c(0xFFFFD84D))
        }
    }
}

private fun buildPet(mb: MeshBuilder, pet: String) {
    val col = c(Catalog.cosmetic(pet)?.colorHex ?: 0xFFF0A04B)
    val ox = 0.36f; val oz = 0.05f
    when (pet) {
        "pet_bunny" -> {
            mb.block(ox, 0f, oz, 0.16f, 0.14f, 0.2f, col)
            mb.block(ox, 0.12f, oz + 0.08f, 0.13f, 0.12f, 0.12f, col)
            mb.box(ox - 0.05f, 0.24f, oz + 0.06f, ox - 0.02f, 0.38f, oz + 0.09f, col)
            mb.box(ox + 0.02f, 0.24f, oz + 0.06f, ox + 0.05f, 0.38f, oz + 0.09f, col)
            mb.decalZ(ox - 0.035f, 0.18f, ox + 0.035f, 0.2f, oz + 0.141f, INK)
        }
        "pet_cat" -> {
            mb.block(ox, 0f, oz, 0.15f, 0.14f, 0.28f, col)
            mb.block(ox, 0.1f, oz + 0.14f, 0.15f, 0.13f, 0.12f, col)
            mb.box(ox - 0.07f, 0.23f, oz + 0.12f, ox - 0.03f, 0.28f, oz + 0.16f, col)
            mb.box(ox + 0.03f, 0.23f, oz + 0.12f, ox + 0.07f, 0.28f, oz + 0.16f, col)
            mb.box(ox - 0.02f, 0.08f, oz - 0.2f, ox + 0.02f, 0.12f, oz - 0.12f, col)
            mb.decalZ(ox - 0.05f, 0.17f, ox + 0.05f, 0.19f, oz + 0.201f, INK)
        }
        else -> {
            mb.block(ox, 0.04f, oz, 0.17f, 0.12f, 0.3f, col)
            mb.block(ox, 0f, oz + 0.1f, 0.15f, 0.05f, 0.05f, c(0xFFFFFFFF))
            mb.block(ox, 0.08f, oz + 0.17f, 0.15f, 0.13f, 0.13f, col)
            mb.block(ox, 0.08f, oz + 0.25f, 0.08f, 0.06f, 0.05f, c(0xFFFFFFFF))
            mb.box(ox - 0.07f, 0.21f, oz + 0.15f, ox - 0.03f, 0.29f, oz + 0.19f, col)
            mb.box(ox + 0.03f, 0.21f, oz + 0.15f, ox + 0.07f, 0.29f, oz + 0.19f, col)
            mb.decalZ(ox - 0.05f, 0.17f, ox + 0.05f, 0.19f, oz + 0.236f, INK)
        }
    }
}
