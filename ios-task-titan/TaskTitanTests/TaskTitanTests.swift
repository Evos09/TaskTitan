//
//  TaskTitanTests.swift
//  TaskTitanTests
//
//  Created by Rork on October 14, 2026.
//

import Foundation
import Testing
@testable import TaskTitan

/// Epoch day for a calendar date (month is 1-based).
private func day(_ year: Int, _ month: Int, _ dayOfMonth: Int) -> Int {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = TimeZone(identifier: "UTC")!
    let date = cal.date(from: DateComponents(year: year, month: month, day: dayOfMonth))!
    return Int(date.timeIntervalSince1970 / 86_400)
}

private func chore(_ id: String = "c", weight: Int = 1, frequency: Frequency = .daily, intervalDays: Int = 1, start: Int = 0) -> Chore {
    Chore(id: id, title: id, roomId: "r", kind: .general, weight: weight, frequency: frequency, intervalDays: intervalDays, startEpochDay: start)
}

private func done(_ choreId: String, _ epochDay: Int, member: String = "me", confirmed: Bool = true, xp: Int = 20, energy: Int = 1) -> Completion {
    Completion(id: "\(choreId)@\(epochDay)@\(member)", choreId: choreId, epochDay: epochDay, memberId: member, xp: xp, coins: 4, energy: energy, confirmed: confirmed)
}

struct SavedDataTests {
    @Test func olderSavesMissingNewFieldsStillLoad() throws {
        // A save from before most optional fields existed.
        let json = """
        {
          "setupDone": true,
          "members": [{ "id": "me", "name": "Tester" }],
          "chores": [{ "id": "c1", "title": "Dishes", "roomId": "r", "kind": "DISHES", "weight": 2, "startEpochDay": 100 }],
          "completions": [{ "id": "x", "choreId": "c1", "epochDay": 100, "memberId": "me", "xp": 30, "coins": 8, "energy": 2 }],
          "profile": { "coins": 120, "look": { "hair": "hair_short" } }
        }
        """
        let d = try JSONDecoder().decode(AppData.self, from: Data(json.utf8))
        #expect(d.setupDone)
        #expect(d.householdName == "Our Home")
        #expect(d.members.first?.availableDays == [1, 2, 3, 4, 5, 6, 7])
        #expect(d.chores.first?.frequency == .daily)
        #expect(d.chores.first?.remindMinuteOfDay == nil)
        #expect(d.completions.first?.confirmed == true)
        #expect(d.profile.coins == 120)
        #expect(d.profile.look.hair == "hair_short")
        #expect(d.profile.look.outfit == AvatarLook().outfit)
        #expect(d.settings.reminders)
        #expect(d.weeklyGoal == 40)
    }

    @Test func fullDataRoundTrips() throws {
        var d = AppData()
        d.members = [Member(id: "me", name: "Tester", colorIndex: 2, isYou: true, availableDays: [1, 3])]
        var c = chore("c1", weight: 3, frequency: .custom, intervalDays: 4, start: 50)
        c.remindMinuteOfDay = 540
        c.checklist = [ChecklistItem(id: "i", text: "Rinse")]
        d.chores = [c]
        d.completions = [done("c1", 50, confirmed: false)]
        d.profile.look.pet = "pet_cat"
        d.isPro = true
        let back = try JSONDecoder().decode(AppData.self, from: JSONEncoder().encode(d))
        #expect(back == d)
    }
}

struct GameRulesTests {
    @Test func rewardScalesWithWeightAndClamps() {
        #expect(GameRules.reward(1) == Reward(xp: 20, coins: 4, energy: 1))
        #expect(GameRules.reward(5) == Reward(xp: 60, coins: 20, energy: 5))
        #expect(GameRules.reward(0) == GameRules.reward(1))
        #expect(GameRules.reward(9) == GameRules.reward(5))
    }

    @Test func levelsNeedSixtyMoreXpEachTime() {
        #expect(GameRules.levelInfo(0) == LevelInfo(level: 1, xpInto: 0, xpNeeded: 100))
        #expect(GameRules.levelInfo(100) == LevelInfo(level: 2, xpInto: 0, xpNeeded: 160))
        #expect(GameRules.levelInfo(259) == LevelInfo(level: 2, xpInto: 159, xpNeeded: 160))
        #expect(GameRules.levelInfo(260) == LevelInfo(level: 3, xpInto: 0, xpNeeded: 220))
    }

    @Test func dueDatesFollowFrequency() {
        let weekly = chore(frequency: .weekly, start: 100)
        #expect(!GameRules.isDue(weekly, day: 99))
        #expect(GameRules.isDue(weekly, day: 100))
        #expect(!GameRules.isDue(weekly, day: 106))
        #expect(GameRules.isDue(weekly, day: 107))
        let custom = chore(frequency: .custom, intervalDays: 3, start: 100)
        #expect(GameRules.isDue(custom, day: 103))
        #expect(!GameRules.isDue(custom, day: 104))
        #expect(GameRules.isDue(chore(frequency: .custom, intervalDays: 0, start: 100), day: 101))
    }

