import SwiftUI

/// A flat-shaded polygon in world space (y is up). `layer` orders big groups
/// (ground < floor slabs < objects) before depth sorting.
nonisolated struct Face {
    var pts: [SIMD3<Double>]
    var normal: SIMD3<Double>
    var color: RGB
    var layer: Int
    var bias: Double = 0
    var twoSided = false
}

private nonisolated let lightDir: SIMD3<Double> = {
    let v = SIMD3<Double>(-0.45, 1, 0.65)
    return v / (v * v).sum().squareRoot()
}()

/// Shade a base color with the fixed world light plus an optional day/night tint.
nonisolated func shade(_ c: RGB, _ n: SIMD3<Double>, light: Double, tint: RGB?) -> RGB {
    let d = max(0, (n * lightDir).sum())
    let k = (0.64 + 0.36 * d) * light
    var r = c.r * k, g = c.g * k, b = c.b * k
    if let t = tint {
        r = r * 0.78 + t.r * 0.22 * k
        g = g * 0.78 + t.g * 0.22 * k
        b = b * 0.78 + t.b * 0.22 * k
    }
    return RGB(r: min(max(r, 0), 1), g: min(max(g, 0), 1), b: min(max(b, 0), 1), a: c.a)
}

/// Collects faces. Supports a yaw + translate + scale transform for posable objects.
nonisolated final class MeshBuilder {
    private(set) var faces: [Face] = []
    var layer = 1
    private let light: Double
    private let tint: RGB?
    private var o = SIMD3<Double>(0, 0, 0)
    private var cy = 1.0, sy = 0.0, scale = 1.0

    init(light: Double = 1, tint: RGB? = nil) {
        self.light = light
        self.tint = tint
        faces.reserveCapacity(512)
    }

    func transform(_ x: Double, _ y: Double, _ z: Double, yaw: Double, scale s: Double = 1) {
        o = SIMD3(x, y, z); cy = cos(yaw); sy = sin(yaw); scale = s
    }

    func resetTransform() { transform(0, 0, 0, yaw: 0) }

    private func world(_ p: SIMD3<Double>) -> SIMD3<Double> {
        SIMD3(o.x + (p.x * cy + p.z * sy) * scale, o.y + p.y * scale, o.z + (-p.x * sy + p.z * cy) * scale)
    }

    private func add(_ p: [SIMD3<Double>], _ n: SIMD3<Double>, _ color: RGB, bias: Double, twoSided: Bool, unlit: Bool) {
        let wn = SIMD3(n.x * cy + n.z * sy, n.y, -n.x * sy + n.z * cy)
        let col = unlit ? color : shade(color, wn, light: light, tint: tint)
        faces.append(Face(pts: p.map(world), normal: wn, color: col, layer: layer, bias: bias, twoSided: twoSided))
    }

    /// Axis-aligned (local space) box; bottom face omitted.
    func box(_ x0: Double, _ y0: Double, _ z0: Double, _ x1: Double, _ y1: Double, _ z1: Double,
             _ color: RGB, top: RGB? = nil, bias: Double = 0, unlit: Bool = false) {
        add([SIMD3(x0, y1, z0), SIMD3(x1, y1, z0), SIMD3(x1, y1, z1), SIMD3(x0, y1, z1)], SIMD3(0, 1, 0), top ?? color, bias: bias, twoSided: false, unlit: unlit)
        add([SIMD3(x0, y0, z1), SIMD3(x1, y0, z1), SIMD3(x1, y1, z1), SIMD3(x0, y1, z1)], SIMD3(0, 0, 1), color, bias: bias, twoSided: false, unlit: unlit)
        add([SIMD3(x1, y0, z0), SIMD3(x0, y0, z0), SIMD3(x0, y1, z0), SIMD3(x1, y1, z0)], SIMD3(0, 0, -1), color, bias: bias, twoSided: false, unlit: unlit)
        add([SIMD3(x1, y0, z1), SIMD3(x1, y0, z0), SIMD3(x1, y1, z0), SIMD3(x1, y1, z1)], SIMD3(1, 0, 0), color, bias: bias, twoSided: false, unlit: unlit)
        add([SIMD3(x0, y0, z0), SIMD3(x0, y0, z1), SIMD3(x0, y1, z1), SIMD3(x0, y1, z0)], SIMD3(-1, 0, 0), color, bias: bias, twoSided: false, unlit: unlit)
    }

    /// Box centered on (cx, cz) with its base at y.
    func block(_ cx: Double, _ y: Double, _ cz: Double, _ sx: Double, _ syz: Double, _ sz: Double,
               _ color: RGB, top: RGB? = nil, bias: Double = 0) {
        box(cx - sx / 2, y, cz - sz / 2, cx + sx / 2, y + syz, cz + sz / 2, color, top: top, bias: bias)
    }

    /// Upward-facing flat rectangle (rugs, floors, puddles).
    func flat(_ x0: Double, _ z0: Double, _ x1: Double, _ z1: Double, y: Double, _ color: RGB, bias: Double = 0) {
        add([SIMD3(x0, y, z0), SIMD3(x1, y, z0), SIMD3(x1, y, z1), SIMD3(x0, y, z1)], SIMD3(0, 1, 0), color, bias: bias, twoSided: true, unlit: false)
    }

    /// Flat regular polygon (round rugs, aura rings).
    func disc(_ cx: Double, _ y: Double, _ cz: Double, r: Double, segments: Int, _ color: RGB, unlit: Bool = false, bias: Double = 0) {
        let pts = (0..<segments).map { i -> SIMD3<Double> in
            let a = Double(i) / Double(segments) * 2 * .pi
            return SIMD3(cx + cos(a) * r, y, cz + sin(a) * r)
        }
        add(pts, SIMD3(0, 1, 0), color, bias: bias, twoSided: true, unlit: unlit)
    }

    /// Vertical decal on a face pointing +z (eyes, doors, windows).
    func decalZ(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, z: Double, _ color: RGB, bias: Double = -0.02, unlit: Bool = true) {
        add([SIMD3(x0, y0, z), SIMD3(x1, y0, z), SIMD3(x1, y1, z), SIMD3(x0, y1, z)], SIMD3(0, 0, 1), color, bias: bias, twoSided: false, unlit: unlit)
    }

    func decalNegZ(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, z: Double, _ color: RGB, bias: Double = -0.02, unlit: Bool = true) {
        add([SIMD3(x1, y0, z), SIMD3(x0, y0, z), SIMD3(x0, y1, z), SIMD3(x1, y1, z)], SIMD3(0, 0, -1), color, bias: bias, twoSided: false, unlit: unlit)
    }

    func decalX(_ z0: Double, _ y0: Double, _ z1: Double, _ y1: Double, x: Double, _ color: RGB, bias: Double = -0.02, unlit: Bool = true) {
        add([SIMD3(x, y0, z1), SIMD3(x, y0, z0), SIMD3(x, y1, z0), SIMD3(x, y1, z1)], SIMD3(1, 0, 0), color, bias: bias, twoSided: false, unlit: unlit)
    }

    func decalNegX(_ z0: Double, _ y0: Double, _ z1: Double, _ y1: Double, x: Double, _ color: RGB, bias: Double = -0.02, unlit: Bool = true) {
        add([SIMD3(x, y0, z0), SIMD3(x, y0, z1), SIMD3(x, y1, z1), SIMD3(x, y1, z0)], SIMD3(-1, 0, 0), color, bias: bias, twoSided: false, unlit: unlit)
    }
}

