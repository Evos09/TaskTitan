import SwiftUI

enum CameraMode: String, CaseIterable, Identifiable {
    case orbit = "Orbit", follow = "Follow", top = "Top-down"
    var id: String { rawValue }
}

/// Everything the 3D view needs to draw the house.
struct HouseScene {
    var rooms: [Room]
    var topFloor: Int
    var props: [PropSpec] = []
    var pendingIds: Set<String> = []
    var recentClean: [String: Date] = [:]
    var look: AvatarLook?
    var tier: EnergyTier = .neutral
    var decor: [String] = []
    var focus: (roomId: String, at: Date)?
    var roomCondition: [String: Int] = [:]
    var furnish = true
}

/// Mutable per-view simulation state. A plain class so per-frame updates don't trigger SwiftUI invalidation.
private final class HouseSim {
    var yaw = 0.72, pitch = 0.78, zoom = 1.0
    var curYaw = 0.72, curPitch = 0.78, curZoom = 1.0
    var target = SIMD3<Double>(0, 0, 0)
    var ax = 0.0, az = 0.0, aFloor = -1, atx = 0.0, atz = 0.0, facing = 0.7
    var pose: Pose = .relax
    var poseStart = Date()
    var lastFrame: Date?
    var cacheKey: Int?
    var cache: [Face] = []
    var lastDrag: CGSize = .zero
    var lastMag: CGFloat = 1
}

private let cleanAnim: TimeInterval = 1.3
private let camDistance = 14.0

private func yawKey(_ yaw: Double) -> Int {
    let cs = cos(yaw), sn = sin(yaw)
    return (cs > 0.05 ? 1 : cs < -0.05 ? 2 : 0) * 3 + (sn > 0.05 ? 1 : sn < -0.05 ? 2 : 0)
}

private func star(_ p: CGPoint, _ r: CGFloat) -> Path {
    var path = Path()
    path.move(to: CGPoint(x: p.x, y: p.y - r))
    path.addQuadCurve(to: CGPoint(x: p.x + r, y: p.y), control: p)
    path.addQuadCurve(to: CGPoint(x: p.x, y: p.y + r), control: p)
    path.addQuadCurve(to: CGPoint(x: p.x - r, y: p.y), control: p)
    path.addQuadCurve(to: CGPoint(x: p.x, y: p.y - r), control: p)
    path.closeSubpath()
    return path
}

/// Real-time stylized 3D house with avatar. Drag to orbit, pinch to zoom, double-tap to reset.
/// With reduced motion the scene renders statically (no idle loop).
struct House3DView: View {
    let scene: HouseScene
    var cameraMode: CameraMode = .orbit
    var interactive = true
    var autoSpin = false
    var showLabels = false
    var animated = true
    var description = "3D view of your home"

    @Environment(\.tt) private var tt
    @State private var sim = HouseSim()
    @State private var tick = 0

