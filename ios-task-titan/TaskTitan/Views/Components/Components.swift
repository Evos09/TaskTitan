import SwiftUI

extension RoomType {
    var symbol: String {
        switch self {
        case .kitchen: "refrigerator.fill"
        case .living: "sofa.fill"
        case .dining: "fork.knife"
        case .bedroom: "bed.double.fill"
        case .bathroom: "bathtub.fill"
        case .laundry: "washer.fill"
        case .toy: "teddybear.fill"
        case .office: "desktopcomputer"
        case .garage: "car.fill"
        case .custom: "door.left.hand.open"
        }
    }

    var tint: Color { RGB(Catalog.roomColors[self] ?? 0xF1EDE6).color }
    var accent: Color { RGB(Catalog.roomAccents[self] ?? 0x8A6D4C).color }
}

extension Member {
    var color: Color { RGB(Catalog.memberColors[colorIndex % Catalog.memberColors.count]).color }
}

/// Big soft rounded card with no hard border.
struct SoftCard<Content: View>: View {
    var color: Color?
    var padding: CGFloat = 16
    @ViewBuilder var content: Content
    @Environment(\.tt) private var tt

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(color ?? tt.c.surface, in: .rect(cornerRadius: 28))
    }
}

/// Rounded square icon tile used throughout the app.
struct IconTile: View {
    let symbol: String
    let tint: Color
    let background: Color
    var size: CGFloat = 44

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.44, weight: .bold))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(background, in: .rect(cornerRadius: size * 0.32))
            .accessibilityHidden(true)
    }
}

/// Effort weight shown as filled dots plus a text label (never color-only).
struct WeightDots: View {
    let weight: Int
    var showLabel = true
    @Environment(\.tt) private var tt

