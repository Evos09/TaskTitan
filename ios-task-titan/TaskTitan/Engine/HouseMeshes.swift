import Foundation

nonisolated let floorHeight = 1.45
nonisolated let wallTall = 1.15
nonisolated let wallLow = 0.16
nonisolated let slab = 0.1

/// A pending chore's mess, placed in its room.
nonisolated struct PropSpec: Hashable {
    let choreId: String
    let roomId: String
    let kind: ChoreKind
}

/// World-space layout helpers shared by the house, props and avatar.
nonisolated struct HouseLayout {
    let rooms: [Room]
    let minX: Int, maxX: Int, minZ: Int, maxZ: Int
    let cx: Double, cz: Double, radius: Double

    init(_ rooms: [Room]) {
        self.rooms = rooms
        minX = rooms.map(\.x).min() ?? 0
        maxX = rooms.map { $0.x + $0.w }.max() ?? 12
        minZ = rooms.map(\.y).min() ?? 0
        maxZ = rooms.map { $0.y + $0.h }.max() ?? 9
        cx = Double(minX + maxX) / 2
        cz = Double(minZ + maxZ) / 2
        radius = Double(max(maxX - minX, maxZ - minZ, 4)) / 2
    }

    func wx(_ gx: Double) -> Double { gx - cx }
    func wz(_ gz: Double) -> Double { gz - cz }
    func base(_ floor: Int) -> Double { Double(floor) * floorHeight }

    func center(_ r: Room) -> SIMD3<Double> {
        SIMD3(wx(Double(r.x) + Double(r.w) / 2), base(r.floor) + slab, wz(Double(r.y) + Double(r.h) / 2))
    }

    /// Where the avatar rests in a room: in front of the sofa or near the middle.
    func restSpot(_ r: Room) -> (x: Double, z: Double) {
        if r.type == .living {
            return (wx(Double(r.x) + Double(r.w) / 2), wz(Double(r.y) + min(1.45, Double(r.h) - 0.6)))
        }
        return (wx(Double(r.x) + Double(r.w) * 0.55), wz(Double(r.y) + Double(r.h) * 0.62))
    }
}

/// Hex helper that accepts ARGB literals (alpha ignored) so values match the Android source.
nonisolated func c(_ hex: UInt32) -> RGB { RGB(hex & 0xFFFFFF) }

private nonisolated let wall = c(0xFFFFF1DE)
private nonisolated let wallTop = c(0xFFF3DDBE)
private nonisolated let wood = c(0xFFE7AE70)
private nonisolated let woodDark = c(0xFFC98A4E)
private nonisolated let whiteC = c(0xFFFAF7F2)
private nonisolated let inkC = c(0xFF2B3046)
private nonisolated let leaf = c(0xFF4CBF5E)
private nonisolated let pot = c(0xFFE58A5A)

/// Deterministic 0…1 hash so a chore's mess always lands in the same spot.
nonisolated func hash01(_ s: String, _ salt: Int) -> Double {
    var h: UInt32 = UInt32(truncatingIfNeeded: 17 + salt * 31)
    for ch in s.unicodeScalars { h = h &* 131 &+ ch.value }
    return Double((h >> 1) % 1000) / 1000
}

private nonisolated func touches(_ a: Room, _ b: Room, _ side: Int) -> Bool {
    guard a.id != b.id, a.floor == b.floor else { return false }
    let overlapZ = a.y < b.y + b.h && a.y + a.h > b.y
    let overlapX = a.x < b.x + b.w && a.x + a.w > b.x
    switch side {
    case 0: return b.y + b.h == a.y && overlapX
    case 1: return b.y == a.y + a.h && overlapX
    case 2: return b.x + b.w == a.x && overlapZ
    default: return b.x == a.x + a.w && overlapZ
    }
}

