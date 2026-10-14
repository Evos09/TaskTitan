package com.rork.tasktitan.data

import java.util.UUID

/** Positive-only energy tiers. The avatar starts each day at Neutral and only climbs. */
enum class EnergyTier(val label: String, val min: Int) {
    NEUTRAL("Neutral", 0),
    ENERGIZED("Energized", 1),
    THRIVING("Thriving", 4),
    RADIANT("Radiant", 8),
}

/** How the avatar feels today, set by whether the chores that are yours today are done. */
enum class Health(val label: String) {
    HEALTHY("Healthy"),
    QUEASY("Feeling sick"),
    SICK("Sick"),
}

data class Reward(val xp: Int, val coins: Int, val energy: Int)

object GameRules {
    const val MAX_ENERGY: Int = 10
    const val MAX_FLOORS: Int = 3
    const val GRID_W: Int = 12
    const val GRID_H: Int = 9

    fun newId(): String = UUID.randomUUID().toString().take(12)

    fun weightLabel(weight: Int): String = when (weight) {
        1 -> "Light"
        2 -> "Normal"
        3 -> "Heavy"
        else -> "Big · $weight"
    }

    /** XP, coins, and energy scale linearly with effort weight (1–5). */
    fun reward(weight: Int): Reward {
        val w = weight.coerceIn(1, 5)
        return Reward(xp = 10 + 10 * w, coins = 4 * w, energy = w)
    }

    fun xpForLevel(level: Int): Int = 100 + (level - 1) * 60

    data class LevelInfo(val level: Int, val xpInto: Int, val xpNeeded: Int)

    fun levelInfo(totalXp: Int): LevelInfo {
        var level = 1
        var remaining = totalXp
        while (remaining >= xpForLevel(level)) {
            remaining -= xpForLevel(level)
            level++
        }
        return LevelInfo(level, remaining, xpForLevel(level))
    }

    fun tier(energy: Int): EnergyTier = EnergyTier.entries.last { energy >= it.min }

    /** Healthy with every chore of yours done (or none due), sick when fewer than half are done. */
    fun health(due: Int, done: Int): Health = when {
        due <= 0 || done >= due -> Health.HEALTHY
        done * 2 >= due -> Health.QUEASY
        else -> Health.SICK
    }

    fun isDue(chore: Chore, day: Long): Boolean {
        if (day < chore.startEpochDay) return false
        val diff = day - chore.startEpochDay
        return when (chore.frequency) {
            Frequency.DAILY -> true
            Frequency.WEEKLY -> diff % 7 == 0L
            Frequency.MONTHLY -> DateUtil.dayOfMonth(day) ==
                DateUtil.dayOfMonth(chore.startEpochDay).coerceAtMost(DateUtil.lengthOfMonth(day))
            Frequency.CUSTOM -> diff % chore.intervalDays.coerceAtLeast(1) == 0L
        }
    }

    fun streak(completions: List<Completion>, memberId: String, today: Long): Int {
        val days = completions.filter { it.memberId == memberId && it.confirmed }.map { it.epochDay }.toSet()
        var cursor = if (today in days) today else today - 1
        var count = 0
        while (cursor in days) {
            count++
            cursor--
        }
        return count
    }

    /**
     * Consecutive weeks (ending at [today]'s week) where the household reached its
     * shared weekly goal. The current week counts once the goal is met and never
     * breaks the streak while still in progress — only a fully missed past week does.
     */
    fun weeklyStreak(completions: List<Completion>, goal: Int, today: Long): Int {
        val days = completions.filter { it.confirmed }.map { it.epochDay }
        fun met(weekStart: Long): Boolean = days.count { it in weekStart..weekStart + 6 } >= goal
        var week = DateUtil.weekStart(today)
        var count = 0
        if (met(week)) count++
        week -= 7
        while (met(week)) {
            count++
            week -= 7
        }
        return count
    }

    fun conditionLabel(percent: Int): String = when {
        percent >= 85 -> "Sparkling"
        percent >= 60 -> "Tidy"
        percent >= 25 -> "Cozy"
        else -> "Fresh start"
    }