    var body: some View {
        let color: Color = switch weight {
        case 1: tt.c.sky
        case 2: tt.c.mint
        case 3: tt.c.tangerine
        default: tt.c.bubblegum
        }
        HStack(spacing: 3) {
            if showLabel {
                Text(GameRules.weightLabel(weight)).font(tt.body(12, 700)).foregroundStyle(tt.c.muted)
                    .padding(.trailing, 3)
            }
            ForEach(0..<min(weight, 5), id: \.self) { _ in
                Circle().fill(color).frame(width: 9, height: 9)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Effort \(GameRules.weightLabel(weight)), \(weight) of 5")
    }
}

struct MemberBadge: View {
    let member: Member
    var size: CGFloat = 40
    @Environment(\.tt) private var tt

    var body: some View {
        let color = member.color
        Text(String(member.name.prefix(1)).uppercased())
            .font(tt.display(size * 0.42))
            .foregroundStyle(color.mix(with: .black, by: 0.25))
            .frame(width: size - 5, height: size - 5)
            .background {
                Circle()
                    .fill(LinearGradient(colors: [color.opacity(0.25), color.opacity(0.45)], startPoint: .top, endPoint: .bottom))
                    .overlay(Circle().fill(Color.white.opacity(0.55)))
            }
            .padding(2.5)
            .background(color, in: .circle)
            .accessibilityLabel(member.name)
    }
}

/// Animated rounded progress bar.
struct FatProgress: View {
    let progress: Double
    let color: Color
    let track: Color
    var height: CGFloat = 12
    @Environment(\.tt) private var tt

    var body: some View {
        Capsule()
            .fill(track)
            .frame(height: height)
            .overlay(alignment: .leading) {
                GeometryReader { geo in
                    Capsule()
                        .fill(LinearGradient(colors: [color.opacity(0.85), color], startPoint: .leading, endPoint: .trailing))
                        .frame(width: max(0, geo.size.width * min(max(progress, 0), 1)))
                }
            }
            .clipShape(Capsule())
            .animation(tt.reducedMotion ? nil : .spring(response: 0.6, dampingFraction: 0.7), value: progress)
            .accessibilityElement()
            .accessibilityValue("\(Int(progress * 100)) percent")
    }
}

/// Compact game HUD: level, energy tier, XP, coins.
struct HudStrip: View {
    let stats: Stats
    @Environment(\.tt) private var tt

    var body: some View {
        HStack(spacing: 8) {
            pill("Level \(stats.level.level)") {
                Image(systemName: "star.fill").font(.system(size: 20)).foregroundStyle(tt.c.sun)
                Text("Lv \(stats.level.level)").font(tt.display(17)).foregroundStyle(tt.c.ink).monospacedDigit()
            }
            .layoutPriority(0.8)
            pill("Energy \(stats.tier.label)") {
                Image(systemName: "bolt.fill").font(.system(size: 18)).foregroundStyle(tt.c.mint)
                VStack(alignment: .leading, spacing: 0) {
                    Text("Energy").font(tt.body(10, 700)).foregroundStyle(tt.c.muted)
                    Text(stats.tier.label).font(tt.body(13, 800)).foregroundStyle(tt.c.mint).lineLimit(1).minimumScaleFactor(0.7)
                }
            }
            pill("XP \(stats.level.xpInto) of \(stats.level.xpNeeded)") {
                Text("XP").font(.system(size: 9, weight: .black)).foregroundStyle(.white)
                    .frame(width: 22, height: 22).background(tt.c.sky, in: .circle)
                VStack(alignment: .leading, spacing: 3) {
                    Text("\(stats.level.xpInto)/\(stats.level.xpNeeded)").font(tt.body(10, 700)).foregroundStyle(tt.c.ink).lineLimit(1).monospacedDigit().minimumScaleFactor(0.7)
                    FatProgress(progress: Double(stats.level.xpInto) / Double(max(stats.level.xpNeeded, 1)), color: tt.c.sky, track: tt.c.skySoft, height: 6)
                }
            }
            pill("\(stats.coins) coins") {
                Image(systemName: "dollarsign.circle.fill").font(.system(size: 20)).foregroundStyle(tt.c.sun)
                VStack(alignment: .leading, spacing: 0) {
                    Text(stats.coins.formatted()).font(tt.display(14)).foregroundStyle(tt.c.ink).lineLimit(1).monospacedDigit().minimumScaleFactor(0.7)
                    Text("coins").font(tt.body(10, 700)).foregroundStyle(tt.c.muted)
                }
            }
        }
    }

    private func pill<C: View>(_ label: String, @ViewBuilder _ content: () -> C) -> some View {
        HStack(spacing: 4) { content() }
            .padding(.horizontal, 7)
            .padding(.vertical, 9)
            .frame(maxWidth: .infinity, minHeight: 50)
            .background(tt.c.surface, in: .rect(cornerRadius: 18))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(label)
    }
}

struct BrandMark: View {
    var size: CGFloat = 40
    @Environment(\.tt) private var tt

    var body: some View {
        Image(systemName: "heart.fill")
            .font(.system(size: size * 0.48, weight: .bold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(tt.c.tangerine, in: .rect(cornerRadius: size * 0.3))
            .accessibilityHidden(true)
    }
}

struct SectionTitle<Trailing: View>: View {
    let title: String
    @ViewBuilder var trailing: Trailing
    @Environment(\.tt) private var tt

    var body: some View {
        HStack {
            Text(title).font(tt.display(22)).foregroundStyle(tt.c.ink).accessibilityAddTraits(.isHeader)
            Spacer()
            trailing
        }
    }
}

extension SectionTitle where Trailing == EmptyView {
    init(_ title: String) {
        self.title = title
        self.trailing = EmptyView()
    }
}

/// Chunky pill button with a squishy press and a light haptic.
struct CandyButtonStyle: ButtonStyle {
    var fill: Color
    var foreground: Color = .white
    var height: CGFloat = 56
    @Environment(\.tt) private var tt
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(tt.body(16, 800))
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity, minHeight: height)
            .background(fill.opacity(isEnabled ? 1 : 0.4), in: .rect(cornerRadius: 24))
            .scaleEffect(configuration.isPressed && !tt.reducedMotion ? 0.96 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, new in new }
    }
}

/// Small tappable chip used for filters, room pickers and camera modes.
struct Chip: View {
    let label: String
    var symbol: String?
    let selected: Bool
    var tint: Color?
    let action: () -> Void
    @Environment(\.tt) private var tt

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                if let symbol { Image(systemName: symbol).font(.system(size: 12, weight: .bold)) }
                Text(label).font(tt.body(13, 800)).lineLimit(1)
            }
            .padding(.horizontal, 13)
            .frame(minHeight: 36)
            .foregroundStyle(selected ? Color.white : tt.c.ink)
            .background(selected ? (tint ?? tt.c.tangerine) : tt.c.surface, in: .capsule)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