/// Builds the static house: ground, stacked lower floors, the active floor's rooms,
/// cutaway walls (tall only on exterior walls facing away from camera) and furniture.
nonisolated func buildHouse(
    _ mb: MeshBuilder, _ layout: HouseLayout, topFloor: Int, camYaw: Double, night: Bool,
    pending: [PropSpec], decor: [String], furnish: Bool = true
) {
    let rooms = layout.rooms
    let pad = 1.2
    mb.layer = -20
    mb.box(layout.wx(Double(layout.minX) - pad), -0.3, layout.wz(Double(layout.minZ) - pad),
           layout.wx(Double(layout.maxX) + pad), 0, layout.wz(Double(layout.maxZ) + pad), c(0xFF7FCB6A), top: c(0xFF9BDB7E))
    mb.layer = -19
    let trees: [(Double, Double)] = [
        (Double(layout.minX) - 0.6, Double(layout.maxZ) + 0.5),
        (Double(layout.maxX) + 0.6, Double(layout.minZ) - 0.4),
        (Double(layout.maxX) + 0.6, Double(layout.maxZ) + 0.5),
    ]
    for (tx, tz) in trees {
        let x = layout.wx(tx), z = layout.wz(tz)
        mb.block(x, 0, z, 0.14, 0.3, 0.14, woodDark)
        mb.block(x, 0.3, z, 0.55, 0.4, 0.55, leaf, top: c(0xFF63D474))
        mb.block(x, 0.7, z, 0.32, 0.28, 0.32, leaf, top: c(0xFF7BE08A))
    }

    let camX = sin(camYaw), camZ = cos(camYaw)
    let win = night ? c(0xFFFFD86B) : c(0xFFBFE6FF)

    for f in 0..<max(topFloor, 0) {
        mb.layer = f * 10 + 1
        let b = layout.base(f)
        for r in rooms where r.floor == f {
            let x0 = layout.wx(Double(r.x)), x1 = layout.wx(Double(r.x + r.w))
            let z0 = layout.wz(Double(r.y)), z1 = layout.wz(Double(r.y + r.h))
            mb.box(x0, b, z0, x1, b + floorHeight, z1, wall, top: wallTop)
            var wx = x0 + 0.5
            while wx + 0.5 <= x1 - 0.2 {
                mb.decalZ(wx, b + 0.55, wx + 0.45, b + 1.05, z: z1 + 0.001, win)
                wx += 1.1
            }
            var wz = z0 + 0.5
            while wz + 0.5 <= z1 - 0.2 {
                mb.decalX(wz, b + 0.55, wz + 0.45, b + 1.05, x: x1 + 0.001, win)
                wz += 1.1
            }
        }
    }

    let floorRooms = rooms.filter { $0.floor == topFloor }
    let base = layout.base(topFloor)
    for r in floorRooms {
        let x0 = layout.wx(Double(r.x)), x1 = layout.wx(Double(r.x + r.w))
        let z0 = layout.wz(Double(r.y)), z1 = layout.wz(Double(r.y + r.h))
        mb.layer = topFloor * 10
        mb.box(x0, base, z0, x1, base + slab, z1, wallTop, top: RGB(Catalog.roomColors[r.type] ?? 0xF1EDE6))
        mb.layer = topFloor * 10 + 1
        let sides: [(Int, (Double, Double), [Double])] = [
            (0, (0, -1), [x0, z0 - 0.04, x1, z0 + 0.04]),
            (1, (0, 1), [x0, z1 - 0.04, x1, z1 + 0.04]),
            (2, (-1, 0), [x0 - 0.04, z0, x0 + 0.04, z1]),
            (3, (1, 0), [x1 - 0.04, z0, x1 + 0.04, z1]),
        ]
        for (side, n, rect) in sides {
            let interior = floorRooms.contains { touches(r, $0, side) }
            let facingAway = n.0 * camX + n.1 * camZ < -0.05
            let tall = !interior && facingAway
            let h = tall ? wallTall : wallLow
            mb.box(rect[0], base + slab, rect[1], rect[2], base + slab + h, rect[3], wall, top: wallTop)
            guard tall else { continue }
            let len = side < 2 ? x1 - x0 : z1 - z0
            guard len >= 2 else { continue }
            let mid = side < 2 ? (x0 + x1) / 2 : (z0 + z1) / 2
            let y0 = base + 0.5, y1 = base + 0.95
            switch side {
            case 0:
                mb.decalZ(mid - 0.38, y0 - 0.04, mid + 0.38, y1 + 0.04, z: z0 + 0.041, whiteC)
                mb.decalZ(mid - 0.32, y0, mid + 0.32, y1, z: z0 + 0.042, win, bias: -0.03)
            case 2:
                mb.decalX(mid - 0.38, y0 - 0.04, mid + 0.38, y1 + 0.04, x: x0 + 0.041, whiteC)
                mb.decalX(mid - 0.32, y0, mid + 0.32, y1, x: x0 + 0.042, win, bias: -0.03)
            case 1:
                mb.decalNegZ(mid - 0.38, y0 - 0.04, mid + 0.38, y1 + 0.04, z: z1 - 0.041, whiteC)
                mb.decalNegZ(mid - 0.32, y0, mid + 0.32, y1, z: z1 - 0.042, win, bias: -0.03)
            default:
                mb.decalNegX(mid - 0.38, y0 - 0.04, mid + 0.38, y1 + 0.04, x: x1 - 0.041, whiteC)
                mb.decalNegX(mid - 0.32, y0, mid + 0.32, y1, x: x1 - 0.042, win, bias: -0.03)
            }
        }
        if furnish { furnishRoom(mb, r, x0, z0, x1, z1, base + slab) }
    }

    mb.layer = topFloor * 10 + 1
    if furnish {
        for p in pending {
            guard let room = floorRooms.first(where: { $0.id == p.roomId }) else { continue }
            buildMess(mb, layout, room, p, scale: 1)
        }
        if let decorRoom = floorRooms.first(where: { $0.type == .living }) ?? floorRooms.first {
            buildDecor(mb, layout, decorRoom, decor, night: night)
        }
    }
    mb.resetTransform()
}

