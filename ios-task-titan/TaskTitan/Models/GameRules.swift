import Foundation

/// Positive-only energy tiers. The avatar starts each day at Neutral and only climbs.
nonisolated enum EnergyTier: Int, CaseIterable, Sendable {
    case neutral, energized, thriving, radiant

    var label: String {
        switch self {
        case .neutral: "Neutral"
        case .energized: "Energized"
        case .thriving: "Thriving"
        case .radiant: "Radiant"
        }
    }

    var min: Int {
        switch self {
        case .neutral: 0
        case .energized: 1
        case .thriving: 4
        case .radiant: 8
        }
    }
}

/// How the avatar feels today, set by whether the chores that are yours today are done.
nonisolated enum Health: Sendable {
    case healthy, queasy, sick

    var label: String {
        switch self {
        case .healthy: "Healthy"
        case .queasy: "Feeling sick"
        case .sick: "Sick"
        }
    }
}

nonisolated struct Reward: Hashable, Sendable {
    let xp: Int
    let coins: Int
    let energy: Int
}

nonisolated struct LevelInfo: Hashable, Sendable {
    let level: Int
    let xpInto: Int
    let xpNeeded: Int
}

nonisolated enum GameRules {
    static let maxEnergy = 10
    static let maxFloors = 3
    static let gridW = 12
    static let gridH = 9

    static func newId() -> String { String(UUID().uuidString.lowercased().prefix(12)) }

    static func weightLabel(_ weight: Int) -> String {
        switch weight {
        case 1: "Light"
        case 2: "Normal"
        case 3: "Heavy"
        default: "Big · \(weight)"
        }
    }

    /// XP, coins, and energy scale linearly with effort weight (1–5).
    static func reward(_ weight: Int) -> Reward {
        let w = Swift.min(Swift.max(weight, 1), 5)
        return Reward(xp: 10 + 10 * w, coins: 4 * w, energy: w)
    }

    static func xpForLevel(_ level: Int) -> Int { 100 + (level - 1) * 60 }

    static func levelInfo(_ totalXp: Int) -> LevelInfo {
        var level = 1
        var remaining = totalXp
        while remaining >= xpForLevel(level) {
            remaining -= xpForLevel(level)
            level += 1
        }
        return LevelInfo(level: level, xpInto: remaining, xpNeeded: xpForLevel(level))
    }

    static func tier(_ energy: Int) -> EnergyTier {
        EnergyTier.allCases.last { energy >= $0.min } ?? .neutral
    }

    /// Healthy with every chore of yours done (or none due), sick when fewer than half are done.
    static func health(due: Int, done: Int) -> Health {
        if due <= 0 || done >= due { return .healthy }
        return done * 2 >= due ? .queasy : .sick
    }

    static func isDue(_ chore: Chore, day: Int) -> Bool {
        guard day >= chore.startEpochDay else { return false }
        let diff = day - chore.startEpochDay
        switch chore.frequency {
        case .daily: return true
        case .weekly: return diff % 7 == 0
        case .monthly:
            return DateUtil.dayOfMonth(day) == Swift.min(DateUtil.dayOfMonth(chore.startEpochDay), DateUtil.lengthOfMonth(day))
        case .custom: return diff % Swift.max(chore.intervalDays, 1) == 0
        }
    }

    static func streak(_ completions: [Completion], memberId: String, today: Int) -> Int {
        let days = Set(completions.filter { $0.memberId == memberId && $0.confirmed }.map(\.epochDay))
        var cursor = days.contains(today) ? today : today - 1
        var count = 0
        while days.contains(cursor) {
            count += 1
            cursor -= 1
        }
        return count
    }

    /// Consecutive weeks (ending at today's week) where the household met its shared goal.
    /// The current week counts once met and never breaks the streak while still open.
    static func weeklyStreak(_ completions: [Completion], goal: Int, today: Int) -> Int {
        let days = completions.filter(\.confirmed).map(\.epochDay)
        func met(_ weekStart: Int) -> Bool { days.filter { $0 >= weekStart && $0 <= weekStart + 6 }.count >= goal }
        var week = DateUtil.weekStart(today)
        var count = 0
        if met(week) { count += 1 }
        week -= 7
        while met(week) {
            count += 1
            week -= 7
        }
        return count
    }

    static func conditionLabel(_ percent: Int) -> String {
        if percent >= 85 { return "Sparkling" }
        if percent >= 60 { return "Tidy" }
        if percent >= 25 { return "Cozy" }
        return "Fresh start"
    }

    static func nextFreeSpot(_ rooms: [Room], floor: Int, w: Int, h: Int) -> (x: Int, y: Int)? {
        guard h <= gridH, w <= gridW else { return nil }
        for y in 0...(gridH - h) {
            for x in 0...(gridW - w) {
                let hit = rooms.contains { $0.floor == floor && x < $0.x + $0.w && x + w > $0.x && y < $0.y + $0.h && y + h > $0.y }
                if !hit { return (x, y) }
            }
        }
        return nil
    }

    static func overlaps(_ rooms: [Room], candidate c: Room) -> Bool {
        rooms.contains {
            $0.id != c.id && $0.floor == c.floor &&
                c.x < $0.x + $0.w && c.x + c.w > $0.x && c.y < $0.y + $0.h && c.y + c.h > $0.y
        }
    }

    /// Starter floor plans for the 30-second setup.
    static func layout(for size: HomeSize) -> [Room] {
        func r(_ type: RoomType, _ floor: Int, _ x: Int, _ y: Int, _ w: Int, _ h: Int, _ name: String? = nil) -> Room {
            Room(id: newId(), name: name ?? type.label, type: type, floor: floor, x: x, y: y, w: w, h: h)
        }
        switch size {
        case .studio:
            return [r(.living, 0, 0, 0, 6, 5, "Studio"), r(.kitchen, 0, 6, 0, 4, 3), r(.bathroom, 0, 6, 3, 4, 2)]
        case .one:
            return [r(.kitchen, 0, 0, 0, 4, 4), r(.living, 0, 4, 0, 5, 4), r(.bedroom, 0, 0, 4, 5, 4), r(.bathroom, 0, 5, 4, 4, 3)]
        case .two:
            return [
                r(.kitchen, 0, 0, 0, 4, 4), r(.living, 0, 4, 0, 5, 4), r(.toy, 0, 9, 0, 3, 4),
                r(.bedroom, 0, 0, 4, 4, 4), r(.bedroom, 0, 4, 4, 4, 4, "Kids room"), r(.bathroom, 0, 8, 4, 4, 3),
            ]
        case .three:
            return [
                r(.kitchen, 0, 0, 0, 4, 4), r(.living, 0, 4, 0, 5, 4), r(.dining, 0, 9, 0, 3, 4),
                r(.laundry, 0, 0, 4, 3, 3), r(.bathroom, 0, 3, 4, 3, 3),
                r(.bedroom, 1, 0, 0, 4, 4), r(.bedroom, 1, 4, 0, 4, 4, "Bedroom 2"), r(.bedroom, 1, 8, 0, 4, 4, "Bedroom 3"),
                r(.bathroom, 1, 0, 4, 4, 3),
            ]
        case .four:
            return [
                r(.kitchen, 0, 0, 0, 4, 4), r(.living, 0, 4, 0, 5, 4), r(.dining, 0, 9, 0, 3, 4),
                r(.laundry, 0, 0, 4, 3, 3), r(.garage, 0, 3, 4, 5, 4),
                r(.bedroom, 1, 0, 0, 4, 4), r(.bedroom, 1, 4, 0, 4, 4, "Bedroom 2"), r(.toy, 1, 8, 0, 4, 4),
                r(.bathroom, 1, 0, 4, 4, 3),
                r(.bedroom, 2, 0, 0, 4, 4, "Bedroom 3"), r(.bedroom, 2, 4, 0, 4, 4, "Bedroom 4"), r(.office, 2, 8, 0, 4, 4),
            ]
        }
    }

    static func inviteCode() -> String {
        let chars = Array("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
        return String((0..<6).map { _ in chars.randomElement() ?? "A" })
    }
}
