package com.rork.tasktitan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Epoch day for a calendar date (month is 1-based). */
fun day(year: Int, month: Int, dayOfMonth: Int): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month - 1, dayOfMonth)
    }.timeInMillis / 86_400_000L

fun chore(
    id: String = "c",
    weight: Int = 1,
    frequency: Frequency = Frequency.DAILY,
    intervalDays: Int = 1,
    start: Long = 0L,
    roomId: String = "r",
) = Chore(id = id, title = id, roomId = roomId, kind = ChoreKind.GENERAL, weight = weight, frequency = frequency, intervalDays = intervalDays, startEpochDay = start)

fun done(choreId: String, epochDay: Long, memberId: String = "me", confirmed: Boolean = true, xp: Int = 20, coins: Int = 4, energy: Int = 1) =
    Completion(id = "$choreId@$epochDay@$memberId", choreId = choreId, epochDay = epochDay, memberId = memberId, xp = xp, coins = coins, energy = energy, confirmed = confirmed)

class GameRulesTest {
    @Test
    fun `reward scales with weight and clamps to 1-5`() {
        assertEquals(Reward(xp = 20, coins = 4, energy = 1), GameRules.reward(1))
        assertEquals(Reward(xp = 60, coins = 20, energy = 5), GameRules.reward(5))
        assertEquals(GameRules.reward(1), GameRules.reward(0))
        assertEquals(GameRules.reward(5), GameRules.reward(9))
    }

    @Test
    fun `levels need 60 more xp each time`() {
        assertEquals(GameRules.LevelInfo(1, 0, 100), GameRules.levelInfo(0))
        assertEquals(GameRules.LevelInfo(1, 99, 100), GameRules.levelInfo(99))
        assertEquals(GameRules.LevelInfo(2, 0, 160), GameRules.levelInfo(100))
        assertEquals(GameRules.LevelInfo(2, 159, 160), GameRules.levelInfo(259))
        assertEquals(GameRules.LevelInfo(3, 0, 220), GameRules.levelInfo(260))
    }

    @Test
    fun `energy tiers switch at their thresholds`() {
        assertEquals(EnergyTier.NEUTRAL, GameRules.tier(0))
        assertEquals(EnergyTier.ENERGIZED, GameRules.tier(1))
        assertEquals(EnergyTier.ENERGIZED, GameRules.tier(3))
        assertEquals(EnergyTier.THRIVING, GameRules.tier(4))
        assertEquals(EnergyTier.THRIVING, GameRules.tier(7))
        assertEquals(EnergyTier.RADIANT, GameRules.tier(8))
        assertEquals(EnergyTier.RADIANT, GameRules.tier(GameRules.MAX_ENERGY))
    }

    @Test
    fun `condition labels switch at their thresholds`() {
        assertEquals("Fresh start", GameRules.conditionLabel(0))
        assertEquals("Fresh start", GameRules.conditionLabel(24))
        assertEquals("Cozy", GameRules.conditionLabel(25))
        assertEquals("Tidy", GameRules.conditionLabel(60))
        assertEquals("Sparkling", GameRules.conditionLabel(85))
        assertEquals("Sparkling", GameRules.conditionLabel(100))
    }

    @Test
    fun `nothing is due before its start day`() {
        val c = chore(start = 100)
        assertFalse(GameRules.isDue(c, 99))
        assertTrue(GameRules.isDue(c, 100))
        assertTrue(GameRules.isDue(c, 101))
    }

    @Test
    fun `weekly chores are due every seventh day`() {
        val c = chore(frequency = Frequency.WEEKLY, start = 100)
        assertTrue(GameRules.isDue(c, 100))
        assertFalse(GameRules.isDue(c, 106))
        assertTrue(GameRules.isDue(c, 107))
        assertTrue(GameRules.isDue(c, 114))
    }

    @Test
    fun `custom interval chores repeat on their interval`() {
        val c = chore(frequency = Frequency.CUSTOM, intervalDays = 3, start = 100)
        assertTrue(GameRules.isDue(c, 100))
        assertFalse(GameRules.isDue(c, 101))
        assertTrue(GameRules.isDue(c, 103))
        assertFalse(GameRules.isDue(c, 104))
    }

    @Test
    fun `a zero custom interval behaves like daily instead of crashing`() {
        val c = chore(frequency = Frequency.CUSTOM, intervalDays = 0, start = 100)
        assertTrue(GameRules.isDue(c, 100))
        assertTrue(GameRules.isDue(c, 101))
    }