private nonisolated func furnishRoom(_ mb: MeshBuilder, _ r: Room, _ x0: Double, _ z0: Double, _ x1: Double, _ z1: Double, _ y: Double) {
    let w = x1 - x0, d = z1 - z0
    let cx = (x0 + x1) / 2, cz = (z0 + z1) / 2
    let accent = RGB(Catalog.roomAccents[r.type] ?? 0x8A6D4C)
    mb.layer = r.floor * 10
    switch r.type {
    case .kitchen:
        mb.flat(x0 + 0.3, z0 + 1.0, x1 - 0.3, z1 - 0.3, y: y + 0.003, c(0xFFFFF4E6))
        mb.layer = r.floor * 10 + 1
        let counterEnd = x1 - 0.95
        mb.box(x0 + 0.1, y, z0 + 0.1, counterEnd, y + 0.55, z0 + 0.72, whiteC, top: c(0xFFEFC495))
        mb.box(x0 + 0.5, y + 0.55, z0 + 0.25, x0 + 1.0, y + 0.565, z0 + 0.6, c(0xFF9FB4C7), bias: -0.01)
        mb.box(x0 + 0.72, y + 0.55, z0 + 0.15, x0 + 0.78, y + 0.75, z0 + 0.22, c(0xFFB9C3CC))
        mb.box(x1 - 0.85, y, z0 + 0.1, x1 - 0.12, y + 1.25, z0 + 0.8, c(0xFFE9F2F8), top: c(0xFFF7FBFF))
        mb.decalZ(x1 - 0.8, y + 0.7, x1 - 0.74, y + 1.0, z: z0 + 0.801, c(0xFFB0BCC8))
        if d >= 3 {
            mb.block(cx, y, cz + 0.6, 0.16, 0.42, 0.16, woodDark)
            mb.block(cx, y + 0.42, cz + 0.6, 1.1, 0.07, 0.75, wood)
        }
    case .living:
        mb.flat(cx - w * 0.32, cz - d * 0.18, cx + w * 0.32, cz + d * 0.3, y: y + 0.004, c(0xFFBFE4FF))
        mb.layer = r.floor * 10 + 1
        let sofa = c(0xFFFFFFFF)
        let sw = min(2, w - 0.8) / 2
        mb.box(cx - sw, y, z0 + 0.2, cx + sw, y + 0.28, z0 + 0.85, c(0xFFEDEBF2), top: sofa)
        mb.box(cx - sw, y + 0.28, z0 + 0.2, cx + sw, y + 0.62, z0 + 0.42, c(0xFFEDEBF2), top: sofa)
        mb.box(cx - sw - 0.16, y, z0 + 0.2, cx - sw, y + 0.42, z0 + 0.85, c(0xFFE2E0EA))
        mb.box(cx + sw, y, z0 + 0.2, cx + sw + 0.16, y + 0.42, z0 + 0.85, c(0xFFE2E0EA))
        mb.block(cx - sw * 0.55, y + 0.28, z0 + 0.5, 0.32, 0.26, 0.12, c(0xFFFF8FC4))
        mb.block(cx + sw * 0.55, y + 0.28, z0 + 0.5, 0.32, 0.26, 0.12, c(0xFF7CCBFF))
        if d >= 2.8 {
            mb.block(cx, y, z1 - 0.45, min(1.4, w - 1), 0.3, 0.4, wood)
            mb.box(cx - 0.5, y + 0.3, z1 - 0.5, cx + 0.5, y + 0.85, z1 - 0.44, inkC)
            mb.block(cx, y, cz + 0.2, 0.7, 0.22, 0.45, wood, top: c(0xFFF0BE86))
        }
        plant(mb, x0 + 0.35, y, z0 + 0.35)
    case .dining:
        mb.layer = r.floor * 10 + 1
        mb.block(cx, y, cz, 0.14, 0.45, 0.14, woodDark)
        mb.block(cx, y + 0.45, cz, min(1.5, w - 0.9), 0.07, min(0.95, d - 1.2), wood)
        for (ox, oz) in [(-0.45, -0.75), (0.45, -0.75), (-0.45, 0.75), (0.45, 0.75)] {
            mb.block(cx + ox, y, cz + oz, 0.34, 0.28, 0.34, c(0xFF7CD37F), top: c(0xFF94E096))
            let back = oz < 0 ? cz + oz - 0.17 : cz + oz + 0.11
            mb.box(cx + ox - 0.17, y + 0.28, back, cx + ox + 0.17, y + 0.6, back + 0.06, c(0xFF62C266))
        }
        plant(mb, x1 - 0.35, y, z0 + 0.35)
    case .bedroom:
        mb.flat(x0 + 0.3, cz, x1 - 0.3, z1 - 0.3, y: y + 0.004, c(0xFFF4EEFF))
        mb.layer = r.floor * 10 + 1
        let bx0 = x0 + 0.2, bx1 = min(x0 + 1.5, x1 - 0.8)
        let bz1 = min(z0 + 1.9, z1 - 0.3)
        mb.box(bx0, y, z0 + 0.1, bx1, y + 0.24, bz1, wood, top: wood)
        mb.box(bx0 - 0.02, y, z0 + 0.08, bx1 + 0.02, y + 0.62, z0 + 0.2, woodDark)
        mb.box(bx0 + 0.04, y + 0.24, z0 + 0.2, bx1 - 0.04, y + 0.36, bz1 - 0.03, whiteC)
        mb.box(bx0 + 0.15, y + 0.36, z0 + 0.26, bx1 - 0.15, y + 0.46, z0 + 0.55, whiteC, top: c(0xFFFFFFFF))
        mb.box(bx0 + 0.02, y + 0.36, z0 + 0.75, bx1 - 0.02, y + 0.42, bz1 - 0.02, accent, top: c(0xFFB7A4FF))
        mb.block(bx1 + 0.3, y, z0 + 0.4, 0.4, 0.38, 0.4, wood, top: c(0xFFF0BE86))
        mb.block(bx1 + 0.3, y + 0.38, z0 + 0.4, 0.12, 0.16, 0.12, c(0xFFFFE08A))
        if w >= 3.5 { mb.box(x1 - 0.75, y, z0 + 0.1, x1 - 0.12, y + 1.15, z0 + 0.6, c(0xFFF7E3C8), top: c(0xFFFFEED6)) }
    case .bathroom:
        mb.layer = r.floor * 10 + 1
        let tubEnd = min(x0 + 1.6, x1 - 0.9)
        mb.box(x0 + 0.1, y, z0 + 0.1, tubEnd, y + 0.45, z0 + 0.8, whiteC, top: whiteC)
        mb.box(x0 + 0.2, y + 0.36, z0 + 0.2, tubEnd - 0.1, y + 0.455, z0 + 0.7, c(0xFF8FD7F5), bias: -0.01)
        mb.block(x1 - 0.45, y, z0 + 0.45, 0.36, 0.32, 0.45, whiteC)
        mb.box(x1 - 0.65, y + 0.32, z0 + 0.12, x1 - 0.25, y + 0.62, z0 + 0.28, whiteC)
        if d >= 2.5 {
            mb.box(x0 + 0.1, y, cz + 0.3, x0 + 0.6, y + 0.55, cz + 1.0, c(0xFF8FD3DB), top: whiteC)
            mb.box(x0 + 0.06, y + 0.75, cz + 0.35, x0 + 0.1, y + 1.15, cz + 0.95, c(0xFFD8F3FA))
        }
        mb.flat(cx - 0.4, cz + 0.1, cx + 0.4, cz + 0.6, y: y + 0.004, c(0xFFFFC7E1))
    case .laundry:
        mb.layer = r.floor * 10 + 1
        for i in 0...1 {
            let lx = x0 + 0.15 + Double(i) * 0.72
            mb.box(lx, y, z0 + 0.1, lx + 0.65, y + 0.75, z0 + 0.75, whiteC, top: c(0xFFFFFFFF))
            mb.decalZ(lx + 0.15, y + 0.18, lx + 0.5, y + 0.53, z: z0 + 0.751, c(0xFF7FB6D9))
            mb.decalZ(lx + 0.08, y + 0.62, lx + 0.25, y + 0.68, z: z0 + 0.751, c(0xFFFF8A1F))
        }
        mb.block(x1 - 0.5, y, cz + 0.3, 0.55, 0.35, 0.45, c(0xFFE7C493), top: c(0xFFF2D7AE))
    case .toy:
        mb.flat(x0 + 0.3, cz - 0.3, x1 - 0.3, z1 - 0.3, y: y + 0.004, c(0xFF8FD0FF))
        mb.disc(cx, y + 0.006, cz + 0.4, r: 0.3, segments: 5, c(0xFFFFFFFF), bias: -0.01)
        mb.layer = r.floor * 10 + 1
        let bins = [c(0xFF4DB6FF), c(0xFFFF7FBF), c(0xFFFFC94D)]
        for (i, bc) in bins.enumerated() {
            let bx = x0 + 0.25 + Double(i) * 0.62
            if bx + 0.5 > x1 - 0.1 { break }
            mb.box(bx, y, z0 + 0.15, bx + 0.52, y + 0.36, z0 + 0.6, bc, top: bc)
            mb.box(bx + 0.06, y + 0.36, z0 + 0.22, bx + 0.46, y + 0.4, z0 + 0.53, c(0xFFFFFFFF), top: shade(bc, SIMD3(0, 1, 0), light: 1.08, tint: nil))
        }
        mb.box(x1 - 0.5, y, z0 + 0.1, x1 - 0.12, y + 0.9, z0 + 0.6, wood, top: c(0xFFF0BE86))
        mb.decalNegX(z0 + 0.18, y + 0.5, z0 + 0.52, y + 0.72, x: x1 - 0.501, c(0xFF7CD37F))
    case .office:
        mb.layer = r.floor * 10 + 1
        mb.box(cx - 0.8, y, z0 + 0.1, cx + 0.8, y + 0.5, z0 + 0.7, wood, top: c(0xFFF0BE86))
        mb.box(cx - 0.35, y + 0.5, z0 + 0.25, cx + 0.35, y + 0.92, z0 + 0.3, inkC)
        mb.decalZ(cx - 0.31, y + 0.55, cx + 0.31, y + 0.88, z: z0 + 0.301, c(0xFF7FD3FF))
        mb.block(cx, y, z0 + 1.05, 0.42, 0.3, 0.42, c(0xFFFF8A1F))
        mb.box(cx - 0.21, y + 0.3, z0 + 1.22, cx + 0.21, y + 0.68, z0 + 1.28, c(0xFFE8720C))
        plant(mb, x1 - 0.35, y, z1 - 0.4)
    case .garage:
        mb.layer = r.floor * 10 + 1
        mb.box(cx - 0.55, y + 0.12, cz - 0.9, cx + 0.55, y + 0.5, cz + 0.9, c(0xFFFF6F61), top: c(0xFFFF8A7E))
        mb.box(cx - 0.45, y + 0.5, cz - 0.4, cx + 0.45, y + 0.85, cz + 0.45, c(0xFFBFE6FF), top: c(0xFFFF8A7E))
        for (ox, oz) in [(-0.55, -0.55), (0.48, -0.55), (-0.55, 0.55), (0.48, 0.55)] {
            mb.box(cx + ox, y, cz + oz - 0.17, cx + ox + 0.08, y + 0.3, cz + oz + 0.17, inkC)
        }
        mb.box(x0 + 0.1, y, z0 + 0.1, x0 + 0.5, y + 1.0, z0 + 1.3, c(0xFFB9C3CC))
    case .custom:
        mb.disc(cx, y + 0.004, cz, r: min(w, d) * 0.3, segments: 8, c(0xFFFFD9A8))
        mb.layer = r.floor * 10 + 1
        mb.block(cx, y, z0 + 0.55, 0.7, 0.3, 0.6, c(0xFFFF9F5A))
        mb.box(cx - 0.35, y + 0.3, z0 + 0.25, cx + 0.35, y + 0.65, z0 + 0.38, c(0xFFFF8A3F))
        plant(mb, x1 - 0.35, y, z0 + 0.35)
    }
    mb.layer = r.floor * 10 + 1
}

