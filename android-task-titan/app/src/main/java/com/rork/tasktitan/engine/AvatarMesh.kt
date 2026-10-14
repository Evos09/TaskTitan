package com.rork.tasktitan.engine

import androidx.compose.ui.graphics.Color
import com.rork.tasktitan.data.AvatarLook
import com.rork.tasktitan.data.Catalog
import com.rork.tasktitan.data.EnergyTier
import kotlin.math.abs
import kotlin.math.sin

enum class Pose { IDLE, WALK, CELEBRATE, RELAX }

private fun c(hex: Long): Color = Color(hex)

/**
 * Builds the rigged-style block avatar. Energy tier only adds positive flair
 * (glow ring, bounce, sparkles) — the avatar is never shown slouched or sad.
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
) {
    val skin = c(Catalog.skinTones.getOrElse(look.skin) { Catalog.skinTones[2] })
    val hairCol = c(Catalog.hairColors.getOrElse(look.hairColor) { Catalog.hairColors[1] })
    val outfit = c(Catalog.cosmetic(look.outfit)?.colorHex ?: 0xFFFF8A1F)
    val shoes = c(Catalog.cosmetic(look.shoes)?.colorHex ?: 0xFF2BB5FF)
    val pants = c(0xFF34437A)
    val bw = when (look.body) { 0 -> 0.88f; 2 -> 1.14f; else -> 1f }

    val bounceAmp = when (tier) {
        EnergyTier.NEUTRAL -> 0.006f
        EnergyTier.ENERGIZED -> 0.012f
        EnergyTier.THRIVING -> 0.018f
        EnergyTier.RADIANT -> 0.026f
    }
    val hop = when (pose) {
        Pose.CELEBRATE -> abs(sin(t * 7f)) * 0.22f
        Pose.WALK -> abs(sin(t * 9f)) * 0.03f
        else -> (sin(t * 2.4f) * 0.5f + 0.5f) * bounceAmp
    }

    val auraItem = Catalog.cosmetic(look.aura)
    val auraColor = auraItem?.let { c(it.colorHex) } ?: when (tier) {
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

    mb.transform(x, y + hop * scale, z, facing, scale)

    val sitting = pose == Pose.RELAX
    val hip = if (sitting) 0.22f else 0.38f
    if (sitting) {
        mb.box(-0.26f * bw, 0f, -0.28f, 0.26f * bw, 0.2f, 0.16f, c(0xFFFF8FC4), c(0xFFFFA6D1))
        mb.box(-0.26f * bw, 0.2f, -0.3f, 0.26f * bw, 0.42f, -0.16f, c(0xFFFF7FBF))
        for (sx in listOf(-1f, 1f)) {
            mb.box(sx * 0.07f - 0.05f, 0.16f, -0.02f, sx * 0.07f + 0.05f, 0.26f, 0.3f, pants)
            mb.box(sx * 0.07f - 0.055f, 0.0f, 0.24f, sx * 0.07f + 0.055f, 0.2f, 0.36f, pants)
            mb.box(sx * 0.07f - 0.06f, 0f, 0.3f, sx * 0.07f + 0.06f, 0.06f, 0.42f, shoes)
        }
    } else {
        val swing = if (pose == Pose.WALK) sin(t * 9f) * 0.08f else 0f
        for ((i, sx) in listOf(-1f, 1f).withIndex()) {
            val dz = if (i == 0) swing else -swing
            mb.box(sx * 0.07f - 0.05f, 0.06f, -0.05f + dz, sx * 0.07f + 0.05f, hip, 0.05f + dz, pants)
            mb.box(sx * 0.07f - 0.06f, 0f, -0.06f + dz, sx * 0.07f + 0.06f, 0.06f, 0.09f + dz, shoes)
        }
    }

    val torsoTop = hip + 0.3f
    mb.box(-0.15f * bw, hip, -0.09f, 0.15f * bw, torsoTop, 0.09f, outfit)
    mb.decalZ(-0.05f, hip + 0.12f, 0.05f, hip + 0.2f, 0.0905f, Color.White.copy(alpha = 0.9f))

    val armX = 0.15f * bw + 0.045f
    for ((i, sx) in listOf(-1f, 1f).withIndex()) {
        when {
            pose == Pose.CELEBRATE -> {
                val wave = sin(t * 10f + i) * 0.03f
                mb.box(sx * armX - 0.04f, torsoTop - 0.04f, -0.04f, sx * armX + 0.04f, torsoTop + 0.26f + wave, 0.04f, outfit)
                mb.box(sx * armX - 0.045f, torsoTop + 0.26f + wave, -0.045f, sx * armX + 0.045f, torsoTop + 0.33f + wave, 0.045f, skin)
            }
            sitting -> {
                mb.box(sx * armX - 0.04f, torsoTop - 0.02f, -0.12f, sx * armX + 0.04f, torsoTop + 0.2f, -0.04f, outfit)
                mb.box(sx * 0.1f - 0.04f, torsoTop + 0.2f, -0.16f, sx * 0.1f + 0.04f, torsoTop + 0.27f, -0.08f, skin)
            }
            else -> {
                val swing = if (pose == Pose.WALK) sin(t * 9f) * 0.07f * (if (i == 0) -1f else 1f) else sin(t * 2f + i) * 0.008f
                mb.box(sx * armX - 0.04f, hip + 0.04f, -0.04f + swing, sx * armX + 0.04f, torsoTop - 0.02f, 0.04f + swing, outfit)
                mb.box(sx * armX - 0.045f, hip - 0.02f, -0.045f + swing, sx * armX + 0.045f, hip + 0.05f, 0.045f + swing, skin)
            }
        }
    }

    val headY = torsoTop + 0.02f
    val hs = 0.135f
    mb.box(-hs, headY, -0.125f, hs, headY + 0.27f, 0.125f, skin)
    val blink = (t % 3.7f) < 0.12f
    val eyeH = if (blink) 0.008f else 0.04f
    val eyeY = headY + 0.13f
    mb.decalZ(-0.075f, eyeY, -0.04f, eyeY + eyeH, 0.1262f, c(0xFF1E2236))
    mb.decalZ(0.04f, eyeY, 0.075f, eyeY + eyeH, 0.1262f, c(0xFF1E2236))
    mb.decalZ(-0.11f, headY + 0.08f, -0.07f, headY + 0.11f, 0.1262f, c(0xFFFF9BB8))
    mb.decalZ(0.07f, headY + 0.08f, 0.11f, headY + 0.11f, 0.1262f, c(0xFFFF9BB8))
    val smileW = if (pose == Pose.CELEBRATE || tier == EnergyTier.RADIANT) 0.05f else 0.035f
    mb.decalZ(-smileW, headY + 0.055f, smileW, headY + 0.08f, 0.1262f, c(0xFFC2415E))

    val top = headY + 0.27f
    when (look.hair) {
        "hair_short" -> {
            mb.box(-hs - 0.01f, top - 0.05f, -0.135f, hs + 0.01f, top + 0.04f, 0.13f, hairCol)
            mb.box(-hs - 0.01f, headY + 0.1f, -0.135f, hs + 0.01f, top, -0.08f, hairCol)
        }
        "hair_bun" -> {
            mb.box(-hs - 0.015f, top - 0.06f, -0.14f, hs + 0.015f, top + 0.035f, 0.135f, hairCol)
            mb.box(-hs - 0.015f, headY + 0.06f, -0.14f, hs + 0.015f, top, -0.07f, hairCol)
            mb.block(0f, top + 0.03f, -0.03f, 0.13f, 0.12f, 0.13f, hairCol)
        }
        "hair_long" -> {
            mb.box(-hs - 0.02f, top - 0.06f, -0.145f, hs + 0.02f, top + 0.04f, 0.14f, hairCol)
            mb.box(-hs - 0.02f, headY - 0.12f, -0.145f, hs + 0.02f, top, -0.06f, hairCol)
            mb.box(-hs - 0.03f, headY - 0.05f, -0.06f, -hs + 0.01f, top, 0.09f, hairCol)
            mb.box(hs - 0.01f, headY - 0.05f, -0.06f, hs + 0.03f, top, 0.09f, hairCol)
        }
        "hair_spiky" -> {
            mb.box(-hs - 0.01f, top - 0.05f, -0.135f, hs + 0.01f, top + 0.03f, 0.13f, hairCol)
            for (i in -1..1) mb.block(i * 0.08f, top + 0.02f, 0f, 0.07f, 0.1f + (1 - abs(i)) * 0.04f, 0.07f, hairCol)
        }
        else -> {
            mb.box(-hs - 0.02f, top - 0.07f, -0.145f, hs + 0.02f, top + 0.04f, 0.14f, hairCol)
            mb.box(-hs - 0.02f, headY + 0.04f, -0.145f, hs + 0.02f, top, -0.05f, hairCol)
            mb.box(-hs - 0.03f, headY + 0.04f, -0.05f, -hs + 0.01f, top, 0.07f, hairCol)
            mb.box(hs - 0.01f, headY + 0.04f, -0.05f, hs + 0.03f, top, 0.07f, hairCol)
        }
    }

    val acc = Catalog.cosmetic(look.accessory)
    when (look.accessory) {
        "acc_cap" -> {
            mb.box(-hs - 0.025f, top - 0.01f, -0.15f, hs + 0.025f, top + 0.08f, 0.145f, c(acc?.colorHex ?: 0xFFFF6FB5))
            mb.box(-0.11f, top - 0.01f, 0.145f, 0.11f, top + 0.02f, 0.26f, c(0xFFE0418F))
        }
        "acc_glasses" -> {
            mb.decalZ(-0.09f, eyeY - 0.025f, -0.025f, eyeY + 0.065f, 0.128f, c(0xCC2B2B3A), bias = -0.01f)
            mb.decalZ(0.025f, eyeY - 0.025f, 0.09f, eyeY + 0.065f, 0.128f, c(0xCC2B2B3A), bias = -0.01f)
            mb.decalZ(-0.072f, eyeY - 0.005f, -0.043f, eyeY + 0.045f, 0.1285f, c(0xFFBFE6FF), bias = -0.02f)
            mb.decalZ(0.043f, eyeY - 0.005f, 0.072f, eyeY + 0.045f, 0.1285f, c(0xFFBFE6FF), bias = -0.02f)
        }
        "acc_crown" -> {
            mb.box(-0.1f, top + 0.03f, -0.1f, 0.1f, top + 0.08f, 0.1f, c(0xFFFFC21A))
            for (i in -1..1) mb.block(i * 0.07f, top + 0.08f, 0.07f, 0.04f, 0.05f, 0.04f, c(0xFFFFD84D))
        }
    }
    mb.resetTransform()
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
            mb.decalZ(ox - 0.035f, 0.18f, ox + 0.035f, 0.2f, oz + 0.141f, c(0xFF1E2236))
        }
        "pet_cat" -> {
            mb.block(ox, 0f, oz, 0.15f, 0.14f, 0.28f, col)
            mb.block(ox, 0.1f, oz + 0.14f, 0.15f, 0.13f, 0.12f, col)
            mb.box(ox - 0.07f, 0.23f, oz + 0.12f, ox - 0.03f, 0.28f, oz + 0.16f, col)
            mb.box(ox + 0.03f, 0.23f, oz + 0.12f, ox + 0.07f, 0.28f, oz + 0.16f, col)
            mb.box(ox - 0.02f, 0.08f, oz - 0.2f, ox + 0.02f, 0.12f, oz - 0.12f, col)
            mb.decalZ(ox - 0.05f, 0.17f, ox + 0.05f, 0.19f, oz + 0.201f, c(0xFF1E2236))
        }
        else -> {
            mb.block(ox, 0.04f, oz, 0.17f, 0.12f, 0.3f, col)
            mb.block(ox, 0f, oz + 0.1f, 0.15f, 0.05f, 0.05f, c(0xFFFFFFFF))
            mb.block(ox, 0.08f, oz + 0.17f, 0.15f, 0.13f, 0.13f, col)
            mb.block(ox, 0.08f, oz + 0.25f, 0.08f, 0.06f, 0.05f, c(0xFFFFFFFF))
            mb.box(ox - 0.07f, 0.21f, oz + 0.15f, ox - 0.03f, 0.29f, oz + 0.19f, col)
            mb.box(ox + 0.03f, 0.21f, oz + 0.15f, ox + 0.07f, 0.29f, oz + 0.19f, col)
            mb.decalZ(ox - 0.05f, 0.17f, ox + 0.05f, 0.19f, oz + 0.236f, c(0xFF1E2236))
        }
    }
}
