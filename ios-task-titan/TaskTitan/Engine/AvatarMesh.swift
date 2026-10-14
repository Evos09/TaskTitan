import Foundation

nonisolated enum Pose { case idle, walk, celebrate, relax }

/// Builds the block avatar. Energy tier only adds positive flair (glow ring,
/// bounce, sparkles); the avatar is never shown slouched or sad.
nonisolated func buildAvatar(
    _ mb: MeshBuilder, look: AvatarLook, tier: EnergyTier,
    x: Double, y: Double, z: Double, facing: Double, pose: Pose, t: Double,
    scale: Double = 1, withPet: Bool = true
) {
    let skin = RGB(Catalog.skinTones.indices.contains(look.skin) ? Catalog.skinTones[look.skin] : Catalog.skinTones[2])
    let hairCol = RGB(Catalog.hairColors.indices.contains(look.hairColor) ? Catalog.hairColors[look.hairColor] : Catalog.hairColors[1])
    let outfit = RGB(Catalog.cosmetic(look.outfit)?.color ?? 0xFF8A1F)
    let shoes = RGB(Catalog.cosmetic(look.shoes)?.color ?? 0x2BB5FF)
    let pants = c(0xFF34437A)
    let bw: Double = look.body == 0 ? 0.88 : look.body == 2 ? 1.14 : 1

    let bounceAmp: Double = switch tier {
    case .neutral: 0.006
    case .energized: 0.012
    case .thriving: 0.018
    case .radiant: 0.026
    }
    let hop: Double = switch pose {
    case .celebrate: abs(sin(t * 7)) * 0.22
    case .walk: abs(sin(t * 9)) * 0.03
    default: (sin(t * 2.4) * 0.5 + 0.5) * bounceAmp
    }

    let auraColor: RGB? = Catalog.cosmetic(look.aura).map { RGB($0.color) } ?? {
        switch tier {
        case .neutral: return nil
        case .energized: return c(0xFFBFE6FF)
        case .thriving: return c(0xFFFFD36B)
        case .radiant: return c(0xFFFFB0DA)
        }
    }()
    mb.transform(x, y, z, yaw: 0, scale: scale)
    if let aura = auraColor {
        let pulse = 1 + sin(t * 3) * 0.06
        mb.disc(0, 0.012, 0, r: 0.38 * pulse, segments: 14, aura.alpha(0.55), unlit: true, bias: 0.02)
        mb.disc(0, 0.014, 0, r: 0.26 * pulse, segments: 14, aura.alpha(0.8), unlit: true, bias: 0.01)
    } else {
        mb.disc(0, 0.012, 0, r: 0.22, segments: 12, RGB(0x000000, alpha: 0.2), unlit: true, bias: 0.02)
    }

    if withPet, let pet = look.pet {
        let petHop = pose == .celebrate ? abs(sin(t * 7 + 1)) * 0.14 : abs(sin(t * 3)) * 0.02
        mb.transform(x, y + petHop * scale, z, yaw: facing, scale: scale)
        buildPet(mb, pet)
    }

    mb.transform(x, y + hop * scale, z, yaw: facing, scale: scale)

    let sitting = pose == .relax
    let hip = sitting ? 0.22 : 0.38
    if sitting {
        mb.box(-0.26 * bw, 0, -0.28, 0.26 * bw, 0.2, 0.16, c(0xFFFF8FC4), top: c(0xFFFFA6D1))
        mb.box(-0.26 * bw, 0.2, -0.3, 0.26 * bw, 0.42, -0.16, c(0xFFFF7FBF))
        for sx in [-1.0, 1.0] {
            mb.box(sx * 0.07 - 0.05, 0.16, -0.02, sx * 0.07 + 0.05, 0.26, 0.3, pants)
            mb.box(sx * 0.07 - 0.055, 0, 0.24, sx * 0.07 + 0.055, 0.2, 0.36, pants)
            mb.box(sx * 0.07 - 0.06, 0, 0.3, sx * 0.07 + 0.06, 0.06, 0.42, shoes)
        }
    } else {
        let swing = pose == .walk ? sin(t * 9) * 0.08 : 0
        for (i, sx) in [-1.0, 1.0].enumerated() {
            let dz = i == 0 ? swing : -swing
            mb.box(sx * 0.07 - 0.05, 0.06, -0.05 + dz, sx * 0.07 + 0.05, hip, 0.05 + dz, pants)
            mb.box(sx * 0.07 - 0.06, 0, -0.06 + dz, sx * 0.07 + 0.06, 0.06, 0.09 + dz, shoes)
        }
    }

    let torsoTop = hip + 0.3
    mb.box(-0.15 * bw, hip, -0.09, 0.15 * bw, torsoTop, 0.09, outfit)
    mb.decalZ(-0.05, hip + 0.12, 0.05, hip + 0.2, z: 0.0905, RGB.white.alpha(0.9))

    let armX = 0.15 * bw + 0.045
    for (i, sx) in [-1.0, 1.0].enumerated() {
        let fi = Double(i)
        if pose == .celebrate {
            let wave = sin(t * 10 + fi) * 0.03
            mb.box(sx * armX - 0.04, torsoTop - 0.04, -0.04, sx * armX + 0.04, torsoTop + 0.26 + wave, 0.04, outfit)
            mb.box(sx * armX - 0.045, torsoTop + 0.26 + wave, -0.045, sx * armX + 0.045, torsoTop + 0.33 + wave, 0.045, skin)
        } else if sitting {
            mb.box(sx * armX - 0.04, torsoTop - 0.02, -0.12, sx * armX + 0.04, torsoTop + 0.2, -0.04, outfit)
            mb.box(sx * 0.1 - 0.04, torsoTop + 0.2, -0.16, sx * 0.1 + 0.04, torsoTop + 0.27, -0.08, skin)
        } else {
            let swing = pose == .walk ? sin(t * 9) * 0.07 * (i == 0 ? -1 : 1) : sin(t * 2 + fi) * 0.008
            mb.box(sx * armX - 0.04, hip + 0.04, -0.04 + swing, sx * armX + 0.04, torsoTop - 0.02, 0.04 + swing, outfit)
            mb.box(sx * armX - 0.045, hip - 0.02, -0.045 + swing, sx * armX + 0.045, hip + 0.05, 0.045 + swing, skin)
        }
    }

    let headY = torsoTop + 0.02
    let hs = 0.135
    mb.box(-hs, headY, -0.125, hs, headY + 0.27, 0.125, skin)
    let blink = t.truncatingRemainder(dividingBy: 3.7) < 0.12
    let eyeH = blink ? 0.008 : 0.04
    let eyeY = headY + 0.13
    mb.decalZ(-0.075, eyeY, -0.04, eyeY + eyeH, z: 0.1262, c(0xFF1E2236))
    mb.decalZ(0.04, eyeY, 0.075, eyeY + eyeH, z: 0.1262, c(0xFF1E2236))
    mb.decalZ(-0.11, headY + 0.08, -0.07, headY + 0.11, z: 0.1262, c(0xFFFF9BB8))
    mb.decalZ(0.07, headY + 0.08, 0.11, headY + 0.11, z: 0.1262, c(0xFFFF9BB8))
    let smileW = pose == .celebrate || tier == .radiant ? 0.05 : 0.035
    mb.decalZ(-smileW, headY + 0.055, smileW, headY + 0.08, z: 0.1262, c(0xFFC2415E))

    let top = headY + 0.27
    switch look.hair {
    case "hair_short":
        mb.box(-hs - 0.01, top - 0.05, -0.135, hs + 0.01, top + 0.04, 0.13, hairCol)
        mb.box(-hs - 0.01, headY + 0.1, -0.135, hs + 0.01, top, -0.08, hairCol)
    case "hair_bun":
        mb.box(-hs - 0.015, top - 0.06, -0.14, hs + 0.015, top + 0.035, 0.135, hairCol)
        mb.box(-hs - 0.015, headY + 0.06, -0.14, hs + 0.015, top, -0.07, hairCol)
        mb.block(0, top + 0.03, -0.03, 0.13, 0.12, 0.13, hairCol)
    case "hair_long":
        mb.box(-hs - 0.02, top - 0.06, -0.145, hs + 0.02, top + 0.04, 0.14, hairCol)
        mb.box(-hs - 0.02, headY - 0.12, -0.145, hs + 0.02, top, -0.06, hairCol)
        mb.box(-hs - 0.03, headY - 0.05, -0.06, -hs + 0.01, top, 0.09, hairCol)
        mb.box(hs - 0.01, headY - 0.05, -0.06, hs + 0.03, top, 0.09, hairCol)
    case "hair_spiky":
        mb.box(-hs - 0.01, top - 0.05, -0.135, hs + 0.01, top + 0.03, 0.13, hairCol)
        for i in -1...1 {
            mb.block(Double(i) * 0.08, top + 0.02, 0, 0.07, 0.1 + Double(1 - abs(i)) * 0.04, 0.07, hairCol)
        }
    default:
        mb.box(-hs - 0.02, top - 0.07, -0.145, hs + 0.02, top + 0.04, 0.14, hairCol)
        mb.box(-hs - 0.02, headY + 0.04, -0.145, hs + 0.02, top, -0.05, hairCol)
        mb.box(-hs - 0.03, headY + 0.04, -0.05, -hs + 0.01, top, 0.07, hairCol)
        mb.box(hs - 0.01, headY + 0.04, -0.05, hs + 0.03, top, 0.07, hairCol)
    }

    switch look.accessory {
    case "acc_cap":
        mb.box(-hs - 0.025, top - 0.01, -0.15, hs + 0.025, top + 0.08, 0.145, RGB(Catalog.cosmetic("acc_cap")?.color ?? 0xFF6FB5))
        mb.box(-0.11, top - 0.01, 0.145, 0.11, top + 0.02, 0.26, c(0xFFE0418F))
    case "acc_glasses":
        let frame = RGB(0x2B2B3A, alpha: 0.8)
        mb.decalZ(-0.09, eyeY - 0.025, -0.025, eyeY + 0.065, z: 0.128, frame, bias: -0.01)
        mb.decalZ(0.025, eyeY - 0.025, 0.09, eyeY + 0.065, z: 0.128, frame, bias: -0.01)
        mb.decalZ(-0.072, eyeY - 0.005, -0.043, eyeY + 0.045, z: 0.1285, c(0xFFBFE6FF), bias: -0.02)
        mb.decalZ(0.043, eyeY - 0.005, 0.072, eyeY + 0.045, z: 0.1285, c(0xFFBFE6FF), bias: -0.02)
    case "acc_crown":
        mb.box(-0.1, top + 0.03, -0.1, 0.1, top + 0.08, 0.1, c(0xFFFFC21A))
        for i in -1...1 { mb.block(Double(i) * 0.07, top + 0.08, 0.07, 0.04, 0.05, 0.04, c(0xFFFFD84D)) }
    default: break
    }
    mb.resetTransform()
}

