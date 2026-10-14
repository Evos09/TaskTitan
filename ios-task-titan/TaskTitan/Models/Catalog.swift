import Foundation

/// A reusable chore preset offered in the template picker.
nonisolated struct ChoreTemplate: Hashable, Sendable {
    let title: String
    let roomType: RoomType
    let kind: ChoreKind
    let weight: Int
    let frequency: Frequency
    var intervalDays: Int = 1
    var checklist: [String] = []
}

nonisolated enum ItemCategory: String, CaseIterable, Hashable, Sendable {
    case hair, outfit, shoes, accessory, pet, aura, decor

    var label: String {
        switch self {
        case .hair: "Hair"
        case .outfit: "Outfits"
        case .shoes: "Shoes"
        case .accessory: "Extras"
        case .pet: "Pets"
        case .aura: "Auras"
        case .decor: "Decor"
        }
    }
}

/// A cosmetic unlock. `color` drives its 3D appearance (0xRRGGBB).
nonisolated struct Cosmetic: Hashable, Identifiable, Sendable {
    let id: String
    let name: String
    let category: ItemCategory
    let unlockLevel: Int
    let price: Int
    let color: UInt32
    var pro: Bool = false
    var seasonal: Bool = false
}

nonisolated struct Achievement: Hashable, Identifiable, Sendable {
    let id: String
    let title: String
    let detail: String
    let coins: Int
}

nonisolated struct SeasonEvent: Hashable, Sendable {
    let name: String
    let reward: String
    let decorId: String
    let tint: UInt32
}

nonisolated struct DailyQuest: Hashable, Identifiable, Sendable {
    let id: String
    let title: String
    let coins: Int
    let xp: Int
}