    @Test func monthlyChoresOnThe31stFallOnLastDayOfShortMonths() {
        let c = chore(frequency: .monthly, start: day(2025, 1, 31))
        #expect(GameRules.isDue(c, day: day(2025, 2, 28)))
        #expect(!GameRules.isDue(c, day: day(2025, 2, 27)))
        #expect(GameRules.isDue(c, day: day(2025, 4, 30)))
        #expect(GameRules.isDue(c, day: day(2025, 5, 31)))
    }

    @Test func healthThresholds() {
        #expect(GameRules.health(due: 0, done: 0) == .healthy)
        #expect(GameRules.health(due: 3, done: 3) == .healthy)
        #expect(GameRules.health(due: 4, done: 2) == .queasy)
        #expect(GameRules.health(due: 3, done: 1) == .sick)
    }

    @Test func avatarStyleDefaultsToBoyForOldSaves() throws {
        let look = try JSONDecoder().decode(AvatarLook.self, from: Data(#"{"hair":"hair_bob"}"#.utf8))
        #expect(look.style == "boy")
    }

    @Test func streaksCountConsecutiveConfirmedDays() {
        let list = [done("c", 100), done("c", 99), done("c", 98), done("c", 96), done("c", 97, confirmed: false)]
        #expect(GameRules.streak(list, memberId: "me", today: 100) == 3)
        #expect(GameRules.streak([done("c", 99)], memberId: "me", today: 100) == 1)
        #expect(GameRules.streak([done("c", 99)], memberId: "me", today: 101) == 0)
    }

    @Test func everyStarterLayoutFitsWithoutOverlaps() {
        for size in HomeSize.allCases {
            let rooms = GameRules.layout(for: size)
            #expect(!rooms.isEmpty)
            for r in rooms {
                #expect(r.x >= 0 && r.y >= 0 && r.x + r.w <= GameRules.gridW && r.y + r.h <= GameRules.gridH)
                #expect(r.floor >= 0 && r.floor < GameRules.maxFloors)
                #expect(!GameRules.overlaps(rooms, candidate: r))
            }
        }
    }
}

struct DateUtilTests {
    @Test func weeksStartOnSunday() {
        #expect(DateUtil.dayOfWeek(day(2025, 1, 5)) == 0)
        #expect(DateUtil.weekStart(day(2025, 1, 11)) == day(2025, 1, 5))
        #expect(DateUtil.weekStart(day(2025, 1, 1)) == day(2024, 12, 29))
    }

    @Test func monthsHandleLeapYears() {
        #expect(DateUtil.lengthOfMonth(day(2024, 2, 10)) == 29)
        #expect(DateUtil.lengthOfMonth(day(2025, 2, 10)) == 28)
        #expect(DateUtil.label(day(2025, 3, 9)) == "Mar 9")
    }
}

@MainActor
struct StatsTests {
    private let today = day(2025, 1, 22)

    private func base() -> AppData {
        var d = AppData()
        d.members = [Member(id: "me", name: "Tester", colorIndex: 0, isYou: true), Member(id: "sam", name: "Sam", colorIndex: 1)]
        d.rooms = [Room(id: "r", name: "Kitchen", type: .kitchen, floor: 0, x: 0, y: 0, w: 4, h: 4)]
        d.chores = [chore("light", weight: 1, start: today - 30), chore("heavy", weight: 3, start: today - 30)]
        return d
    }

    @Test func completingAChoreUpdatesConditionXpAndStreak() {
        var d = base()
        d.completions = [done("light", today)]
        let s = AppStore.computeStats(d, today: today)
        #expect(s.houseCondition == 50)
        #expect(s.waitingCount == 1)
        #expect(s.totalXp == 20)
        #expect(s.tier == .energized)
        #expect(s.streak == 1)
        #expect(s.todayChores.map(\.chore.id) == ["heavy", "light"])
        #expect(s.unlockedAchievements.contains("first"))
    }

    @Test func healthFollowsYourChoresForToday() {
        #expect(AppStore.computeStats(base(), today: today).health == .sick)
        var d = base()
        d.completions = [done("light", today)]
        let half = AppStore.computeStats(d, today: today)
        #expect(half.health == .queasy)
        #expect(half.yourDueToday == 2 && half.yourDoneToday == 1)
        d.completions.append(done("heavy", today, member: "sam"))
        #expect(AppStore.computeStats(d, today: today).health == .healthy)
    }

    @Test func choresAssignedToSomeoneElseDoNotMakeYouSick() {
        var d = base()
        d.chores = d.chores.map { var c = $0; c.assigneeId = "sam"; return c }
        let s = AppStore.computeStats(d, today: today)
        #expect(s.yourDueToday == 0)
        #expect(s.health == .healthy)
    }

    @Test func unconfirmedAndOtherMembersWorkDoesNotEarnYourXp() {
        var d = base()
        d.completions = [done("light", today, confirmed: false), done("heavy", today, member: "sam")]
        let s = AppStore.computeStats(d, today: today)
        #expect(s.houseCondition == 100)
        #expect(s.totalXp == 0)
        #expect(s.householdWeek == 1)
    }
}