    @Test
    fun `monthly chores started on the 31st fall on the last day of shorter months`() {
        val c = chore(frequency = Frequency.MONTHLY, start = day(2025, 1, 31))
        assertTrue(GameRules.isDue(c, day(2025, 1, 31)))
        assertFalse(GameRules.isDue(c, day(2025, 2, 27)))
        assertTrue(GameRules.isDue(c, day(2025, 2, 28)))
        assertFalse(GameRules.isDue(c, day(2025, 3, 30)))
        assertTrue(GameRules.isDue(c, day(2025, 3, 31)))
        assertTrue(GameRules.isDue(c, day(2025, 4, 30)))
    }

    @Test
    fun `streak counts consecutive days ending today`() {
        val completions = listOf(done("c", 100), done("c", 99), done("c", 98), done("c", 96))
        assertEquals(3, GameRules.streak(completions, "me", 100))
    }

    @Test
    fun `streak survives until the end of a day with nothing done yet`() {
        val completions = listOf(done("c", 99), done("c", 98))
        assertEquals(2, GameRules.streak(completions, "me", 100))
        assertEquals(0, GameRules.streak(completions, "me", 101))
    }

    @Test
    fun `streak ignores unconfirmed completions and other members`() {
        val completions = listOf(done("c", 100, confirmed = false), done("c", 100, memberId = "sam"), done("c", 99))
        assertEquals(1, GameRules.streak(completions, "me", 100))
        assertEquals(1, GameRules.streak(completions, "sam", 100))
    }

    @Test
    fun `weekly streak counts back until a missed week`() {
        val wednesday = day(2025, 1, 22)
        val thisWeek = DateUtil.weekStart(wednesday)
        val completions = listOf(
            done("a", thisWeek), done("b", thisWeek + 1),
            done("a", thisWeek - 7), done("b", thisWeek - 1),
            // the week starting thisWeek - 14 is missed
            done("a", thisWeek - 21), done("b", thisWeek - 20),
        )
        assertEquals(2, GameRules.weeklyStreak(completions, goal = 2, today = wednesday))
    }

    @Test
    fun `an unfinished current week does not break the weekly streak`() {
        val wednesday = day(2025, 1, 22)
        val thisWeek = DateUtil.weekStart(wednesday)
        val completions = listOf(done("a", thisWeek), done("a", thisWeek - 7), done("b", thisWeek - 6))
        assertEquals(1, GameRules.weeklyStreak(completions, goal = 2, today = wednesday))
    }

    @Test
    fun `rooms that only share an edge do not overlap`() {
        val a = Room("a", "A", RoomType.KITCHEN, 0, 0, 0, 4, 4)
        val beside = Room("b", "B", RoomType.LIVING, 0, 4, 0, 4, 4)
        val onTop = Room("c", "C", RoomType.LIVING, 0, 3, 3, 4, 4)
        val upstairs = onTop.copy(floor = 1)
        assertFalse(GameRules.overlaps(listOf(a), beside))
        assertTrue(GameRules.overlaps(listOf(a), onTop))
        assertFalse(GameRules.overlaps(listOf(a), upstairs))
        assertFalse("a room never overlaps itself", GameRules.overlaps(listOf(a), a.copy(x = 1)))
    }

    @Test
    fun `next free spot skips occupied cells and reports a full floor`() {
        assertEquals(0 to 0, GameRules.nextFreeSpot(emptyList(), 0, 3, 3))
        val kitchen = Room("a", "A", RoomType.KITCHEN, 0, 0, 0, 4, 4)
        assertEquals(4 to 0, GameRules.nextFreeSpot(listOf(kitchen), 0, 3, 3))
        val wholeFloor = Room("f", "F", RoomType.LIVING, 0, 0, 0, GameRules.GRID_W, GameRules.GRID_H)
        assertNull(GameRules.nextFreeSpot(listOf(wholeFloor), 0, 2, 2))
        assertEquals(0 to 0, GameRules.nextFreeSpot(listOf(wholeFloor), 1, 2, 2))
    }

    @Test
    fun `every starter layout fits the grid with no overlapping rooms`() {
        HomeSize.entries.forEach { size ->
            val rooms = GameRules.layoutFor(size)
            assertTrue("$size has rooms", rooms.isNotEmpty())
            rooms.forEach { r ->
                assertTrue("$size ${r.name} is inside the grid", r.x >= 0 && r.y >= 0 && r.x + r.w <= GameRules.GRID_W && r.y + r.h <= GameRules.GRID_H)
                assertTrue("$size ${r.name} is on a valid floor", r.floor in 0 until GameRules.MAX_FLOORS)
                assertFalse("$size ${r.name} overlaps another room", GameRules.overlaps(rooms, r))
            }
            assertEquals("$size room ids are unique", rooms.size, rooms.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `invite codes are six unambiguous characters`() {
        repeat(50) {
            val code = GameRules.inviteCode()
            assertEquals(6, code.length)
            assertTrue(code, code.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
        }
    }
}