    private var reduced: Bool { tt.reducedMotion || !animated }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 60, paused: reduced)) { timeline in
            Canvas { ctx, size in
                _ = tick
                render(&ctx, size: size, now: timeline.date)
            }
        }
        .contentShape(Rectangle())
        .gesture(orbitGesture, including: interactive ? .all : .subviews)
        .onTapGesture(count: 2) {
            guard interactive else { return }
            sim.yaw = 0.72; sim.pitch = 0.78; sim.zoom = 1
            tick += 1
        }
        .accessibilityElement()
        .accessibilityLabel(description)
        .accessibilityAddTraits(.isImage)
        .onAppear { placeAvatar() }
        .onChange(of: scene.topFloor) { _, _ in placeAvatar(); tick += 1 }
        .onChange(of: scene.rooms) { _, _ in placeAvatar(); tick += 1 }
        .onChange(of: scene.focus?.at) { _, _ in walkToFocus() }
    }

    private var orbitGesture: some Gesture {
        SimultaneousGesture(
            DragGesture(minimumDistance: 2)
                .onChanged { v in
                    let dx = v.translation.width - sim.lastDrag.width
                    let dy = v.translation.height - sim.lastDrag.height
                    sim.lastDrag = v.translation
                    sim.yaw -= dx * 0.008
                    sim.pitch = min(max(sim.pitch + dy * 0.005, 0.32), 1.45)
                    tick += 1
                }
                .onEnded { _ in sim.lastDrag = .zero },
            MagnifyGesture()
                .onChanged { v in
                    let f = v.magnification / sim.lastMag
                    sim.lastMag = v.magnification
                    sim.zoom = min(max(sim.zoom * f, 0.6), 2.8)
                    tick += 1
                }
                .onEnded { _ in sim.lastMag = 1 }
        )
    }

    private var layout: HouseLayout { HouseLayout(scene.rooms) }
    private var floorRooms: [Room] { scene.rooms.filter { $0.floor == scene.topFloor } }

    private func placeAvatar() {
        let rooms = floorRooms
        guard let room = rooms.first(where: { $0.type == .living }) ?? rooms.first else { return }
        let spot = layout.restSpot(room)
        sim.ax = spot.x; sim.az = spot.z; sim.atx = spot.x; sim.atz = spot.z
        sim.aFloor = scene.topFloor
        sim.pose = .relax
    }

    private func walkToFocus() {
        guard let focus = scene.focus,
              let room = scene.rooms.first(where: { $0.id == focus.roomId }),
              room.floor == scene.topFloor,
              Date().timeIntervalSince(focus.at) < 8 else { return }
        let spot = layout.restSpot(room)
        sim.atx = spot.x; sim.atz = spot.z; sim.aFloor = room.floor
        if reduced {
            sim.ax = spot.x; sim.az = spot.z; sim.pose = .celebrate
        } else {
            sim.pose = .walk
        }
        sim.poseStart = Date()
        tick += 1
    }

    private func step(now: Date) {
        let dt = min(sim.lastFrame.map { now.timeIntervalSince($0) } ?? 0.016, 0.05)
        sim.lastFrame = now
        if autoSpin { sim.yaw += dt * 0.22 }
        switch sim.pose {
        case .walk:
            let dx = sim.atx - sim.ax, dz = sim.atz - sim.az
            let d = (dx * dx + dz * dz).squareRoot()
            if d < 0.05 {
                sim.pose = .celebrate; sim.poseStart = now
            } else {
                let s = min(d, dt * 1.7)
                sim.ax += dx / d * s; sim.az += dz / d * s
                sim.facing = atan2(dx, dz)
            }
        case .celebrate:
            sim.facing += (sim.curYaw - sim.facing) * min(1, dt * 5)
            if now.timeIntervalSince(sim.poseStart) > 2.6 { sim.pose = .relax; sim.poseStart = now }
        case .idle:
            if now.timeIntervalSince(sim.poseStart) > 5 { sim.pose = .relax }
        case .relax:
            sim.facing += (sim.curYaw - sim.facing) * min(1, dt * 3)
        }
    }

    private func render(_ ctx: inout GraphicsContext, size: CGSize, now: Date) {
        guard size.width > 1, size.height > 1 else { return }
        if !reduced { step(now: now) }
        let layout = self.layout
        let floorRooms = self.floorRooms
        let hour = DateUtil.hourOfDay()
        let night = hour < 6 || hour >= 20
        let dusk = (17...19).contains(hour)
        let light = night ? 0.82 : 1
        let tint: RGB? = night ? c(0xFF4A5BA8) : dusk ? c(0xFFFFB27A) : nil
        let t = reduced ? 0 : now.timeIntervalSince1970.truncatingRemainder(dividingBy: 100)

        // Camera easing (snaps when reduced).
        let wantPitch = cameraMode == .top ? 1.42 : sim.pitch
        let wantZoom = cameraMode == .follow ? sim.zoom * 1.9 : sim.zoom
        let base = layout.base(scene.topFloor)
        let wantTarget = SIMD3(cameraMode == .follow ? sim.ax : 0, base + (cameraMode == .follow ? 0.4 : 0.2), cameraMode == .follow ? sim.az : 0)
        let k = reduced ? 1 : 0.14
        sim.curYaw += (sim.yaw - sim.curYaw) * k
        sim.curPitch += (wantPitch - sim.curPitch) * k
        sim.curZoom += (wantZoom - sim.curZoom) * k
        sim.target += (wantTarget - sim.target) * k

        // Static geometry is cached and rebuilt only when inputs or the cutaway side change.
        let pendingProps = scene.props.filter { scene.pendingIds.contains($0.choreId) }
        var hasher = Hasher()
        hasher.combine(scene.rooms)
        hasher.combine(scene.topFloor)
        hasher.combine(pendingProps)
        hasher.combine(scene.decor)
        hasher.combine(yawKey(sim.curYaw))
        hasher.combine(night)
        hasher.combine(scene.furnish)
        let key = hasher.finalize()
        if key != sim.cacheKey {
            let mb = MeshBuilder(light: light, tint: tint)
            buildHouse(mb, layout, topFloor: scene.topFloor, camYaw: sim.curYaw, night: night, pending: pendingProps, decor: scene.decor, furnish: scene.furnish)
            sim.cache = mb.faces
            sim.cacheKey = key
        }

        let camera = Camera3D(yaw: sim.curYaw, pitch: sim.curPitch, distance: camDistance, target: sim.target, zoom: sim.curZoom)
        let baseScale = min(size.width, size.height * 1.25) / (layout.radius * 2.15 + 1.6)

        // Dynamic: vanishing mess + avatar.
        let dyn = MeshBuilder(light: light, tint: tint)
        dyn.layer = scene.topFloor * 10 + 1
        var bursts: [SIMD3<Double>] = []
        for p in scene.props {
            guard let at = scene.recentClean[p.choreId], !scene.pendingIds.contains(p.choreId) else { continue }
            let age = now.timeIntervalSince(at)
            guard age <= cleanAnim, let room = floorRooms.first(where: { $0.id == p.roomId }) else { continue }
            let s = reduced ? 0 : 1 - age / cleanAnim
            buildMess(dyn, layout, room, p, scale: s * s)
            var center = layout.center(room)
            center.y += 0.3 + (1 - s) * 0.6
            bursts.append(center)
        }
        if let look = scene.look, sim.aFloor == scene.topFloor, !floorRooms.isEmpty {
            dyn.layer = scene.topFloor * 10 + 1
            buildAvatar(dyn, look: look, tier: scene.tier, x: sim.ax, y: base + slab, z: sim.az, facing: sim.facing, pose: sim.pose, t: t)
        }

        Renderer3D.draw(&ctx, size: size, faces: dyn.faces.isEmpty ? sim.cache : sim.cache + dyn.faces, cam: camera, baseScale: baseScale)

        let sparkle = Color(hex: 0xFFE27A)
        func proj(_ p: SIMD3<Double>) -> CGPoint? { Renderer3D.project(camera, size: size, p, baseScale: baseScale) }
        if !reduced {
            for (i, b) in bursts.enumerated() {
                for j in 0..<7 {
                    let a = Double(j) * 0.9 + Double(i)
                    let r = 0.25 + (t * 2 + Double(j)).truncatingRemainder(dividingBy: 1) * 0.5
                    if let pt = proj(SIMD3(b.x + cos(a) * r, b.y + Double(j % 3) * 0.15, b.z + sin(a) * r)) {
                        ctx.fill(star(pt, 7 + CGFloat(j % 3) * 3), with: .color(j % 2 == 0 ? sparkle : .white))
                    }
                }
            }
            for r in floorRooms where (scene.roomCondition[r.id] ?? 0) >= 100 {
                let cc = layout.center(r)
                for j in 0..<3 {
                    let ph = (t * 0.5 + Double(j) * 0.33 + Double(r.x) * 0.1).truncatingRemainder(dividingBy: 1)
                    let sx = cc.x + sin(Double(j) * 2.1 + Double(r.y)) * Double(r.w) * 0.3
                    let sz = cc.z + cos(Double(j) * 1.7 + Double(r.x)) * Double(r.h) * 0.3
                    if let pt = proj(SIMD3(sx, cc.y + 0.2 + ph * 0.9, sz)) {
                        let alpha = sin(ph * .pi)
                        ctx.fill(star(pt, 5 + alpha * 5), with: .color(sparkle.opacity(min(max(alpha, 0), 1))))
                    }
                }
            }
            if scene.look != nil, scene.tier == .radiant, sim.aFloor == scene.topFloor {
                for j in 0..<4 {
                    let a = t * 1.6 + Double(j) * 1.57
                    if let pt = proj(SIMD3(sim.ax + cos(a) * 0.4, base + 0.5 + sin(t * 2 + Double(j)) * 0.25, sim.az + sin(a) * 0.4)) {
                        ctx.fill(star(pt, 6), with: .color(Color(hex: 0xFFB0DA)))
                    }
                }
            }
        }

        if showLabels {
            for r in floorRooms {
                var cc = layout.center(r)
                cc.y += 0.9
                guard let pt = proj(cc) else { continue }
                let pct = scene.roomCondition[r.id] ?? 100
                let label = pct >= 100 ? "\(r.name) ✦ sparkling" : "\(r.name) · \(pct)%"
                let text = ctx.resolve(Text(label).font(.system(size: 11, weight: .bold, design: .rounded)).foregroundStyle(Color(hex: 0x14254A)))
                let ts = text.measure(in: CGSize(width: 240, height: 40))
                let w = ts.width + 20, h = ts.height + 10
                let rect = CGRect(x: pt.x - w / 2, y: pt.y - h / 2, width: w, height: h)
                ctx.fill(Path(roundedRect: rect, cornerRadius: h / 2), with: .color(pct >= 100 ? Color(hex: 0xE2F7E6) : Color.white.opacity(0.95)))
                ctx.draw(text, at: pt)
            }
        }
    }
}

