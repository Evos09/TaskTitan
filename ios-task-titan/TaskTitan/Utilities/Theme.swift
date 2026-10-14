import CoreText
import SwiftUI
import UIKit

/// Simple RGBA color used by the 3D renderer and theme (0…1 components).
nonisolated struct RGB: Hashable, Sendable {
    var r: Double, g: Double, b: Double, a: Double = 1

    init(r: Double, g: Double, b: Double, a: Double = 1) {
        self.r = r; self.g = g; self.b = b; self.a = a
    }

    init(_ hex: UInt32, alpha: Double = 1) {
        r = Double((hex >> 16) & 0xFF) / 255
        g = Double((hex >> 8) & 0xFF) / 255
        b = Double(hex & 0xFF) / 255
        a = alpha
    }

    var color: Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }

    func mix(_ o: RGB, _ t: Double) -> RGB {
        RGB(r: r + (o.r - r) * t, g: g + (o.g - g) * t, b: b + (o.b - b) * t, a: a + (o.a - a) * t)
    }

    func alpha(_ v: Double) -> RGB { RGB(r: r, g: g, b: b, a: v) }

    static let white = RGB(0xFFFFFF)
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) { self = RGB(hex, alpha: alpha).color }
}

/// Candy Pop Light tokens (exact values from the design contract).
struct Palette {
    var canvas: Color
    var surface: Color
    var ink: Color
    var muted: Color
    var tangerine: Color
    var tangerineSoft: Color
    var sky: Color
    var skySoft: Color
    var bubblegum: Color
    var bubblegumSoft: Color
    var mint: Color
    var mintSoft: Color
    var sun: Color
    var outline: Color

    static let candy = Palette(
        canvas: Color(hex: 0xFFF8EC), surface: .white, ink: Color(hex: 0x14254A), muted: Color(hex: 0x66708A),
        tangerine: Color(hex: 0xFF8A1F), tangerineSoft: Color(hex: 0xFFE9D2),
        sky: Color(hex: 0x2BB5FF), skySoft: Color(hex: 0xDDF2FF),
        bubblegum: Color(hex: 0xFF6FB5), bubblegumSoft: Color(hex: 0xFFE3F0),
        mint: Color(hex: 0x2EAD5B), mintSoft: Color(hex: 0xE2F7E6),
        sun: Color(hex: 0xFFC21A), outline: Color(hex: 0x14254A, alpha: 0.08)
    )

    static let highContrast: Palette = {
        var p = Palette.candy
        p.canvas = .white
        p.ink = .black
        p.muted = Color(hex: 0x2B2F3A)
        p.tangerine = Color(hex: 0xC24E00)
        p.sky = Color(hex: 0x00609A)
        p.bubblegum = Color(hex: 0xB0005A)
        p.mint = Color(hex: 0x00702E)
        p.outline = Color(hex: 0x000000, alpha: 0.6)
        return p
    }()
}

/// Theme values shared through the environment.
struct Theme {
    var c: Palette = .candy
    var reducedMotion = false
    var largeText = false

    /// Lilita One for titles, big numbers and level badges.
    func display(_ size: CGFloat) -> Font { AppFonts.display(scaled(size)) }

    /// Nunito body text at an exact variable-font weight (400–900).
    func body(_ size: CGFloat, _ weight: CGFloat = 600) -> Font { AppFonts.body(scaled(size), weight: weight) }

    private func scaled(_ size: CGFloat) -> CGFloat {
        UIFontMetrics.default.scaledValue(for: size) * (largeText ? 1.2 : 1)
    }
}

private struct ThemeKey: EnvironmentKey {
    static let defaultValue = Theme()
}

extension EnvironmentValues {
    var tt: Theme {
        get { self[ThemeKey.self] }
        set { self[ThemeKey.self] = newValue }
    }
}

/// Registers the bundled fonts once and builds weight-correct variable-font instances.
enum AppFonts {
    private static let displayName = "LilitaOne"
    private static let bodyName = "Nunito-ExtraLight"
    private static let wghtAxis = 0x7767_6874 // 'wght'

    static func register() {
        let urls = Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? []
        for url in urls {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }

    static func display(_ size: CGFloat) -> Font {
        if UIFont(name: displayName, size: size) != nil { return .custom(displayName, fixedSize: size) }
        return .system(size: size, weight: .black, design: .rounded)
    }

    static func body(_ size: CGFloat, weight: CGFloat) -> Font {
        let attrs: [UIFontDescriptor.AttributeName: Any] = [
            .name: bodyName,
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [wghtAxis: weight],
        ]
        let descriptor = UIFontDescriptor(fontAttributes: attrs)
        let font = UIFont(descriptor: descriptor, size: size)
        if font.fontName.hasPrefix("Nunito") { return Font(font as CTFont) }
        return .system(size: size, weight: weight >= 800 ? .heavy : weight >= 700 ? .bold : .semibold, design: .rounded)
    }
}
