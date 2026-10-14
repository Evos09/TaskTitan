import Foundation

nonisolated enum Pose { case idle, walk, celebrate, relax }

private nonisolated func mix(_ a: RGB, _ b: RGB, _ f: Double) -> RGB {
    RGB(r: a.r + (b.r - a.r) * f, g: a.g + (b.g - a.g) * f, b: a.b + (b.b - a.b) * f, a: a.a)
}

private nonisolated func darken(_ a: RGB, _ f: Double) -> RGB { RGB(r: a.r * f, g: a.g * f, b: a.b * f, a: a.a) }

private nonisolated let avatarWhite = c(0xF7F4EE)
private nonisolated let avatarInk = c(0x1E2236)
private nonisolated let sickGreen = c(0x9CCB6B)
private nonisolated let faceZ = 0.1362
private nonisolated let headY = 0.70
private nonisolated let headTop = 0.99

/// Builds the jointed block avatar (boy or girl, from `look.style`). Energy tier adds
/// flair (glow ring, bounce); `health` shows how today's chores are going: a pale green
/// tint, tired eyes and a sweat drop when some are left, plus a thermometer, slouch and a
/// green cloud overhead when most are left. Finishing them makes the avatar healthy again.
nonisolated func buildAvatar(
    _ mb: MeshBuilder, look: AvatarLook, tier: EnergyTier,
    x: Double, y: Double, z: Double, facing: Double, pose: Pose, t: Double,
    scale: Double = 1, withPet: Bool = true, health: Health = .healthy
) {
    let girl = look.style == "girl"
    let sick = health == .sick
    let unwell = health != .healthy
    let baseSkin = RGB(Catalog.skinTones.indices.contains(look.skin) ? Catalog.skinTones[look.skin] : Catalog.skinTones[2])
    let skin: RGB = switch health {
    case .healthy: baseSkin
    case .queasy: mix(baseSkin, sickGreen, 0.2)
    case .sick: mix(baseSkin, sickGreen, 0.38)
    }
    let skinDark = darken(skin, 0.88)
    let hairCol = RGB(Catalog.hairColors.indices.contains(look.hairColor) ? Catalog.hairColors[look.hairColor] : Catalog.hairColors[1])
    let outfit = RGB(Catalog.cosmetic(look.outfit)?.color ?? 0xFF8A1F)
    let outfitDark = darken(outfit, 0.78)
    let shoes = RGB(Catalog.cosmetic(look.shoes)?.color ?? 0x2BB5FF)
    let pants = c(0x34437A)
    let pantsDark = c(0x27325E)
    let bw: Double = look.body == 0 ? 0.88 : look.body == 2 ? 1.14 : 1
    let tw = (girl ? 0.15 : 0.16) * bw
    let motion: Double = switch health { case .healthy: 1; case .queasy: 0.7; case .sick: 0.4 }

    let tierBounce: Double = switch tier {
    case .neutral: 0.006
    case .energized: 0.012
    case .thriving: 0.018
    case .radiant: 0.026
    }
    let bounceAmp = unwell ? 0 : tierBounce
    let hop: Double = switch pose {
    case .celebrate: abs(sin(t * 7)) * 0.22 * motion
    case .walk: abs(sin(t * 9 * motion)) * 0.03
    default: (sin(t * 2.4) * 0.5 + 0.5) * bounceAmp
    }
    let shiver = sick ? sin(t * 40) * 0.006 : 0

    // Ground: energy aura, or a plain shadow while sick
    let auraColor: RGB? = sick ? nil : Catalog.cosmetic(look.aura).map { RGB($0.color) } ?? {
        switch tier {
        case .neutral: return nil
        case .energized: return c(0xBFE6FF)
        case .thriving: return c(0xFFD36B)
        case .radiant: return c(0xFFB0DA)
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

    let sitting = pose == .relax
    if sitting {
        mb.transform(x, y, z, yaw: facing, scale: scale)
        mb.box(-0.26 * bw, 0, -0.28, 0.26 * bw, 0.2, 0.16, c(0xFF8FC4), top: c(0xFFA6D1))
        mb.box(-0.26 * bw, 0.2, -0.3, 0.26 * bw, 0.42, -0.16, c(0xFF7FBF))
    }
    let lower = sitting ? -0.14 : 0
    mb.transform(x + shiver * scale, y + (hop + lower) * scale, z, yaw: facing, scale: scale)

    // Legs: hip -> knee -> cuff or socks -> sneaker
    for (i, sx) in [-1.0, 1.0].enumerated() {
        let lx = sx * 0.075
        let stride = pose == .walk ? sin(t * 9 * motion) * (i == 0 ? 1 : -1) : 0
        let hipRx = sitting ? -Double.pi / 2 : stride * 0.45
        let kneeRx = sitting ? Double.pi / 2 : max(0, stride) * 0.75
        mb.pushJoint(lx, 0.36, 0, rx: hipRx)
        mb.box(lx - 0.056, 0.21, -0.056, lx + 0.056, 0.36, 0.056, pants)
        mb.pushJoint(lx, 0.21, 0, rx: kneeRx)
        mb.box(lx - 0.053, 0.11, -0.053, lx + 0.053, 0.21, 0.053, pants)
        if girl {
            mb.box(lx - 0.056, 0.075, -0.056, lx + 0.056, 0.135, 0.056, avatarWhite)
        } else {
            mb.box(lx - 0.06, 0.075, -0.06, lx + 0.06, 0.11, 0.06, pantsDark)
        }
        mb.box(lx - 0.062, 0.025, -0.066, lx + 0.062, 0.085, 0.095, shoes)
        mb.box(lx - 0.066, 0, -0.07, lx + 0.066, 0.025, 0.11, avatarWhite)
        mb.box(lx - 0.058, 0.025, 0.07, lx + 0.058, 0.06, 0.108, avatarWhite)
        mb.box(lx - 0.028, 0.085, 0, lx + 0.028, 0.091, 0.06, avatarWhite)
        mb.popJoint()
        mb.popJoint()
    }

    if girl {
        // A-line skirt over the leggings
        mb.box(-tw - 0.008, 0.30, -0.105, tw + 0.008, 0.37, 0.105, pants)
        mb.box(-tw - 0.03, 0.24, -0.125, tw + 0.03, 0.30, 0.125, pants)
        mb.box(-tw - 0.032, 0.225, -0.127, tw + 0.032, 0.24, 0.127, pantsDark)
    }

    // Torso leans forward when unwell; arms and head ride along
    let slouch: Double = switch health { case .healthy: 0; case .queasy: 0.07; case .sick: 0.16 }
    mb.pushJoint(0, 0.36, 0, rx: slouch)
    mb.box(-tw, 0.36, -0.095, tw, 0.67, 0.095, outfit)
    mb.box(-tw - 0.006, 0.36, -0.1, tw + 0.006, 0.395, 0.1, outfitDark)
    mb.box(-0.09, 0.41, 0.095, 0.09, 0.485, 0.104, outfitDark)
    mb.box(-0.122, 0.64, -0.115, 0.122, 0.69, 0.045, outfitDark)
    mb.box(-0.13, 0.58, -0.135, 0.13, 0.69, -0.09, outfitDark)
    mb.box(-0.047, 0.55, 0.095, -0.035, 0.64, 0.101, avatarWhite)
    mb.box(0.035, 0.55, 0.095, 0.047, 0.64, 0.101, avatarWhite)
    heartBadge(mb, hx: 0.085, hy: 0.56)
    mb.box(-0.05, 0.67, -0.04, 0.05, 0.705, 0.04, skin)

    for (i, sx) in [-1.0, 1.0].enumerated() {
        var srx = 0.0, srz = 0.0, erx = 0.0, erz = 0.0
        if pose == .celebrate {
            srz = sx * (sick ? Double.pi * 0.55 : Double.pi - 0.42) + sin(t * 10 + Double(i)) * 0.12 * motion
            erz = sx * 0.25
        } else if pose == .walk {
            srx = -sin(t * 9 * motion) * (i == 0 ? 1 : -1) * 0.5
            erx = -0.45
        } else if sitting {
            srx = -0.55; erx = -0.7
        } else if sick && i == 1 {
            srx = -0.35; srz = sx * 0.05; erx = -1.35 // holding the tummy
        } else {
            srx = sin(t * 2 + Double(i)) * 0.05; srz = sx * 0.05; erx = -0.12
        }
        let ax = sx * (tw + 0.046)
        mb.pushJoint(ax, 0.65, 0, rx: srx, rz: srz)
        mb.box(ax - 0.046, 0.52, -0.046, ax + 0.046, 0.67, 0.046, outfit)
        mb.pushJoint(ax, 0.52, 0, rx: erx, rz: erz)
        mb.box(ax - 0.044, 0.44, -0.044, ax + 0.044, 0.52, 0.044, outfit)
        mb.box(ax - 0.049, 0.415, -0.049, ax + 0.049, 0.44, 0.049, outfitDark)
        mb.box(ax - 0.045, 0.35, -0.045, ax + 0.045, 0.415, 0.045, skin)
        mb.box(ax - sx * 0.045 - 0.012, 0.375, 0, ax - sx * 0.045 + 0.012, 0.405, 0.04, skin)
        mb.popJoint()
        mb.popJoint()
    }

    // Head
    let headRx: Double = pose == .celebrate && !sick ? -0.12 : sick ? 0.12 : 0
    let headRz: Double = sick ? sin(t * 0.9) * 0.08 : pose == .idle ? sin(t * 1.3) * 0.05 : 0
    mb.pushJoint(0, headY, 0, rx: headRx, rz: headRz)
    mb.box(-0.15, headY, -0.135, 0.15, headTop, 0.135, skin)
    mb.box(-0.172, 0.81, -0.02, -0.15, 0.87, 0.035, skinDark)
    mb.box(0.15, 0.81, -0.02, 0.172, 0.87, 0.035, skinDark)
    buildFace(mb, girl: girl, health: health, pose: pose, t: t, hairCol: hairCol, skinDark: skinDark)
    buildHair(mb, look: look, girl: girl, sick: sick, pose: pose, t: t, hc: hairCol, outfit: outfit, outfitDark: outfitDark, motion: motion)
    buildAccessory(mb, look: look)
    mb.popJoint()
    mb.popJoint()

    // A little green cloud floats overhead while sick, so it reads from across the 3D house
    if sick {
        let by = headTop + 0.16 + sin(t * 2) * 0.03
        let cloud = c(0x8CCB5E)
        mb.block(-0.06, by, 0, 0.09, 0.07, 0.07, cloud)
        mb.block(0, by + 0.03, 0, 0.1, 0.08, 0.08, cloud)
        mb.block(0.06, by, 0, 0.08, 0.06, 0.07, cloud)
        mb.block(0.01, by - 0.07, 0.01, 0.025, 0.04, 0.025, c(0x8FD6FF))
    }
    mb.resetTransform()
}

private nonisolated func heartBadge(_ mb: MeshBuilder, hx: Double, hy: Double) {
    let z0 = 0.095, z1 = 0.103
    mb.box(hx - 0.03, hy + 0.015, z0, hx - 0.004, hy + 0.037, z1, avatarWhite)
    mb.box(hx + 0.004, hy + 0.015, z0, hx + 0.03, hy + 0.037, z1, avatarWhite)
    mb.box(hx - 0.022, hy, z0, hx + 0.022, hy + 0.02, z1, avatarWhite)
    mb.box(hx - 0.008, hy - 0.012, z0, hx + 0.008, hy + 0.003, z1, avatarWhite)
}

private nonisolated func buildFace(_ mb: MeshBuilder, girl: Bool, health: Health, pose: Pose, t: Double, hairCol: RGB, skinDark: RGB) {
    let z = faceZ
    let blink = t.truncatingRemainder(dividingBy: 3.7) < 0.12
    for sx in [-1.0, 1.0] {
        let x0 = sx < 0 ? -0.088 : 0.04
        let x1 = sx < 0 ? -0.04 : 0.088
        if health == .sick {
            mb.decalZ(x0, 0.82, x1, 0.832, z: z, avatarInk)
            mb.decalZ(x0 + 0.004, 0.80, x1 - 0.004, 0.81, z: z, skinDark)
        } else if blink {
            mb.decalZ(x0, 0.83, x1, 0.84, z: z, avatarInk)
        } else if health == .queasy {
            mb.decalZ(x0, 0.81, x1, 0.842, z: z, avatarInk)
        } else {
            mb.decalZ(x0, 0.803, x1, 0.867, z: z, avatarInk)
            mb.decalZ(x0 + 0.01, 0.841, x0 + 0.026, 0.857, z: z, RGB.white, bias: -0.03)
        }
        if girl {
            let ox = sx < 0 ? x0 : x1
            mb.decalZ(min(ox, ox + sx * 0.018), 0.852, max(ox, ox + sx * 0.018), 0.874, z: z, avatarInk)
        }
        // Brows: worried (inner ends raised) while unwell
        let inner = health != .healthy ? 0.012 : 0
        let bx0 = sx < 0 ? -0.094 : 0.036
        let bx1 = sx < 0 ? -0.036 : 0.094
        let mid = (bx0 + bx1) / 2
        mb.decalZ(sx < 0 ? bx0 : mid, 0.888, sx < 0 ? mid : bx1, 0.903, z: z, hairCol)
        mb.decalZ(sx < 0 ? mid : bx0, 0.888 + inner, sx < 0 ? bx1 : mid, 0.903 + inner, z: z, hairCol)
        if health == .healthy {
            let px0 = sx < 0 ? -0.128 : 0.088
            mb.decalZ(px0, 0.768, px0 + 0.04, 0.793, z: z, c(0xFF9BB8))
        }
    }
    mb.box(-0.016, 0.785, 0.135, 0.016, 0.812, 0.151, skinDark)

    let mouth = c(0xC2415E)
    if health == .sick {
        mb.decalZ(-0.03, 0.755, 0.03, 0.767, z: z, mouth)
        mb.decalZ(-0.044, 0.742, -0.03, 0.756, z: z, mouth)
        mb.decalZ(0.03, 0.742, 0.044, 0.756, z: z, mouth)
        // thermometer
        mb.box(0.02, 0.748, 0.13, 0.15, 0.766, 0.175, avatarWhite)
        mb.box(0.15, 0.744, 0.13, 0.18, 0.77, 0.178, c(0xE0413E))
    } else if health == .queasy {
        mb.decalZ(-0.03, 0.75, 0.03, 0.762, z: z, mouth)
    } else if pose == .celebrate {
        mb.decalZ(-0.04, 0.728, 0.04, 0.774, z: z, c(0x7A1F35))
        mb.decalZ(-0.024, 0.728, 0.024, 0.745, z: z, c(0xFF8FA8), bias: -0.03)
    } else {
        mb.decalZ(-0.034, 0.745, 0.034, 0.761, z: z, mouth)
        mb.decalZ(-0.048, 0.756, -0.034, 0.772, z: z, mouth)
        mb.decalZ(0.034, 0.756, 0.048, 0.772, z: z, mouth)
    }
    if health != .healthy {
        let drop = c(0x8FD6FF)
        mb.decalZ(-0.135, 0.90, -0.115, 0.935, z: z, drop, bias: -0.03)
        mb.decalZ(-0.131, 0.89, -0.119, 0.90, z: z, drop, bias: -0.03)
    }
}

private nonisolated func buildHair(
    _ mb: MeshBuilder, look: AvatarLook, girl: Bool, sick: Bool, pose: Pose, t: Double,
    hc: RGB, outfit: RGB, outfitDark: RGB, motion: Double
) {
    let top = headTop
    let fz = 0.135
    mb.box(-0.168, top - 0.07, -0.152, 0.168, top + 0.045, 0.15, hc)
    mb.box(-0.11, top + 0.045, 0.01, 0.05, top + 0.055, 0.12, mix(hc, RGB.white, 0.28))

    func shortCut(tuft: Bool) {
        mb.box(-0.168, 0.79, -0.152, 0.168, top, -0.07, hc)
        mb.box(-0.172, 0.84, -0.07, -0.142, top, 0.095, hc)
        mb.box(0.142, 0.84, -0.07, 0.172, top, 0.095, hc)
        mb.box(-0.152, top - 0.075, fz - 0.01, -0.05, top + 0.01, fz + 0.02, hc)
        mb.box(-0.05, top - 0.055, fz - 0.01, 0.065, top + 0.01, fz + 0.022, hc)
        mb.box(0.065, top - 0.095, fz - 0.01, 0.152, top + 0.01, fz + 0.02, hc)
        if tuft {
            mb.box(-0.01, top + 0.045, -0.02, 0.03, top + 0.085, 0.02, hc)
            mb.box(0.01, top + 0.075, -0.02, 0.05, top + 0.105, 0.02, hc)
        }
    }

    func longLocks(_ bottom: Double) {
        mb.box(-0.168, bottom, -0.152, 0.168, top, -0.07, hc)
        mb.box(-0.174, bottom + 0.01, -0.07, -0.142, top, 0.112, hc)
        mb.box(0.142, bottom + 0.01, -0.07, 0.174, top, 0.112, hc)
        mb.box(-0.152, top - 0.105, fz - 0.01, -0.03, top + 0.01, fz + 0.022, hc)
        mb.box(-0.03, top - 0.075, fz - 0.01, 0.07, top + 0.01, fz + 0.02, hc)
        mb.box(0.07, top - 0.05, fz - 0.01, 0.152, top + 0.01, fz + 0.018, hc)
    }

    switch look.hair {
    case "hair_short":
        shortCut(tuft: false)
    case "hair_spiky":
        shortCut(tuft: false)
        for i in -1...1 {
            mb.block(Double(i) * 0.08, top + 0.03, 0, 0.07, 0.1 + Double(1 - abs(i)) * 0.04, 0.07, hc)
        }
    case "hair_long":
        longLocks(0.62)
    case "hair_bun":
        longLocks(0.80)
        mb.block(0, top + 0.03, -0.03, 0.13, 0.12, 0.13, hc)
    default:
        guard girl else { shortCut(tuft: true); return }
        longLocks(0.69)
        // Ponytail with a bow; it trails behind and swings with the motion
        let swing: Double = sick ? 0
            : pose == .celebrate ? abs(sin(t * 7)) * 0.45
            : pose == .walk ? sin(t * 9 * motion) * 0.12
            : sin(t * 2) * 0.05
        let tailRz = pose == .walk ? sin(t * 4.5) * 0.12 : sin(t * 1.3) * 0.06
        let py = top - 0.02
        let pz = -0.152
        mb.pushJoint(0, py, pz, rx: (sick ? 0.1 : 0.3) + swing, rz: tailRz * motion)
        let bow = mix(outfit, RGB.white, 0.48)
        mb.box(-0.04, py - 0.03, pz - 0.04, 0.04, py + 0.03, pz, outfitDark)
        mb.box(-0.05, py - 0.14, pz - 0.06, 0.05, py - 0.03, pz, hc)
        mb.box(-0.045, py - 0.25, pz - 0.055, 0.045, py - 0.14, pz, hc)
        mb.box(-0.03, py - 0.31, pz - 0.045, 0.03, py - 0.25, pz - 0.005, hc)
        mb.box(-0.095, py - 0.03, pz - 0.06, -0.022, py + 0.045, pz - 0.03, bow)
        mb.box(0.022, py - 0.03, pz - 0.06, 0.095, py + 0.045, pz - 0.03, bow)
        mb.box(-0.024, py - 0.018, pz - 0.066, 0.024, py + 0.032, pz - 0.03, outfitDark)
        mb.popJoint()
    }
}

private nonisolated func buildAccessory(_ mb: MeshBuilder, look: AvatarLook) {
    let top = headTop
    switch look.accessory {
    case "acc_cap":
        mb.box(-0.175, top - 0.01, -0.165, 0.175, top + 0.08, 0.16, RGB(Catalog.cosmetic("acc_cap")?.color ?? 0xFF6FB5))
        mb.box(-0.12, top - 0.01, 0.16, 0.12, top + 0.02, 0.28, c(0xE0418F))
    case "acc_glasses":
        let frame = c(0x2B2B3A)
        let z = faceZ + 0.004
        for sx in [-1.0, 1.0] {
            let x0 = sx < 0 ? -0.1 : 0.028
            let x1 = sx < 0 ? -0.028 : 0.1
            mb.decalZ(x0, 0.875, x1, 0.888, z: z, frame, bias: -0.04)
            mb.decalZ(x0, 0.79, x1, 0.803, z: z, frame, bias: -0.04)
            mb.decalZ(x0, 0.79, x0 + 0.012, 0.888, z: z, frame, bias: -0.04)
            mb.decalZ(x1 - 0.012, 0.79, x1, 0.888, z: z, frame, bias: -0.04)
        }
        mb.decalZ(-0.028, 0.845, 0.028, 0.856, z: z, frame, bias: -0.04)
    case "acc_crown":
        mb.box(-0.1, top + 0.03, -0.1, 0.1, top + 0.08, 0.1, c(0xFFC21A))
        for i in -1...1 { mb.block(Double(i) * 0.07, top + 0.08, 0.07, 0.04, 0.05, 0.04, c(0xFFD84D)) }
    default: break
    }
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
        mb.decalZ(ox - 0.035, 0.18, ox + 0.035, 0.2, z: oz + 0.141, avatarInk)
    case "pet_cat":
        mb.block(ox, 0, oz, 0.15, 0.14, 0.28, col)
        mb.block(ox, 0.1, oz + 0.14, 0.15, 0.13, 0.12, col)
        mb.box(ox - 0.07, 0.23, oz + 0.12, ox - 0.03, 0.28, oz + 0.16, col)
        mb.box(ox + 0.03, 0.23, oz + 0.12, ox + 0.07, 0.28, oz + 0.16, col)
        mb.box(ox - 0.02, 0.08, oz - 0.2, ox + 0.02, 0.12, oz - 0.12, col)
        mb.decalZ(ox - 0.05, 0.17, ox + 0.05, 0.19, z: oz + 0.201, avatarInk)
    default:
        mb.block(ox, 0.04, oz, 0.17, 0.12, 0.3, col)
        mb.block(ox, 0, oz + 0.1, 0.15, 0.05, 0.05, c(0xFFFFFF))
        mb.block(ox, 0.08, oz + 0.17, 0.15, 0.13, 0.13, col)
        mb.block(ox, 0.08, oz + 0.25, 0.08, 0.06, 0.05, c(0xFFFFFF))
        mb.box(ox - 0.07, 0.21, oz + 0.15, ox - 0.03, 0.29, oz + 0.19, col)
        mb.box(ox + 0.03, 0.21, oz + 0.15, ox + 0.07, 0.29, oz + 0.19, col)
        mb.decalZ(ox - 0.05, 0.17, ox + 0.05, 0.19, z: oz + 0.236, avatarInk)
    }
}