nonisolated enum Catalog {
    static let skinTones: [UInt32] = [0xFFE0C7, 0xF6C9A0, 0xE3A777, 0xC68650, 0x95603A, 0x5F3B24]
    static let hairColors: [UInt32] = [0x2B1B12, 0x6B3E1F, 0xC98A3C, 0xF2D27A, 0xE0573E, 0x7A5CFF]
    static let memberColors: [UInt32] = [0xFF6FB5, 0x2BB5FF, 0x34C46B, 0xFF8A1F, 0x8E6CFF, 0xFFC21A]
    static let bodyLabels = ["Petite", "Classic", "Sturdy"]

    static let roomColors: [RoomType: UInt32] = [
        .kitchen: 0xFFE3C6, .living: 0xD8EEFF, .dining: 0xDDF5DA, .bedroom: 0xE9E0FF, .bathroom: 0xD3F3F6,
        .laundry: 0xEDE3FA, .toy: 0xFFDDEC, .office: 0xFFEFD2, .garage: 0xE6E8EC, .custom: 0xF1EDE6,
    ]

    static let roomAccents: [RoomType: UInt32] = [
        .kitchen: 0xE8720C, .living: 0x1C8FD6, .dining: 0x2E9A44, .bedroom: 0x7354D9, .bathroom: 0x0F94A3,
        .laundry: 0x8B55C9, .toy: 0xE0418F, .office: 0xC27A0A, .garage: 0x5D6675, .custom: 0x8A6D4C,
    ]

    static let templates: [ChoreTemplate] = [
        ChoreTemplate(title: "Wash dishes", roomType: .kitchen, kind: .dishes, weight: 2, frequency: .daily, checklist: ["Wash or load every dish", "Wipe the sink", "Dry and put away"]),
        ChoreTemplate(title: "Wipe counters", roomType: .kitchen, kind: .surfaces, weight: 1, frequency: .daily, checklist: ["Clear the counters", "Spray and wipe"]),
        ChoreTemplate(title: "Take out trash", roomType: .kitchen, kind: .trash, weight: 1, frequency: .custom, intervalDays: 2, checklist: ["Tie the bag", "New bag in the bin"]),
        ChoreTemplate(title: "Mop kitchen floor", roomType: .kitchen, kind: .floor, weight: 3, frequency: .weekly, checklist: ["Sweep first", "Mop corners too"]),
        ChoreTemplate(title: "Vacuum living room", roomType: .living, kind: .floor, weight: 3, frequency: .weekly, checklist: ["Move chairs aside", "Vacuum under the sofa", "Empty the canister"]),
        ChoreTemplate(title: "Tidy cushions & blankets", roomType: .living, kind: .general, weight: 1, frequency: .daily),
        ChoreTemplate(title: "Dust shelves", roomType: .living, kind: .surfaces, weight: 2, frequency: .weekly),
        ChoreTemplate(title: "Water plants", roomType: .living, kind: .plants, weight: 1, frequency: .custom, intervalDays: 3),
        ChoreTemplate(title: "Clear the table", roomType: .dining, kind: .dishes, weight: 1, frequency: .daily),
        ChoreTemplate(title: "Wipe table & chairs", roomType: .dining, kind: .surfaces, weight: 1, frequency: .daily),
        ChoreTemplate(title: "Make the bed", roomType: .bedroom, kind: .bed, weight: 1, frequency: .daily, checklist: ["Straighten sheets", "Fluff pillows"]),
        ChoreTemplate(title: "Put clothes away", roomType: .bedroom, kind: .laundry, weight: 2, frequency: .daily),
        ChoreTemplate(title: "Change the sheets", roomType: .bedroom, kind: .bed, weight: 3, frequency: .weekly),
        ChoreTemplate(title: "Clean sink & mirror", roomType: .bathroom, kind: .surfaces, weight: 2, frequency: .weekly),
        ChoreTemplate(title: "Scrub the tub", roomType: .bathroom, kind: .surfaces, weight: 4, frequency: .weekly),
        ChoreTemplate(title: "Clean the toilet", roomType: .bathroom, kind: .surfaces, weight: 3, frequency: .weekly),
        ChoreTemplate(title: "Restock essentials", roomType: .bathroom, kind: .general, weight: 1, frequency: .weekly),
        ChoreTemplate(title: "Run a laundry load", roomType: .laundry, kind: .laundry, weight: 2, frequency: .custom, intervalDays: 2),
        ChoreTemplate(title: "Fold laundry", roomType: .laundry, kind: .laundry, weight: 2, frequency: .custom, intervalDays: 2, checklist: ["Fold everything", "Sort by person"]),
        ChoreTemplate(title: "Pick up toys", roomType: .toy, kind: .toys, weight: 1, frequency: .daily, checklist: ["All toys back in bins", "Books on the shelf", "Rug is clear"]),
        ChoreTemplate(title: "Sort toy bins", roomType: .toy, kind: .toys, weight: 2, frequency: .weekly),
        ChoreTemplate(title: "Clear the desk", roomType: .office, kind: .general, weight: 1, frequency: .daily),
        ChoreTemplate(title: "Empty paper bin", roomType: .office, kind: .trash, weight: 1, frequency: .weekly),
        ChoreTemplate(title: "Sweep the garage", roomType: .garage, kind: .floor, weight: 3, frequency: .monthly),
        ChoreTemplate(title: "Sort recycling", roomType: .garage, kind: .trash, weight: 2, frequency: .weekly),
        ChoreTemplate(title: "Quick tidy", roomType: .custom, kind: .general, weight: 1, frequency: .daily),
    ]

    static let cosmetics: [Cosmetic] = [
        Cosmetic(id: "hair_bob", name: "Bouncy Bob", category: .hair, unlockLevel: 1, price: 0, color: 0),
        Cosmetic(id: "hair_short", name: "Fresh Crop", category: .hair, unlockLevel: 1, price: 0, color: 0),
        Cosmetic(id: "hair_bun", name: "Top Bun", category: .hair, unlockLevel: 3, price: 60, color: 0),
        Cosmetic(id: "hair_long", name: "Long Waves", category: .hair, unlockLevel: 5, price: 90, color: 0),
        Cosmetic(id: "hair_spiky", name: "Spiky Hero", category: .hair, unlockLevel: 8, price: 140, color: 0),
        Cosmetic(id: "outfit_tangerine", name: "Tangerine Tee", category: .outfit, unlockLevel: 1, price: 0, color: 0xFF8A1F),
        Cosmetic(id: "outfit_sky", name: "Sky Hoodie", category: .outfit, unlockLevel: 1, price: 0, color: 0x2BB5FF),
        Cosmetic(id: "outfit_bubblegum", name: "Bubblegum Overalls", category: .outfit, unlockLevel: 2, price: 50, color: 0xFF6FB5),
        Cosmetic(id: "outfit_sunny", name: "Sunny Hoodie", category: .outfit, unlockLevel: 4, price: 80, color: 0xFFC21A),
        Cosmetic(id: "outfit_mint", name: "Mint Apron", category: .outfit, unlockLevel: 6, price: 110, color: 0x3ED598),
        Cosmetic(id: "outfit_galaxy", name: "Galaxy Jumpsuit", category: .outfit, unlockLevel: 10, price: 220, color: 0x6C4BFF, pro: true),
        Cosmetic(id: "shoes_sky", name: "Sky Kicks", category: .shoes, unlockLevel: 1, price: 0, color: 0x2BB5FF),
        Cosmetic(id: "shoes_white", name: "Cloud Steps", category: .shoes, unlockLevel: 1, price: 0, color: 0xF5F5F5),
        Cosmetic(id: "shoes_rocket", name: "Rocket Sneakers", category: .shoes, unlockLevel: 5, price: 100, color: 0xE8372C),
        Cosmetic(id: "shoes_gold", name: "Golden Slippers", category: .shoes, unlockLevel: 12, price: 260, color: 0xFFC21A, pro: true),
        Cosmetic(id: "acc_cap", name: "Pixel Cap", category: .accessory, unlockLevel: 3, price: 70, color: 0xFF6FB5),
        Cosmetic(id: "acc_glasses", name: "Round Specs", category: .accessory, unlockLevel: 4, price: 70, color: 0x2B2B3A),
        Cosmetic(id: "acc_crown", name: "Tiny Crown", category: .accessory, unlockLevel: 15, price: 300, color: 0xFFC21A),
        Cosmetic(id: "pet_corgi", name: "Corgi Pal", category: .pet, unlockLevel: 6, price: 150, color: 0xF0A04B),
        Cosmetic(id: "pet_cat", name: "Cloud Cat", category: .pet, unlockLevel: 9, price: 180, color: 0xEDEDF2),
        Cosmetic(id: "pet_bunny", name: "Bun Bun", category: .pet, unlockLevel: 14, price: 240, color: 0xFFD1E3, pro: true),
        Cosmetic(id: "aura_spark", name: "Spark Ring", category: .aura, unlockLevel: 2, price: 40, color: 0xFFC21A),
        Cosmetic(id: "aura_sky", name: "Breeze Ring", category: .aura, unlockLevel: 7, price: 120, color: 0x2BB5FF),
        Cosmetic(id: "aura_rainbow", name: "Rainbow Halo", category: .aura, unlockLevel: 13, price: 260, color: 0xFF6FB5, pro: true),
        Cosmetic(id: "decor_plant", name: "Leafy Friend", category: .decor, unlockLevel: 2, price: 40, color: 0x3BB55C),
        Cosmetic(id: "decor_lamp", name: "Glow Lamp", category: .decor, unlockLevel: 4, price: 60, color: 0xFFE08A),
        Cosmetic(id: "decor_rug", name: "Cozy Rug", category: .decor, unlockLevel: 5, price: 90, color: 0xFF8A1F),
        Cosmetic(id: "decor_pumpkin", name: "Autumn Pumpkin", category: .decor, unlockLevel: 1, price: 0, color: 0xFF7A1A, seasonal: true),
        Cosmetic(id: "decor_snowman", name: "Snow Buddy", category: .decor, unlockLevel: 1, price: 0, color: 0xFFFFFF, seasonal: true),
        Cosmetic(id: "decor_tulips", name: "Spring Tulips", category: .decor, unlockLevel: 1, price: 0, color: 0xFF6FB5, seasonal: true),
        Cosmetic(id: "decor_beachball", name: "Beach Ball", category: .decor, unlockLevel: 1, price: 0, color: 0x2BB5FF, seasonal: true),
        Cosmetic(id: "decor_lights", name: "Star Lights", category: .decor, unlockLevel: 8, price: 150, color: 0xFFE45C, pro: true),
    ]

    static let achievements: [Achievement] = [
        Achievement(id: "first", title: "First sparkle", detail: "Finish your very first chore", coins: 20),
        Achievement(id: "ten", title: "Getting going", detail: "Finish 10 chores", coins: 40),
        Achievement(id: "fifty", title: "Home hero", detail: "Finish 50 chores", coins: 120),
        Achievement(id: "heavy", title: "Heavy lifter", detail: "Finish 5 Heavy-or-harder chores", coins: 60),
        Achievement(id: "streak3", title: "Warming up", detail: "Reach a 3-day streak", coins: 30),
        Achievement(id: "streak7", title: "Week of wins", detail: "Reach a 7-day streak", coins: 80),
        Achievement(id: "builder", title: "Architect", detail: "Design a home with 6+ rooms", coins: 50),
        Achievement(id: "tower", title: "Three stories", detail: "Build all three floors", coins: 60),
    ]

    static let dailyQuests: [DailyQuest] = [
        DailyQuest(id: "q_three", title: "Finish 3 chores today", coins: 25, xp: 30),
        DailyQuest(id: "q_heavy", title: "Finish one Heavy chore", coins: 20, xp: 25),
        DailyQuest(id: "q_room", title: "Make one room sparkle", coins: 15, xp: 20),
    ]

    /// Seasonal event for a calendar month (0-based).
    static func season(for month: Int) -> SeasonEvent {
        switch month {
        case 11, 0, 1: SeasonEvent(name: "Winter Glow", reward: "Unlock the snow buddy decor", decorId: "decor_snowman", tint: 0xD8EEFF)
        case 2, 3, 4: SeasonEvent(name: "Spring Bloom", reward: "Unlock spring tulips", decorId: "decor_tulips", tint: 0xFFE3F0)
        case 5, 6, 7: SeasonEvent(name: "Summer Splash", reward: "Unlock the beach ball", decorId: "decor_beachball", tint: 0xD3F3F6)
        default: SeasonEvent(name: "Autumn Leaves", reward: "Unlock pumpkin decor", decorId: "decor_pumpkin", tint: 0xFFE9D2)
        }
    }

    static func cosmetic(_ id: String?) -> Cosmetic? { cosmetics.first { $0.id == id } }

    static func templates(for type: RoomType) -> [ChoreTemplate] { templates.filter { $0.roomType == type } }

    /// Picks a mess kind from free text when the user writes a custom chore.
    static func guessKind(_ title: String, roomType: RoomType?) -> ChoreKind {
        let t = title.lowercased()
        func any(_ words: [String]) -> Bool { words.contains { t.contains($0) } }
        if any(["dish", "plate", "table"]) { return .dishes }
        if any(["toy", "lego", "doll"]) { return .toys }
        if any(["laundry", "fold", "cloth", "wash clothes"]) { return .laundry }
        if any(["vacuum", "sweep", "mop", "floor"]) { return .floor }
        if any(["trash", "garbage", "bin", "recycl"]) { return .trash }
        if any(["bed", "sheet"]) { return .bed }
        if any(["plant", "water"]) { return .plants }
        if any(["wipe", "dust", "scrub", "clean"]) { return .surfaces }
        switch roomType {
        case .toy: return .toys
        case .laundry: return .laundry
        case .kitchen: return .dishes
        default: return .general
        }
    }
}
