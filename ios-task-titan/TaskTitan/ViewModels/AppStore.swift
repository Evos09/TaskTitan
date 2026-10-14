import Foundation
import Observation

/// A chore as it stands on a given day.
struct ChoreStatus: Identifiable, Hashable {
    let chore: Chore
    let room: Room?
    let completion: Completion?
    var id: String { chore.id }
    var isDone: Bool { completion != nil }
    var awaitingConfirm: Bool { completion.map { !$0.confirmed } ?? false }
}

struct QuestStatus: Identifiable, Hashable {
    let id: String
    let title: String
    let coins: Int
    let xp: Int
    let progress: Double
    let claimed: Bool
}

/// Everything screens need, derived from `AppData` for the current day.
struct Stats {
    var today: Int
    var you: Member?
    var level: LevelInfo
    var totalXp: Int
    var coins: Int
    var energy: Int
    var tier: EnergyTier
    var streak: Int
    var todayChores: [ChoreStatus]
    var waitingCount: Int
    var houseCondition: Int
    var roomCondition: [String: Int]
    var householdWeek: Int
    var weekStreak: Int
    var yourWeek: Int
    var yourTotal: Int
    var sparklingDaysThisWeek: Int
    var contributors: [Member]
    var quests: [QuestStatus]
    var season: SeasonEvent
    var seasonProgress: Int
    var seasonGoal: Int
    var seasonDaysLeft: Int
    var seasonUnlocked: Bool
    var unlockedAchievements: Set<String>
}

struct Celebration: Identifiable, Equatable {
    let title: String
    let message: String
    var big: Bool = false
    let id = UUID()
}

/// App-wide state: encrypted persistence, derived stats, and every user action.
@Observable
final class AppStore {
    private(set) var data: AppData = AppData() {
        didSet { recompute() }
    }
    private(set) var stats: Stats
    private(set) var loaded = false
    private(set) var today: Int = DateUtil.today()

    /// choreId → time it was just completed; drives the room-tidying animation.
    private(set) var recentClean: [String: Date] = [:]
    /// Room the avatar should stroll to and celebrate in.
    private(set) var avatarFocus: (roomId: String, at: Date)?
    var celebration: Celebration?

    @ObservationIgnored private let store = SecureStore()

    init() {
        stats = AppStore.computeStats(AppData(), today: DateUtil.today())
        if let saved = store.load() { data = saved }
        recompute()
        loaded = true
        ReminderService.shared.sync(data)
    }

    private func recompute() {
        stats = AppStore.computeStats(data, today: today)
    }

    func refreshDay() {
        let now = DateUtil.today()
        if now != today {
            today = now
            recompute()
        }
        // Expired pauses and day rollovers are picked up here.
        ReminderService.shared.sync(data)
    }

    private func mutate(_ block: (inout AppData) -> Void) {
        var copy = data
        block(&copy)
        data = copy
        store.save(copy)
        ReminderService.shared.sync(copy)
    }

    static func you(_ d: AppData) -> Member? { d.members.first { $0.isYou } }

    // MARK: Onboarding & house

    func giveConsent(name: String, ageGroup: String, parentApproved: Bool) {
        mutate { d in
            let trimmed = name.trimmingCharacters(in: .whitespaces)
            let you = Member(id: GameRules.newId(), name: trimmed.isEmpty ? "You" : trimmed, colorIndex: 0, isYou: true)
            d.members = [you]
            d.inviteCode = GameRules.inviteCode()
            d.profile.name = you.name
            d.settings.consentGiven = true
            d.settings.ageGroup = ageGroup
            d.settings.parentApproved = parentApproved
        }
        ReminderService.shared.requestPermission()
    }

    func saveHouse(rooms: [Room], floors: Int, size: HomeSize) {
        mutate { d in
            let ids = Set(rooms.map(\.id))
            let kept = d.chores.filter { ids.contains($0.roomId) }
            let seeded = d.setupDone ? [] : starterChores(rooms, youId: d.members.first?.id)
            d.rooms = rooms
            d.floors = min(max(floors, 1), GameRules.maxFloors)
            d.homeSize = size
            d.chores = kept + seeded
            d.setupDone = true
        }
    }

