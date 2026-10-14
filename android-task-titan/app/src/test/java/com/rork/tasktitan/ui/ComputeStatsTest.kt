package com.rork.tasktitan.ui

import com.rork.tasktitan.data.AppData
import com.rork.tasktitan.data.EnergyTier
import com.rork.tasktitan.data.Frequency
import com.rork.tasktitan.data.Health
import com.rork.tasktitan.data.Member
import com.rork.tasktitan.data.Room
import com.rork.tasktitan.data.RoomType
import com.rork.tasktitan.data.chore
import com.rork.tasktitan.data.day
import com.rork.tasktitan.data.done
import com.rork.tasktitan.reminders.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeStatsTest {
    private val today = day(2025, 1, 22)
    private val me = Member("me", "Tester", 0, isYou = true)
    private val sam = Member("sam", "Sam", 1)
    private val kitchen = Room("r", "Kitchen", RoomType.KITCHEN, 0, 0, 0, 4, 4)
    private val bedroom = Room("r2", "Bedroom", RoomType.BEDROOM, 0, 4, 0, 4, 4)
    private val light = chore("light", weight = 1, start = today - 30)
    private val heavy = chore("heavy", weight = 3, start = today - 30)
    private val base = AppData(members = listOf(me, sam), rooms = listOf(kitchen, bedroom), chores = listOf(light, heavy))

    private fun stats(d: AppData) = AppViewModel.computeStats(d, today)

    @Test
    fun `a fresh home has nothing done`() {
        val s = stats(base)
        assertEquals(0, s.houseCondition)
        assertEquals(2, s.waitingCount)
        assertEquals(0, s.totalXp)
        assertEquals(1, s.level.level)
        assertEquals(EnergyTier.NEUTRAL, s.tier)
        assertEquals(0, s.streak)
        assertTrue(s.unlockedAchievements.isEmpty())
    }

    @Test
    fun `an empty home counts as fully tidy`() {
        val s = stats(AppData(members = listOf(me)))
        assertEquals(100, s.houseCondition)
        assertEquals(0, s.waitingCount)
    }

    @Test
    fun `completing a chore updates condition, xp, energy and streak`() {
        val s = stats(base.copy(completions = listOf(done("light", today, xp = 20, energy = 1))))
        assertEquals(50, s.houseCondition)
        assertEquals(1, s.waitingCount)
        assertEquals(20, s.totalXp)
        assertEquals(1, s.energy)
        assertEquals(EnergyTier.ENERGIZED, s.tier)
        assertEquals(1, s.streak)
        assertEquals(1, s.yourWeek)
        assertTrue("first" in s.unlockedAchievements)
        // Bedroom has no chores due, so it is fully tidy; the kitchen is half done.
        assertEquals(50, s.roomCondition["r"])
        assertEquals(100, s.roomCondition["r2"])
    }

    @Test
    fun `unfinished chores are listed first, heaviest first`() {
        val none = stats(base)
        assertEquals(listOf("heavy", "light"), none.todayChores.map { it.chore.id })
        val heavyDone = stats(base.copy(completions = listOf(done("heavy", today))))
        assertEquals(listOf("light", "heavy"), heavyDone.todayChores.map { it.chore.id })
        assertTrue(heavyDone.todayChores.last().isDone)
    }

    @Test
    fun `a completion awaiting confirmation is done but earns nothing yet`() {
        val s = stats(base.copy(completions = listOf(done("light", today, confirmed = false))))
        assertEquals(50, s.houseCondition)
        assertTrue(s.todayChores.first { it.chore.id == "light" }.awaitingConfirm)
        assertEquals(0, s.totalXp)
        assertEquals(0, s.energy)
        assertEquals(0, s.streak)
    }

    @Test
    fun `other members tidy the house but do not earn your xp`() {
        val s = stats(base.copy(completions = listOf(done("light", today, memberId = "sam"))))
        assertEquals(50, s.houseCondition)
        assertEquals(0, s.totalXp)
        assertEquals(1, s.householdWeek)
        assertEquals(0, s.yourWeek)
        assertEquals(listOf(sam), s.contributors)
    }

    @Test
    fun `energy is capped and bonus xp counts toward level`() {
        val d = base.copy(
            completions = listOf(done("light", today, energy = 7), done("heavy", today, energy = 7, xp = 40)),
            profile = base.profile.copy(bonusXp = 40),
        )
        val s = stats(d)
        assertEquals(10, s.energy)
        assertEquals(EnergyTier.RADIANT, s.tier)
        assertEquals(100, s.totalXp)
        assertEquals(2, s.level.level)
    }

    @Test
    fun `daily quests track todays progress and claims`() {
        val d = base.copy(
            completions = listOf(done("heavy", today)),
            profile = base.profile.copy(claimedQuests = listOf("$today:q_heavy", "${today - 1}:q_three")),
        )
        val quests = stats(d).quests.associateBy { it.id }
        assertEquals(1f / 3f, quests.getValue("q_three").progress, 0.001f)
        assertFalse("yesterday's claim does not carry over", quests.getValue("q_three").claimed)
        assertEquals(1f, quests.getValue("q_heavy").progress, 0.001f)
        assertTrue(quests.getValue("q_heavy").claimed)
        assertEquals(0f, quests.getValue("q_room").progress, 0.001f)
    }

    @Test
    fun `chores not due today are left out`() {
        val weekly = chore("weekly", frequency = Frequency.WEEKLY, start = today - 3)
        val s = stats(base.copy(chores = listOf(light, weekly)))
        assertEquals(listOf("light"), s.todayChores.map { it.chore.id })
    }

    @Test
    fun `health follows your chores for today`() {
        assertEquals(Health.SICK, stats(base).health)
        assertEquals(2, stats(base).yourDueToday)
        val half = stats(base.copy(completions = listOf(done("light", today))))
        assertEquals(Health.QUEASY, half.health)
        assertEquals(1, half.yourDoneToday)
        val all = stats(base.copy(completions = listOf(done("light", today), done("heavy", today))))
        assertEquals(Health.HEALTHY, all.health)
    }

    @Test
    fun `chores assigned to someone else do not make you sick`() {
        val samsChores = base.copy(chores = base.chores.map { it.copy(assigneeId = "sam") })
        val s = stats(samsChores)
        assertEquals(0, s.yourDueToday)
        assertEquals(Health.HEALTHY, s.health)
    }

    @Test
    fun `a chore someone else finished for you still counts`() {
        val s = stats(base.copy(completions = listOf(done("light", today, memberId = "sam"), done("heavy", today, memberId = "sam"))))
        assertEquals(Health.HEALTHY, s.health)
    }

    @Test
    fun `next reminder day skips days already done`() {
        val weekly = chore("weekly", frequency = Frequency.WEEKLY, start = today)
        assertEquals(today + 7, ReminderScheduler.nextDueDay(weekly, base, after = today))
        val doneNextWeek = base.copy(completions = listOf(done("weekly", today + 7)))
        assertEquals(today + 14, ReminderScheduler.nextDueDay(weekly, doneNextWeek, after = today))
    }

    @Test
    fun `a chore with no reminder time schedules nothing`() {
        assertNull(ReminderScheduler.nextOccurrence(light, base))
    }

    @Test
    fun `a reminder is scheduled in the future on a due day`() {
        val now = System.currentTimeMillis()
        val next = ReminderScheduler.nextOccurrence(light.copy(remindMinuteOfDay = 9 * 60, startEpochDay = 0), base, now)
        assertTrue(next != null && next.first > now)
        assertTrue("within the next two days for a daily chore", next!!.first - now <= 2 * 86_400_000L)
    }
}