private nonisolated func plant(_ mb: MeshBuilder, _ x: Double, _ y: Double, _ z: Double) {
    mb.block(x, y, z, 0.24, 0.22, 0.24, pot)
    mb.block(x, y + 0.22, z, 0.32, 0.3, 0.32, leaf, top: c(0xFF6AD67A))
    mb.block(x + 0.04, y + 0.52, z - 0.03, 0.18, 0.16, 0.18, c(0xFF5BCB6B), top: c(0xFF7FE08D))
}

/// Mess props for a pending chore. `scale` shrinks them as they vanish.
nonisolated func buildMess(_ mb: MeshBuilder, _ layout: HouseLayout, _ room: Room, _ p: PropSpec, scale s: Double) {
    guard s > 0.01 else { return }
    let base = layout.base(room.floor) + slab
    let h1 = hash01(p.choreId, 1), h2 = hash01(p.choreId, 2)
    let rw = Double(room.w), rh = Double(room.h)
    var px = layout.wx(Double(room.x) + 0.8 + h1 * (rw - 1.6))
    var pz = layout.wz(Double(room.y) + 1.1 + h2 * (rh - 1.6))
    var py = base
    if p.kind == .dishes && room.type == .kitchen {
        px = layout.wx(Double(room.x) + 1.4 + h1 * max(0.1, rw - 2.6)); pz = layout.wz(Double(room.y) + 0.42); py = base + 0.55
    }
    if p.kind == .bed && room.type == .bedroom {
        px = layout.wx(Double(room.x) + 0.85); pz = layout.wz(Double(room.y) + 1.2); py = base + 0.42
    }
    mb.transform(px, py, pz, yaw: h1 * 6, scale: s)
    switch p.kind {
    case .dishes:
        for i in 0...3 {
            let fi = Double(i)
            mb.box(-0.16 + fi * 0.01, fi * 0.045, -0.16, 0.16 + fi * 0.01, fi * 0.045 + 0.035, 0.16, c(0xFFFFFFFF), top: i == 3 ? c(0xFFCFE6F5) : c(0xFFFFFFFF))
        }
        mb.box(0.22, 0, -0.06, 0.34, 0.14, 0.06, c(0xFF7CC8FF))
    case .toys:
        let cols: [UInt32] = [0xFFFF5A5A, 0xFF4DB6FF, 0xFFFFC94D, 0xFF7CD37F, 0xFFB98CFF, 0xFFFF7FBF]
        for i in 0...5 {
            let a = Double(i) * 1.1 + h2 * 3
            let r = 0.18 + Double(i % 3) * 0.13
            let sz = 0.1 + Double(i % 2) * 0.06
            mb.block(cos(a) * r, 0, sin(a) * r, sz, sz, sz, c(cols[i]))
        }
        mb.block(0.05, 0, -0.32, 0.22, 0.08, 0.14, c(0xFFFF8A1F))
    case .laundry:
        let cols: [UInt32] = [0xFF8FD0FF, 0xFFFF9FCB, 0xFFFFD36B, 0xFFFFFFFF]
        for i in 0...3 {
            let fi = Double(i)
            mb.block(fi * 0.03 - 0.04, fi * 0.06, Double(i % 2) * 0.05, 0.42 - fi * 0.05, 0.06, 0.32 - fi * 0.03, c(cols[i]))
        }
    case .floor:
        for i in 0...4 {
            let a = Double(i) * 1.3, r = 0.12 + Double(i) * 0.08
            mb.flat(cos(a) * r - 0.07, sin(a) * r - 0.05, cos(a) * r + 0.07, sin(a) * r + 0.05, y: 0.005, c(0xFFB3AA9C))
        }
        mb.block(0.25, 0, 0.2, 0.06, 0.05, 0.06, c(0xFF9C8E7A))
    case .trash:
        mb.block(0, 0, 0, 0.32, 0.36, 0.3, c(0xFF3F8F5A), top: c(0xFF4FA56A))
        mb.block(0, 0.36, 0, 0.12, 0.1, 0.12, c(0xFF3F8F5A))
        mb.block(0.3, 0, 0.1, 0.12, 0.1, 0.08, c(0xFFE8E2D4))
    case .surfaces:
        mb.flat(-0.25, -0.15, 0.05, 0.1, y: 0.006, c(0xFFC9B79C))
        mb.flat(0.05, 0.05, 0.3, 0.25, y: 0.006, c(0xFFD2C3AA))
        mb.block(0.32, 0, -0.18, 0.1, 0.24, 0.1, c(0xFF2BB5FF))
        mb.block(0.32, 0.24, -0.16, 0.06, 0.06, 0.12, c(0xFFFFFFFF))
    case .bed:
        mb.block(0, 0, 0, 0.6, 0.1, 0.45, c(0xFFB7A4FF))
        mb.block(0.1, 0.1, 0.05, 0.4, 0.08, 0.3, c(0xFFD9CCFF))
        mb.block(-0.15, 0.18, -0.05, 0.3, 0.07, 0.2, c(0xFFFFFFFF))
    case .plants:
        mb.block(0, 0, 0, 0.26, 0.2, 0.18, c(0xFF2BB5FF))
        mb.box(0.13, 0.1, -0.03, 0.34, 0.15, 0.03, c(0xFF2BB5FF))
        mb.box(-0.08, 0.2, -0.02, 0.08, 0.24, 0.02, c(0xFF1C8FD6))
    case .general:
        mb.block(0, 0, 0, 0.38, 0.3, 0.32, c(0xFFD9A86A), top: c(0xFFE8BF85))
        mb.box(-0.19, 0.3, -0.16, 0.0, 0.33, 0.16, c(0xFFC99556))
    }
    mb.resetTransform()
}