    private func starterChores(_ rooms: [Room], youId: String?) -> [Chore] {
        var seen = Set<RoomType>()
        var out: [Chore] = []
        for room in rooms where !seen.contains(room.type) {
            seen.insert(room.type)
            if let t = Catalog.templates(for: room.type).first { out.append(fromTemplate(t, room: room, assignee: youId)) }
        }
        return Array(out.prefix(6))
    }

    func fromTemplate(_ t: ChoreTemplate, room: Room, assignee: String?, start: Int = DateUtil.today()) -> Chore {
        Chore(
            id: GameRules.newId(), title: t.title, roomId: room.id, kind: t.kind, weight: t.weight,
            frequency: t.frequency, intervalDays: t.intervalDays, startEpochDay: start, assigneeId: assignee,
            checklist: t.checklist.map { ChecklistItem(id: GameRules.newId(), text: $0) }
        )
    }

    // MARK: Chores

    func saveChore(_ chore: Chore) {
        mutate { d in
            var c = chore
            if c.autoAssign { c.assigneeId = leastBusy(d, excluding: c.id) }
            if let i = d.chores.firstIndex(where: { $0.id == c.id }) { d.chores[i] = c } else { d.chores.append(c) }
        }
    }

    func deleteChore(_ id: String) { mutate { $0.chores.removeAll { $0.id == id } } }

    private func leastBusy(_ d: AppData, excluding: String) -> String? {
        let weekday = DateUtil.dayOfWeek(DateUtil.today()) + 1
        var pool = d.members.filter { $0.availableDays.contains(weekday) }
        if pool.isEmpty { pool = d.members }
        return pool.min { a, b in
            d.chores.filter { $0.assigneeId == a.id && $0.id != excluding }.count <
                d.chores.filter { $0.assigneeId == b.id && $0.id != excluding }.count
        }?.id
    }

    private func nextInRotation(_ d: AppData, _ chore: Chore) -> String? {
        guard !d.members.isEmpty else { return chore.assigneeId }
        let idx = d.members.firstIndex { $0.id == chore.assigneeId } ?? -1
        return d.members[(idx + 1) % d.members.count].id
    }

    func completeChore(_ choreId: String, memberId: String? = nil) {
        let before = data
        let day = DateUtil.today()
        guard let chore = before.chores.first(where: { $0.id == choreId }) else { return }
        if before.completions.contains(where: { $0.choreId == choreId && $0.epochDay == day }) { return }
        let assignee = chore.assigneeId.flatMap { id in before.members.contains { $0.id == id } ? id : nil }
        guard let doer = memberId ?? assignee ?? AppStore.you(before)?.id else { return }
        let isYou = before.members.first { $0.id == doer }?.isYou == true
        let reward = GameRules.reward(chore.weight)
        let streakNow = GameRules.streak(before.completions, memberId: doer, today: day)
        let firstToday = !before.completions.contains { $0.memberId == doer && $0.epochDay == day }
        let streakBonus = isYou && firstToday && streakNow >= 1 ? min(streakNow, 7) * 2 : 0
        let completion = Completion(
            id: GameRules.newId(), choreId: choreId, epochDay: day, memberId: doer,
            xp: reward.xp + streakBonus, coins: reward.coins, energy: reward.energy,
            confirmed: !chore.needsConfirm, atMillis: Int64(Date().timeIntervalSince1970 * 1000)
        )
        let levelBefore = stats.level.level
        var sparkle: Celebration?

        mutate { d in
            d.completions.append(completion)
            if isYou && completion.confirmed { d.profile.coins += reward.coins }
            var updated = chore
            if chore.rotate { updated.assigneeId = nextInRotation(d, chore) } else if chore.autoAssign { updated.assigneeId = leastBusy(d, excluding: chore.id) }
            if let i = d.chores.firstIndex(where: { $0.id == chore.id }) { d.chores[i] = updated }
            if let room = d.rooms.first(where: { $0.id == chore.roomId }) {
                let dueInRoom = d.chores.filter { $0.roomId == room.id && GameRules.isDue($0, day: day) }
                let completions = d.completions
                let allDone = dueInRoom.allSatisfy { c in completions.contains { $0.choreId == c.id && $0.epochDay == day } }
                let remembered = d.memories.contains { $0.roomId == room.id && $0.epochDay == day }
                if allDone && !remembered {
                    let memory = Memory(id: GameRules.newId(), roomId: room.id, roomName: room.name, epochDay: day, messKinds: dueInRoom.map(\.kind))
                    d.memories = Array(([memory] + d.memories).prefix(40))
                    sparkle = Celebration(title: "\(room.name) is sparkling!", message: "Saved to your house memories.")
                }
            }
        }
        recentClean[choreId] = Date()
        avatarFocus = (chore.roomId, Date())
        if let sparkle { celebration = sparkle }
        if stats.level.level > levelBefore {
            celebration = Celebration(title: "Level \(stats.level.level)!", message: "New closet items may be ready in Rewards.", big: true)
        }
    }