/// Spinning 3D avatar on a pedestal. Drag sideways to turn it.
struct AvatarView: View {
    let look: AvatarLook
    let tier: EnergyTier
    var pose: Pose = .idle
    var spin = true
    var pedestal: RGB = c(0xFFFFE9D2)
    var description = "Your 3D avatar"

    @Environment(\.tt) private var tt
    @State private var yaw = 0.4
    @State private var lastDrag: CGFloat = 0
    @State private var start = Date()

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 60, paused: tt.reducedMotion)) { timeline in
            Canvas { ctx, size in
                let elapsed = timeline.date.timeIntervalSince(start)
                let t = tt.reducedMotion ? 0 : elapsed.truncatingRemainder(dividingBy: 100)
                let spinYaw = yaw + (spin && !tt.reducedMotion ? elapsed * 0.5 : 0)
                let mb = MeshBuilder()
                mb.layer = 0
                mb.box(-0.5, -0.18, -0.5, 0.5, 0, 0.5, RGB(r: pedestal.r * 0.95, g: pedestal.g * 0.9, b: pedestal.b), top: pedestal)
                mb.layer = 1
                buildAvatar(mb, look: look, tier: tier, x: 0, y: 0, z: 0, facing: spinYaw, pose: pose, t: t)
                let cam = Camera3D(yaw: 0, pitch: 0.28, distance: 8, target: SIMD3(0, 0.55, 0), zoom: 1)
                Renderer3D.draw(&ctx, size: size, faces: mb.faces, cam: cam, baseScale: min(size.width, size.height) * 0.62)
            }
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 2)
                .onChanged { v in
                    yaw += (v.translation.width - lastDrag) * 0.012
                    lastDrag = v.translation.width
                }
                .onEnded { _ in lastDrag = 0 }
        )
        .accessibilityElement()
        .accessibilityLabel(description)
        .accessibilityAddTraits(.isImage)
    }
}