private nonisolated func buildDecor(_ mb: MeshBuilder, _ layout: HouseLayout, _ room: Room, _ decor: [String], night: Bool) {
    let base = layout.base(room.floor) + slab
    let x0 = layout.wx(Double(room.x)), x1 = layout.wx(Double(room.x + room.w))
    let z0 = layout.wz(Double(room.y)), z1 = layout.wz(Double(room.y + room.h))
    for id in decor {
        switch id {
        case "decor_plant": plant(mb, x1 - 0.35, base, z0 + 0.35)
        case "decor_lamp":
            mb.block(x0 + 0.35, base, z1 - 0.9, 0.2, 0.04, 0.2, inkC)
            mb.block(x0 + 0.35, base + 0.04, z1 - 0.9, 0.04, 0.75, 0.04, inkC)
            mb.block(x0 + 0.35, base + 0.75, z1 - 0.9, 0.3, 0.22, 0.3, night ? c(0xFFFFF0A0) : c(0xFFFFE08A))
        case "decor_rug":
            mb.disc((x0 + x1) / 2 + 0.6, base + 0.008, (z0 + z1) / 2 + 0.3, r: 0.55, segments: 10, c(0xFFFFB25C))
        case "decor_pumpkin":
            let px = x0 + 0.45, pz = z1 - 0.45
            mb.block(px, base, pz, 0.36, 0.26, 0.36, c(0xFFFF7A1A), top: c(0xFFFF9440))
            mb.block(px, base, pz, 0.44, 0.2, 0.26, c(0xFFF06A10))
            mb.block(px, base + 0.26, pz, 0.06, 0.1, 0.06, c(0xFF5E8F3A))
        case "decor_snowman":
            let px = x0 + 0.45, pz = z1 - 0.45
            mb.block(px, base, pz, 0.34, 0.3, 0.34, c(0xFFFFFFFF))
            mb.block(px, base + 0.3, pz, 0.24, 0.22, 0.24, c(0xFFFFFFFF))
            mb.block(px, base + 0.4, pz + 0.12, 0.05, 0.04, 0.1, c(0xFFFF8A1F))
        case "decor_tulips":
            let px = x0 + 0.45, pz = z1 - 0.45
            mb.block(px, base, pz, 0.24, 0.2, 0.24, c(0xFFFFFFFF))
            for i in 0...2 {
                mb.block(px - 0.07 + Double(i) * 0.07, base + 0.2 + Double(i % 2) * 0.05, pz, 0.07, 0.16, 0.07, c(i == 1 ? 0xFFFFC94D : 0xFFFF6FB5))
            }
        case "decor_beachball":
            let px = x0 + 0.45, pz = z1 - 0.45
            mb.block(px, base, pz, 0.3, 0.3, 0.3, c(0xFF2BB5FF))
            mb.block(px, base + 0.05, pz, 0.32, 0.2, 0.12, c(0xFFFFFFFF))
            mb.block(px, base + 0.05, pz, 0.12, 0.2, 0.32, c(0xFFFF6FB5))
        case "decor_lights":
            var lx = x0 + 0.2
            while lx < x1 - 0.2 {
                mb.block(lx, base + wallTall - 0.15, z0 + 0.08, 0.07, 0.07, 0.07, c(0xFFFFE45C), bias: -0.02)
                lx += 0.35
            }
        default: break
        }
    }
}