    func undoCompletion(_ choreId: String) {
        let day = DateUtil.today()
        mutate { d in
            guard let c = d.completions.first(where: { $0.choreId == choreId && $0.epochDay == day }) else { return }
            let wasYou = d.members.first { $0.id == c.memberId }?.isYou == true
            if wasYou && c.confirmed { d.profile.coins = max(d.profile.coins - c.coins, 0) }
            d.completions.removeAll { $0.id == c.id }
        }
    }

    func confirmCompletion(_ completionId: String) {
        mutate { d in
            guard let i = d.completions.firstIndex(where: { $0.id == completionId }) else { return }
            let c = d.completions[i]
            d.completions[i].confirmed = true
            if d.members.first(where: { $0.id == c.memberId })?.isYou == true { d.profile.coins += c.coins }
        }
    }

    // MARK: Household

    func addMember(_ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        mutate { d in d.members.append(Member(id: GameRules.newId(), name: trimmed, colorIndex: d.members.count % Catalog.memberColors.count)) }
    }

    func removeMember(_ id: String) {
        mutate { d in
            let you = AppStore.you(d)
            guard you?.id != id else { return }
            d.members.removeAll { $0.id == id }
            for i in d.chores.indices where d.chores[i].assigneeId == id { d.chores[i].assigneeId = you?.id }
        }
    }

    func setWeeklyGoal(_ goal: Int) { mutate { $0.weeklyGoal = min(max(goal, 5), 300) } }

    func renameHousehold(_ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        mutate { $0.householdName = trimmed.isEmpty ? "Our Home" : trimmed }
    }

    /// Master switch for gentle reminders; turning it on clears any pause.
    func setReminders(_ on: Bool) {
        mutate { d in
            d.settings.reminders = on
            if on { d.settings.reminderPausedUntil = 0 }
        }
        if on { ReminderService.shared.requestPermission() }
    }

    /// Silence all reminders for the next seven days; they resume automatically.
    func pauseRemindersForWeek() { mutate { $0.settings.reminderPausedUntil = DateUtil.today() + 7 } }

    func resumeReminders() { mutate { $0.settings.reminderPausedUntil = 0 } }

    var remindersPaused: Bool {
        data.settings.reminderPausedUntil > 0 && today <= data.settings.reminderPausedUntil
    }

    // MARK: Rewards

    /// Returns an error message, or nil when the item was bought (and equipped).
    func buy(_ itemId: String) -> String? {
        guard let item = Catalog.cosmetic(itemId) else { return "Item not found" }
        if data.profile.owned.contains(itemId) { return nil }
        if item.pro && !data.isPro { return "Part of Task Titan Pro" }
        if stats.level.level < item.unlockLevel { return "Unlocks at level \(item.unlockLevel)" }
        if data.profile.coins < item.price { return "You need \(item.price - data.profile.coins) more coins" }
        mutate { d in
            d.profile.coins -= item.price
            d.profile.owned.append(itemId)
        }
        equip(itemId)
        return nil
    }