private nonisolated func buildPet(_ mb: MeshBuilder, _ pet: String) {
    let col = RGB(Catalog.cosmetic(pet)?.color ?? 0xF0A04B)
    let ox = 0.36, oz = 0.05
    switch pet {
    case "pet_bunny":
        mb.block(ox, 0, oz, 0.16, 0.14, 0.2, col)
        mb.block(ox, 0.12, oz + 0.08, 0.13, 0.12, 0.12, col)
        mb.box(ox - 0.05, 0.24, oz + 0.06, ox - 0.02, 0.38, oz + 0.09, col)
        mb.box(ox + 0.02, 0.24, oz + 0.06, ox + 0.05, 0.38, oz + 0.09, col)
        mb.decalZ(ox - 0.035, 0.18, ox + 0.035, 0.2, z: oz + 0.141, c(0xFF1E2236))
    case "pet_cat":
        mb.block(ox, 0, oz, 0.15, 0.14, 0.28, col)
        mb.block(ox, 0.1, oz + 0.14, 0.15, 0.13, 0.12, col)
        mb.box(ox - 0.07, 0.23, oz + 0.12, ox - 0.03, 0.28, oz + 0.16, col)
        mb.box(ox + 0.03, 0.23, oz + 0.12, ox + 0.07, 0.28, oz + 0.16, col)
        mb.box(ox - 0.02, 0.08, oz - 0.2, ox + 0.02, 0.12, oz - 0.12, col)
        mb.decalZ(ox - 0.05, 0.17, ox + 0.05, 0.19, z: oz + 0.201, c(0xFF1E2236))
    default:
        mb.block(ox, 0.04, oz, 0.17, 0.12, 0.3, col)
        mb.block(ox, 0, oz + 0.1, 0.15, 0.05, 0.05, c(0xFFFFFFFF))
        mb.block(ox, 0.08, oz + 0.17, 0.15, 0.13, 0.13, col)
        mb.block(ox, 0.08, oz + 0.25, 0.08, 0.06, 0.05, c(0xFFFFFFFF))
        mb.box(ox - 0.07, 0.21, oz + 0.15, ox - 0.03, 0.29, oz + 0.19, col)
        mb.box(ox + 0.03, 0.21, oz + 0.15, ox + 0.07, 0.29, oz + 0.19, col)
        mb.decalZ(ox - 0.05, 0.17, ox + 0.05, 0.19, z: oz + 0.236, c(0xFF1E2236))
    }
}