/// Orbit camera around a target point.
nonisolated struct Camera3D: Equatable {
    var yaw: Double
    var pitch: Double
    var distance: Double
    var target: SIMD3<Double>
    var zoom: Double
}

/// Projects and paints faces back-to-front (painter's algorithm) into a SwiftUI Canvas.
nonisolated enum Renderer3D {
    /// Project a world point to screen space; nil when behind the camera.
    static func project(_ cam: Camera3D, size: CGSize, _ p: SIMD3<Double>, baseScale: Double) -> CGPoint? {
        let cyw = cos(cam.yaw), syw = sin(cam.yaw), cp = cos(cam.pitch), sp = sin(cam.pitch)
        let q = p - cam.target
        let xr = q.x * cyw - q.z * syw
        let zr = q.x * syw + q.z * cyw
        let y2 = q.y * cp - zr * sp
        let z2 = q.y * sp + zr * cp
        let d = cam.distance - z2
        guard d >= 0.2 else { return nil }
        let s = baseScale * cam.zoom * cam.distance / d
        return CGPoint(x: size.width / 2 + xr * s, y: size.height * 0.54 - y2 * s)
    }

    static func draw(_ ctx: inout GraphicsContext, size: CGSize, faces: [Face], cam: Camera3D, baseScale: Double) {
        let cyw = cos(cam.yaw), syw = sin(cam.yaw), cp = cos(cam.pitch), sp = sin(cam.pitch)
        let camPos = cam.target + SIMD3(cam.distance * cp * syw, cam.distance * sp, cam.distance * cp * cyw)
        let scale = baseScale * cam.zoom
        let w2 = size.width / 2, hy = size.height * 0.54

        var items: [(index: Int, depth: Double, path: Path)] = []
        items.reserveCapacity(faces.count)
        for (i, f) in faces.enumerated() {
            guard let first = f.pts.first else { continue }
            if !f.twoSided && (f.normal * (camPos - first)).sum() <= 0 { continue }
            var path = Path()
            var dsum = 0.0
            var ok = true
            for (k, p) in f.pts.enumerated() {
                let q = p - cam.target
                let xr = q.x * cyw - q.z * syw
                let zr = q.x * syw + q.z * cyw
                let y2 = q.y * cp - zr * sp
                let z2 = q.y * sp + zr * cp
                let d = cam.distance - z2
                if d < 0.2 { ok = false; break }
                let s = scale * cam.distance / d
                let pt = CGPoint(x: w2 + xr * s, y: hy - y2 * s)
                if k == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
                dsum += d
            }
            guard ok else { continue }
            path.closeSubpath()
            items.append((i, dsum / Double(f.pts.count) + f.bias, path))
        }
        items.sort { a, b in
            let la = faces[a.index].layer, lb = faces[b.index].layer
            return la != lb ? la < lb : a.depth > b.depth
        }
        for item in items {
            ctx.fill(item.path, with: .color(faces[item.index].color.color))
        }
    }
}
