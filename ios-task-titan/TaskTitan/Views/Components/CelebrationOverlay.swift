import SwiftUI

private struct ConfettiPiece {
    let x: Double, delay: Double, speed: Double, size: Double, color: Color, spin: Double
}

/// Milestone celebration with confetti. Confetti is skipped when reduced motion is on.
/// Tap anywhere (or "Yay!") to dismiss.
struct CelebrationOverlay: View {
    let celebration: Celebration
    let onDismiss: () -> Void

    @Environment(\.tt) private var tt
    @State private var popped = false
    @State private var start = Date()
    @State private var pieces: [ConfettiPiece] = []

    var body: some View {
        ZStack {
            Color(hex: 0x14254A, alpha: 0.53)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
                .accessibilityHidden(true)

            if !tt.reducedMotion {
                TimelineView(.animation) { tl in
                    Canvas { ctx, size in
                        let t = min(tl.date.timeIntervalSince(start) / 2.6, 1)
                        for p in pieces {
                            let local = min(max((t - p.delay) / (1 - p.delay), 0), 1)
                            guard local > 0 else { continue }
                            let y = -40 + local * p.speed * size.height * 1.2
                            let x = p.x * size.width + sin(local * 12 + p.spin) * 24
                            var c = ctx
                            c.translateBy(x: x, y: y)
                            c.rotate(by: .degrees(p.spin * local))
                            c.fill(Path(CGRect(x: -p.size / 2, y: -p.size / 4, width: p.size, height: p.size / 2)), with: .color(p.color))
                        }
                    }
                }
                .ignoresSafeArea()
                .allowsHitTesting(false)
            }

            VStack(spacing: 0) {
                Image(systemName: celebration.big ? "trophy.fill" : "sparkles")
                    .font(.system(size: 42, weight: .bold))
                    .foregroundStyle(celebration.big ? tt.c.tangerine : tt.c.mint)
                    .frame(width: 84, height: 84)
                    .background(celebration.big ? tt.c.tangerineSoft : tt.c.mintSoft, in: .circle)
                    .symbolEffect(.bounce, value: popped)
                Text(celebration.title)
                    .font(tt.display(26)).foregroundStyle(tt.c.ink)
                    .multilineTextAlignment(.center)
                    .padding(.top, 14)
                Text(celebration.message)
                    .font(tt.body(16)).foregroundStyle(tt.c.muted)
                    .multilineTextAlignment(.center)
                    .padding(.top, 6)
                Button("Yay!", action: onDismiss)
                    .buttonStyle(CandyButtonStyle(fill: tt.c.tangerine, height: 52))
                    .padding(.top, 18)
            }
            .padding(26)
            .frame(maxWidth: 380)
            .background(tt.c.surface, in: .rect(cornerRadius: 32))
            .padding(32)
            .scaleEffect(popped || tt.reducedMotion ? 1 : 0.6)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
        }
        .sensoryFeedback(.success, trigger: celebration.id)
        .onAppear {
            start = Date()
            let colors = [tt.c.tangerine, tt.c.sky, tt.c.bubblegum, tt.c.sun, tt.c.mint]
            pieces = (0..<(celebration.big ? 70 : 36)).map { _ in
                ConfettiPiece(x: .random(in: 0...1), delay: .random(in: 0...0.35), speed: .random(in: 0.6...1.4),
                              size: .random(in: 8...18), color: colors.randomElement() ?? .orange, spin: .random(in: 0...720))
            }
            withAnimation(.spring(response: 0.45, dampingFraction: 0.5)) { popped = true }
            UIAccessibility.post(notification: .announcement, argument: "\(celebration.title). \(celebration.message)")
        }
    }
}