    fun nextFreeSpot(rooms: List<Room>, floor: Int, w: Int, h: Int): Pair<Int, Int>? {
        for (y in 0..GRID_H - h) for (x in 0..GRID_W - w) {
            val overlaps = rooms.any { it.floor == floor && x < it.x + it.w && x + w > it.x && y < it.y + it.h && y + h > it.y }
            if (!overlaps) return x to y
        }
        return null
    }

    fun overlaps(rooms: List<Room>, candidate: Room): Boolean = rooms.any {
        it.id != candidate.id && it.floor == candidate.floor &&
            candidate.x < it.x + it.w && candidate.x + candidate.w > it.x &&
            candidate.y < it.y + it.h && candidate.y + candidate.h > it.y
    }

    /** Starter floor plans for the 30-second setup. */
    fun layoutFor(size: HomeSize): List<Room> {
        fun r(type: RoomType, floor: Int, x: Int, y: Int, w: Int, h: Int, name: String = type.label) =
            Room(newId(), name, type, floor, x, y, w, h)
        return when (size) {
            HomeSize.STUDIO -> listOf(
                r(RoomType.LIVING, 0, 0, 0, 6, 5, "Studio"),
                r(RoomType.KITCHEN, 0, 6, 0, 4, 3),
                r(RoomType.BATHROOM, 0, 6, 3, 4, 2),
            )
            HomeSize.ONE -> listOf(
                r(RoomType.KITCHEN, 0, 0, 0, 4, 4),
                r(RoomType.LIVING, 0, 4, 0, 5, 4),
                r(RoomType.BEDROOM, 0, 0, 4, 5, 4),
                r(RoomType.BATHROOM, 0, 5, 4, 4, 3),
            )
            HomeSize.TWO -> listOf(
                r(RoomType.KITCHEN, 0, 0, 0, 4, 4),
                r(RoomType.LIVING, 0, 4, 0, 5, 4),
                r(RoomType.TOY, 0, 9, 0, 3, 4),
                r(RoomType.BEDROOM, 0, 0, 4, 4, 4),
                r(RoomType.BEDROOM, 0, 4, 4, 4, 4, "Kids room"),
                r(RoomType.BATHROOM, 0, 8, 4, 4, 3),
            )
            HomeSize.THREE -> listOf(
                r(RoomType.KITCHEN, 0, 0, 0, 4, 4),
                r(RoomType.LIVING, 0, 4, 0, 5, 4),
                r(RoomType.DINING, 0, 9, 0, 3, 4),
                r(RoomType.LAUNDRY, 0, 0, 4, 3, 3),
                r(RoomType.BATHROOM, 0, 3, 4, 3, 3),
                r(RoomType.BEDROOM, 1, 0, 0, 4, 4),
                r(RoomType.BEDROOM, 1, 4, 0, 4, 4, "Bedroom 2"),
                r(RoomType.BEDROOM, 1, 8, 0, 4, 4, "Bedroom 3"),
                r(RoomType.BATHROOM, 1, 0, 4, 4, 3),
            )
            HomeSize.FOUR -> listOf(
                r(RoomType.KITCHEN, 0, 0, 0, 4, 4),
                r(RoomType.LIVING, 0, 4, 0, 5, 4),
                r(RoomType.DINING, 0, 9, 0, 3, 4),
                r(RoomType.LAUNDRY, 0, 0, 4, 3, 3),
                r(RoomType.GARAGE, 0, 3, 4, 5, 4),
                r(RoomType.BEDROOM, 1, 0, 0, 4, 4),
                r(RoomType.BEDROOM, 1, 4, 0, 4, 4, "Bedroom 2"),
                r(RoomType.TOY, 1, 8, 0, 4, 4),
                r(RoomType.BATHROOM, 1, 0, 4, 4, 3),
                r(RoomType.BEDROOM, 2, 0, 0, 4, 4, "Bedroom 3"),
                r(RoomType.BEDROOM, 2, 4, 0, 4, 4, "Bedroom 4"),
                r(RoomType.OFFICE, 2, 8, 0, 4, 4),
            )
        }
    }

    fun inviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