    func equip(_ itemId: String) {
        guard let item = Catalog.cosmetic(itemId), data.profile.owned.contains(itemId) else { return }
        mutate { d in
            switch item.category {
            case .hair: d.profile.look.hair = itemId
            case .outfit: d.profile.look.outfit = itemId
            case .shoes: d.profile.look.shoes = itemId
            case .accessory: d.profile.look.accessory = d.profile.look.accessory == itemId ? nil : itemId
            case .pet: d.profile.look.pet = d.profile.look.pet == itemId ? nil : itemId
            case .aura: d.profile.look.aura = d.profile.look.aura == itemId ? nil : itemId
            case .decor:
                if d.profile.equippedDecor.contains(itemId) { d.profile.equippedDecor.removeAll { $0 == itemId } } else { d.profile.equippedDecor.append(itemId) }
            }
        }
    }

    func setLook(_ look: AvatarLook) { mutate { $0.profile.look = look } }

    func claimQuest(_ id: String) {
        guard let q = stats.quests.first(where: { $0.id == id }), !q.claimed, q.progress >= 1 else { return }
        let key = "\(DateUtil.today()):\(id)"
        mutate { d in
            d.profile.coins += q.coins
            d.profile.bonusXp += q.xp
            d.profile.claimedQuests = Array((d.profile.claimedQuests + [key]).suffix(60))
        }
        celebration = Celebration(title: "Quest complete!", message: "+\(q.coins) coins · +\(q.xp) XP")
    }

    func claimAchievement(_ id: String) {
        guard let a = Catalog.achievements.first(where: { $0.id == id }),
              !data.profile.claimedAchievements.contains(id), stats.unlockedAchievements.contains(id) else { return }
        mutate { d in
            d.profile.coins += a.coins
            d.profile.claimedAchievements.append(id)
        }
        celebration = Celebration(title: a.title, message: "Milestone reached! +\(a.coins) coins", big: true)
    }

    func claimSeason() {
        let s = stats
        guard s.seasonUnlocked, !data.profile.owned.contains(s.season.decorId) else { return }
        mutate { d in
            d.profile.owned.append(s.season.decorId)
            d.profile.equippedDecor.append(s.season.decorId)
        }
        celebration = Celebration(title: "\(s.season.name) reward!", message: "\(Catalog.cosmetic(s.season.decorId)?.name ?? "New decor") now decorates your home.", big: true)
    }

    // MARK: Settings & privacy

    func updateSettings(_ block: (inout AppSettings) -> Void) { mutate { block(&$0.settings) } }

    func exportJSON() -> String { store.export(data) }

    func deleteEverything() {
        store.wipe()
        ReminderService.shared.cancelAll()
        recentClean = [:]
        avatarFocus = nil
        data = AppData()
    }

    // MARK: Derived stats

