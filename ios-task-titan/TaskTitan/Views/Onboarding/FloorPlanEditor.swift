import SwiftUI

private enum DragMode { case none, move, tl, tr, bl, br }

/// Drag-and-drop floor plan grid. Drag a room to move it (snaps to grid); drag a
/// corner handle of the selected room to resize. Overlaps are rejected.
struct FloorPlanEditor: View {
    let rooms: [Room]
    let floor: Int
    let selectedId: String?
    let onSelect: (String?) -> Void
    let onChange: (Room) -> Void

    @Environment(\.tt) private var tt
    @State private var mode: DragMode = .none
    @State private var start: Room?
    @State private var preview: Room?
    @State private var previewValid = true

    var body: some View {
        GeometryReader { geo in
            let cell = geo.size.width / CGFloat(GameRules.gridW)
            Canvas { ctx, size in
                draw(&ctx, size: size, cell: cell)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 6)
                    .onChanged { v in drag(v, cell: cell) }
                    .onEnded { _ in
                        if let p = preview, previewValid { onChange(p) }
                        preview = nil; start = nil; mode = .none
                    }
            )
            .simultaneousGesture(
                SpatialTapGesture().onEnded { v in onSelect(hit(v.location, cell: cell)?.id) }
            )
        }
        .aspectRatio(CGFloat(GameRules.gridW) / CGFloat(GameRules.gridH), contentMode: .fit)
        .sensoryFeedback(.selection, trigger: preview.map { "\($0.x),\($0.y),\($0.w),\($0.h)" })
        .accessibilityElement()
        .accessibilityLabel("Floor plan editor. Drag rooms to arrange, drag corners to resize.")
        .accessibilityValue(rooms.filter { $0.floor == floor }.map(\.name).joined(separator: ", "))
    }

    private func hit(_ p: CGPoint, cell: CGFloat) -> Room? {
        rooms.last {
            $0.floor == floor && p.x >= CGFloat($0.x) * cell && p.x <= CGFloat($0.x + $0.w) * cell &&
                p.y >= CGFloat($0.y) * cell && p.y <= CGFloat($0.y + $0.h) * cell
        }
    }

    private func drag(_ v: DragGesture.Value, cell: CGFloat) {
        if start == nil {
            let pos = v.startLocation
            mode = .none
            if let sel = rooms.first(where: { $0.id == selectedId && $0.floor == floor }) {
                let r = cell * 0.7
                let corners: [(DragMode, CGPoint)] = [
                    (.tl, CGPoint(x: CGFloat(sel.x) * cell, y: CGFloat(sel.y) * cell)),
                    (.tr, CGPoint(x: CGFloat(sel.x + sel.w) * cell, y: CGFloat(sel.y) * cell)),
                    (.bl, CGPoint(x: CGFloat(sel.x) * cell, y: CGFloat(sel.y + sel.h) * cell)),
                    (.br, CGPoint(x: CGFloat(sel.x + sel.w) * cell, y: CGFloat(sel.y + sel.h) * cell)),
                ]
                if let hitCorner = corners.first(where: { abs($0.1.x - pos.x) < r && abs($0.1.y - pos.y) < r }) {
                    mode = hitCorner.0
                    start = sel
                }
            }
            if mode == .none, let h = hit(pos, cell: cell) {
                mode = .move
                start = h
                onSelect(h.id)
            }
        }
        guard let s = start, mode != .none else { return }
        let dx = Int((v.translation.width / cell).rounded())
        let dy = Int((v.translation.height / cell).rounded())
        let gw = GameRules.gridW, gh = GameRules.gridH
        var x0 = s.x, y0 = s.y, x1 = s.x + s.w, y1 = s.y + s.h
        func clamp(_ v: Int, _ lo: Int, _ hi: Int) -> Int { min(max(v, lo), hi) }
        switch mode {
        case .move:
            let nx = clamp(s.x + dx, 0, gw - s.w), ny = clamp(s.y + dy, 0, gh - s.h)
            x0 = nx; y0 = ny; x1 = nx + s.w; y1 = ny + s.h
        case .tl: x0 = clamp(s.x + dx, 0, x1 - 2); y0 = clamp(s.y + dy, 0, y1 - 2)
        case .tr: x1 = clamp(s.x + s.w + dx, x0 + 2, gw); y0 = clamp(s.y + dy, 0, y1 - 2)
        case .bl: x0 = clamp(s.x + dx, 0, x1 - 2); y1 = clamp(s.y + s.h + dy, y0 + 2, gh)
        case .br: x1 = clamp(s.x + s.w + dx, x0 + 2, gw); y1 = clamp(s.y + s.h + dy, y0 + 2, gh)
        case .none: break
        }
        var candidate = s
        candidate.x = x0; candidate.y = y0; candidate.w = x1 - x0; candidate.h = y1 - y0
        if candidate != preview {
            preview = candidate
            previewValid = !GameRules.overlaps(rooms, candidate: candidate)
        }
    }

    private func draw(_ ctx: inout GraphicsContext, size: CGSize, cell: CGFloat) {
        let grid = Color(hex: 0x14254A, alpha: 0.08)
        for gx in 0...GameRules.gridW {
            var p = Path(); p.move(to: CGPoint(x: CGFloat(gx) * cell, y: 0)); p.addLine(to: CGPoint(x: CGFloat(gx) * cell, y: size.height))
            ctx.stroke(p, with: .color(grid), lineWidth: 1)
        }
        for gy in 0...GameRules.gridH {
            var p = Path(); p.move(to: CGPoint(x: 0, y: CGFloat(gy) * cell)); p.addLine(to: CGPoint(x: size.width, y: CGFloat(gy) * cell))
            ctx.stroke(p, with: .color(grid), lineWidth: 1)
        }
        let floorRooms = rooms.filter { $0.floor == floor }.map { preview?.id == $0.id ? (preview ?? $0) : $0 }
        let gap = cell * 0.06
        let orange = Color(hex: 0xFF8A1F)
        for r in floorRooms {
            let selected = r.id == selectedId
            let rect = CGRect(x: CGFloat(r.x) * cell + gap, y: CGFloat(r.y) * cell + gap, width: CGFloat(r.w) * cell - gap * 2, height: CGFloat(r.h) * cell - gap * 2)
            let invalid = preview?.id == r.id && !previewValid
            let shape = Path(roundedRect: rect, cornerRadius: cell * 0.35)
            ctx.fill(shape, with: .color(invalid ? Color(hex: 0xFFD4D4) : r.type.tint))
            ctx.stroke(shape, with: .color(selected ? orange : r.type.accent.opacity(0.25)), lineWidth: selected ? 3 : 1.5)
            let text = ctx.resolve(Text(r.name).font(tt.body(12, 800)).foregroundStyle(r.type.accent))
            ctx.draw(text, in: rect.insetBy(dx: 4, dy: max(0, rect.height / 2 - 16)))
            if selected {
                for corner in [CGPoint(x: rect.minX, y: rect.minY), CGPoint(x: rect.maxX, y: rect.minY), CGPoint(x: rect.minX, y: rect.maxY), CGPoint(x: rect.maxX, y: rect.maxY)] {
                    let dot = Path(ellipseIn: CGRect(x: corner.x - cell * 0.24, y: corner.y - cell * 0.24, width: cell * 0.48, height: cell * 0.48))
                    ctx.fill(dot, with: .color(.white))
                    ctx.stroke(dot, with: .color(orange), lineWidth: 2.5)
                }
            }
        }
    }
}
