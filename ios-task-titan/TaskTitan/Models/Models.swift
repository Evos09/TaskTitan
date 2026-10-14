import Foundation

/// Room kinds. Raw values match the Android app so exports are interchangeable.
nonisolated enum RoomType: String, Codable, CaseIterable, Hashable, Sendable {
    case kitchen = "KITCHEN", living = "LIVING", dining = "DINING", bedroom = "BEDROOM", bathroom = "BATHROOM"
    case laundry = "LAUNDRY", toy = "TOY", office = "OFFICE", garage = "GARAGE", custom = "CUSTOM"

    var label: String {
        switch self {
        case .kitchen: "Kitchen"
        case .living: "Living room"
        case .dining: "Dining room"
        case .bedroom: "Bedroom"
        case .bathroom: "Bathroom"
        case .laundry: "Laundry"
        case .toy: "Toy room"
        case .office: "Home office"
        case .garage: "Garage"
        case .custom: "Custom room"
        }
    }
}

nonisolated enum ChoreKind: String, Codable, CaseIterable, Hashable, Sendable {
    case dishes = "DISHES", toys = "TOYS", laundry = "LAUNDRY", floor = "FLOOR", trash = "TRASH"
    case surfaces = "SURFACES", bed = "BED", plants = "PLANTS", general = "GENERAL"
}

nonisolated enum Frequency: String, Codable, CaseIterable, Hashable, Sendable {
    case daily = "DAILY", weekly = "WEEKLY", monthly = "MONTHLY", custom = "CUSTOM"

    var label: String {
        switch self {
        case .daily: "Daily"
        case .weekly: "Weekly"
        case .monthly: "Monthly"
        case .custom: "Every few days"
        }
    }
}

nonisolated enum HomeSize: String, Codable, CaseIterable, Hashable, Sendable {
    case studio = "STUDIO", one = "ONE", two = "TWO", three = "THREE", four = "FOUR"

    var label: String {
        switch self {
        case .studio: "Studio"
        case .one: "1BR"
        case .two: "2BR"
        case .three: "3BR"
        case .four: "4BR+"
        }
    }
}

nonisolated struct Room: Codable, Hashable, Identifiable, Sendable {
    var id: String
    var name: String
    var type: RoomType
    var floor: Int
    var x: Int
    var y: Int
    var w: Int
    var h: Int
}

nonisolated struct Member: Codable, Hashable, Identifiable, Sendable {
    var id: String
    var name: String
    var colorIndex: Int
    var isYou: Bool = false
    var availableDays: [Int] = [1, 2, 3, 4, 5, 6, 7]
}

nonisolated struct ChecklistItem: Codable, Hashable, Identifiable, Sendable {
    var id: String
    var text: String
}

nonisolated struct Chore: Codable, Hashable, Identifiable, Sendable {
    var id: String
    var title: String
    var roomId: String
    var kind: ChoreKind
    var weight: Int
    var frequency: Frequency = .daily
    var intervalDays: Int = 1
    var startEpochDay: Int
    var assigneeId: String? = nil
    var rotate: Bool = false
    var autoAssign: Bool = false
    var timerMinutes: Int? = nil
    var needsConfirm: Bool = false
    var checklist: [ChecklistItem] = []
    var notes: String = ""
    /// Local minute-of-day (0–1439) for the gentle reminder; nil = off.
    var remindMinuteOfDay: Int? = nil
}

nonisolated struct Completion: Codable, Hashable, Identifiable, Sendable {
    var id: String
    var choreId: String
    var epochDay: Int
    var memberId: String
    var xp: Int
    var coins: Int
    var energy: Int
    var confirmed: Bool = true
    var atMillis: Int64 = 0
}

nonisolated struct Memory: Codable, Hashable, Identifiable, Sendable {
    var id: String
    var roomId: String
    var roomName: String
    var epochDay: Int
    var messKinds: [ChoreKind]
}

nonisolated struct AvatarLook: Codable, Hashable, Sendable {
    var body: Int = 1
    var skin: Int = 2
    var hairColor: Int = 1
    var hair: String = "hair_bob"
    var outfit: String = "outfit_tangerine"
    var shoes: String = "shoes_sky"
    var accessory: String? = nil
    var pet: String? = nil
    var aura: String? = nil
}

nonisolated struct Profile: Codable, Hashable, Sendable {
    var name: String = "You"
    var coins: Int = 50
    var bonusXp: Int = 0
    var owned: [String] = ["hair_bob", "hair_short", "outfit_tangerine", "outfit_sky", "shoes_sky", "shoes_white"]
    var look: AvatarLook = AvatarLook()
    var claimedQuests: [String] = []
    var claimedAchievements: [String] = []
    var equippedDecor: [String] = []
    var lastSeenLevel: Int = 1
}

nonisolated struct AppSettings: Codable, Hashable, Sendable {
    var consentGiven: Bool = false
    var ageGroup: String = "adult"
    var parentApproved: Bool = false
    var highContrast: Bool = false
    var reducedMotion: Bool = false
    var largeText: Bool = false
    var shareAvatar: Bool = false
    var shareHouse: Bool = false
    var shareActivity: Bool = false
    var shareProgress: Bool = false
    var interHouseholdChallenge: Bool = false
    var reminders: Bool = true
    /// Epoch day until which reminders are paused; 0 = not paused. Auto-expires.
    var reminderPausedUntil: Int = 0
}

nonisolated struct AppData: Codable, Hashable, Sendable {
    var version: Int = 1
    var setupDone: Bool = false
    var householdName: String = "Our Home"
    var inviteCode: String = ""
    var homeSize: HomeSize = .two
    var floors: Int = 1
    var rooms: [Room] = []
    var members: [Member] = []
    var chores: [Chore] = []
    var completions: [Completion] = []
    var memories: [Memory] = []
    var profile: Profile = Profile()
    var settings: AppSettings = AppSettings()
    var weeklyGoal: Int = 40
    var isPro: Bool = false
}

// Tolerant decoding: any field missing in older saves falls back to its default.
nonisolated extension AppSettings {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = AppSettings()
        consentGiven = try c.decodeIfPresent(Bool.self, forKey: .consentGiven) ?? d.consentGiven
        ageGroup = try c.decodeIfPresent(String.self, forKey: .ageGroup) ?? d.ageGroup
        parentApproved = try c.decodeIfPresent(Bool.self, forKey: .parentApproved) ?? d.parentApproved
        highContrast = try c.decodeIfPresent(Bool.self, forKey: .highContrast) ?? d.highContrast
        reducedMotion = try c.decodeIfPresent(Bool.self, forKey: .reducedMotion) ?? d.reducedMotion
        largeText = try c.decodeIfPresent(Bool.self, forKey: .largeText) ?? d.largeText
        shareAvatar = try c.decodeIfPresent(Bool.self, forKey: .shareAvatar) ?? d.shareAvatar
        shareHouse = try c.decodeIfPresent(Bool.self, forKey: .shareHouse) ?? d.shareHouse
        shareActivity = try c.decodeIfPresent(Bool.self, forKey: .shareActivity) ?? d.shareActivity
        shareProgress = try c.decodeIfPresent(Bool.self, forKey: .shareProgress) ?? d.shareProgress
        interHouseholdChallenge = try c.decodeIfPresent(Bool.self, forKey: .interHouseholdChallenge) ?? d.interHouseholdChallenge
        reminders = try c.decodeIfPresent(Bool.self, forKey: .reminders) ?? d.reminders
        reminderPausedUntil = try c.decodeIfPresent(Int.self, forKey: .reminderPausedUntil) ?? d.reminderPausedUntil
    }
}