    static func computeStats(_ d: AppData, today: Int) -> Stats {
        let you = you(d)
        let validIds = Set(d.chores.map(\.id))
        let confirmed = d.completions.filter(\.confirmed)
        let yours = confirmed.filter { $0.memberId == you?.id }
        let totalXp = yours.reduce(0) { $0 + $1.xp } + d.profile.bonusXp
        let level = GameRules.levelInfo(totalXp)
        let energy = min(yours.filter { $0.epochDay == today }.reduce(0) { $0 + $1.energy }, GameRules.maxEnergy)
        let roomsById = Dictionary(d.rooms.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let todayCompletions = Dictionary(d.completions.filter { $0.epochDay == today }.map { ($0.choreId, $0) }, uniquingKeysWith: { a, _ in a })
        let todayChores = d.chores.filter { GameRules.isDue($0, day: today) }
            .map { ChoreStatus(chore: $0, room: roomsById[$0.roomId], completion: todayCompletions[$0.id]) }
            .sorted { a, b in a.isDone != b.isDone ? !a.isDone : a.chore.weight > b.chore.weight }
        let dueCount = todayChores.count
        let doneCount = todayChores.filter(\.isDone).count
        let condition = dueCount == 0 ? 100 : doneCount * 100 / dueCount
        var roomCondition: [String: Int] = [:]
        for r in d.rooms {
            let due = todayChores.filter { $0.chore.roomId == r.id }
            roomCondition[r.id] = due.isEmpty ? 100 : due.filter(\.isDone).count * 100 / due.count
        }
        let weekStart = DateUtil.weekStart(today)
        let weekCompletions = confirmed.filter { $0.epochDay >= weekStart && $0.epochDay <= today }
        let sparklingDays = (weekStart...today).filter { day in
            let due = d.chores.filter { GameRules.isDue($0, day: day) }
            return !due.isEmpty && due.allSatisfy { c in d.completions.contains { $0.choreId == c.id && $0.epochDay == day } }
        }.count
        let contributors = d.members.filter { m in weekCompletions.contains { $0.memberId == m.id } }

        let yourToday = yours.filter { $0.epochDay == today && validIds.contains($0.choreId) }
        let choresById = Dictionary(d.chores.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let claimed = Set(d.profile.claimedQuests.filter { $0.hasPrefix("\(today):") }.compactMap { $0.split(separator: ":").last.map(String.init) })
        let quests = Catalog.dailyQuests.map { q -> QuestStatus in
            let progress: Double
            switch q.id {
            case "q_three": progress = Double(yourToday.count) / 3
            case "q_heavy": progress = yourToday.contains { (choresById[$0.choreId]?.weight ?? 0) >= 3 } ? 1 : 0
            default: progress = d.memories.contains { $0.epochDay == today } ? 1 : 0
            }
            return QuestStatus(id: q.id, title: q.title, coins: q.coins, xp: q.xp, progress: min(progress, 1), claimed: claimed.contains(q.id))
        }

        let season = Catalog.season(for: DateUtil.month(today))
        let monthStart = today - (DateUtil.dayOfMonth(today) - 1)
        let seasonProgress = confirmed.filter { $0.epochDay >= monthStart && $0.epochDay <= today }.count
        let seasonGoal = 25
        let daysLeft = DateUtil.lengthOfMonth(today) - DateUtil.dayOfMonth(today)

        let streak = you.map { GameRules.streak(d.completions, memberId: $0.id, today: today) } ?? 0
        let heavyCount = yours.filter { (choresById[$0.choreId]?.weight ?? 0) >= 3 }.count
        var unlocked = Set<String>()
        if !yours.isEmpty { unlocked.insert("first") }
        if yours.count >= 10 { unlocked.insert("ten") }
        if yours.count >= 50 { unlocked.insert("fifty") }
        if heavyCount >= 5 { unlocked.insert("heavy") }
        if streak >= 3 { unlocked.insert("streak3") }
        if streak >= 7 { unlocked.insert("streak7") }
        if d.rooms.count >= 6 { unlocked.insert("builder") }
        if d.floors >= 3 && (0...2).allSatisfy({ f in d.rooms.contains { $0.floor == f } }) { unlocked.insert("tower") }

        return Stats(
            today: today, you: you, level: level, totalXp: totalXp, coins: d.profile.coins,
            energy: energy, tier: GameRules.tier(energy), streak: streak,
            todayChores: todayChores, waitingCount: dueCount - doneCount,
            houseCondition: condition, roomCondition: roomCondition,
            householdWeek: weekCompletions.count,
            weekStreak: GameRules.weeklyStreak(confirmed, goal: d.weeklyGoal, today: today),
            yourWeek: weekCompletions.filter { $0.memberId == you?.id }.count,
            yourTotal: yours.count, sparklingDaysThisWeek: sparklingDays, contributors: contributors,
            quests: quests, season: season, seasonProgress: seasonProgress, seasonGoal: seasonGoal,
            seasonDaysLeft: daysLeft, seasonUnlocked: seasonProgress >= seasonGoal,
            unlockedAchievements: unlocked
        )
    }
}
