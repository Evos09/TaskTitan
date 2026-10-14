package com.rork.tasktitan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.tasktitan.data.AppData
import com.rork.tasktitan.data.AppSettings
import com.rork.tasktitan.data.AvatarLook
import com.rork.tasktitan.data.Catalog
import com.rork.tasktitan.data.ChecklistItem
import com.rork.tasktitan.data.Chore
import com.rork.tasktitan.data.ChoreTemplate
import com.rork.tasktitan.data.Completion
import com.rork.tasktitan.data.DateUtil
import com.rork.tasktitan.data.EnergyTier
import com.rork.tasktitan.data.GameRules
import com.rork.tasktitan.data.Health
import com.rork.tasktitan.data.HomeSize
import com.rork.tasktitan.data.ItemCategory
import com.rork.tasktitan.data.Member
import com.rork.tasktitan.data.Memory
import com.rork.tasktitan.data.Room
import com.rork.tasktitan.data.SeasonEvent
import com.rork.tasktitan.data.SecureStore
import com.rork.tasktitan.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A chore as it stands on a given day. */
data class ChoreStatus(
    val chore: Chore,
    val room: Room?,
    val completion: Completion?,
) {
    val isDone: Boolean get() = completion != null
    val awaitingConfirm: Boolean get() = completion != null && !completion.confirmed
}

data class QuestStatus(val id: String, val title: String, val coins: Int, val xp: Int, val progress: Float, val claimed: Boolean)

/** Everything screens need, derived from [AppData] for the current day. */
data class Stats(
    val today: Long,
    val you: Member?,
    val level: GameRules.LevelInfo,
    val totalXp: Int,
    val coins: Int,
    val energy: Int,
    val tier: EnergyTier,
    /** How the avatar feels: set by whether your chores due today are done. */
    val health: Health,
    val yourDueToday: Int,
    val yourDoneToday: Int,
    val streak: Int,
    val todayChores: List<ChoreStatus>,
    val waitingCount: Int,
    val houseCondition: Int,
    val roomCondition: Map<String, Int>,
    val householdWeek: Int,
    val weekStreak: Int,
    val yourWeek: Int,
    val yourTotal: Int,
    val sparklingDaysThisWeek: Int,
    val contributors: List<Member>,
    val quests: List<QuestStatus>,
    val season: SeasonEvent,
    val seasonProgress: Int,
    val seasonGoal: Int,
    val seasonDaysLeft: Int,
    val seasonUnlocked: Boolean,
    val unlockedAchievements: Set<String>,
)

data class Celebration(val title: String, val message: String, val big: Boolean = false, val id: Long = System.nanoTime())

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SecureStore(application)
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val dayTick = MutableStateFlow(DateUtil.today())

    /** choreId → time the chore was just completed; drives the room-tidying animation. */
    private val _recentClean = MutableStateFlow<Map<String, Long>>(emptyMap())
    val recentClean: StateFlow<Map<String, Long>> = _recentClean.asStateFlow()

    /** Room the avatar should stroll to and celebrate in. */
    private val _avatarFocus = MutableStateFlow<Pair<String, Long>?>(null)
    val avatarFocus: StateFlow<Pair<String, Long>?> = _avatarFocus.asStateFlow()

    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration.asStateFlow()

    val stats: StateFlow<Stats> = combine(_data, dayTick) { d, day -> computeStats(d, day) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, computeStats(AppData(), DateUtil.today()))

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = store.load()
            if (saved != null) _data.value = saved
            _loaded.value = true
            if (saved != null) ReminderScheduler.sync(getApplication(), saved)
        }
    }

    fun refreshDay() {
        dayTick.value = DateUtil.today()
        // Re-sync on return to the app: expired pauses resume and missed day rolls happen here.
        viewModelScope.launch(Dispatchers.IO) { ReminderScheduler.sync(getApplication(), _data.value) }
    }

    private fun mutate(block: (AppData) -> AppData) {
        _data.update(block)
        val snapshot = _data.value
        viewModelScope.launch(Dispatchers.IO) {
            store.save(snapshot)
            // Keep the gentle-reminder alarms in sync with the data and the Settings toggle.
            ReminderScheduler.sync(getApplication(), snapshot)
        }
    }

    fun dismissCelebration() {
        _celebration.value = null
    }

    // region Onboarding & house

    fun giveConsent(name: String, ageGroup: String, parentApproved: Boolean, style: String = "boy") = mutate { d ->
        val you = Member(GameRules.newId(), name.ifBlank { "You" }, 0, isYou = true)
        d.copy(
            members = listOf(you),
            inviteCode = GameRules.inviteCode(),
            profile = d.profile.copy(name = you.name, look = d.profile.look.copy(style = style)),
            settings = d.settings.copy(consentGiven = true, ageGroup = ageGroup, parentApproved = parentApproved),
        )
    }

    fun saveHouse(rooms: List<Room>, floors: Int, size: HomeSize) = mutate { d ->
        val ids = rooms.map { it.id }.toSet()
        val keptChores = d.chores.filter { it.roomId in ids }
        val seeded = if (!d.setupDone) starterChores(rooms, d.members.firstOrNull()?.id) else emptyList()
        d.copy(
            rooms = rooms,
            floors = floors.coerceIn(1, GameRules.MAX_FLOORS),
            homeSize = size,
            chores = keptChores + seeded,
            setupDone = true,
        )
    }

    private fun starterChores(rooms: List<Room>, youId: String?): List<Chore> {
        val today = DateUtil.today()
        return rooms.distinctBy { it.type }.mapNotNull { room ->
            Catalog.templatesFor(room.type).firstOrNull()?.let { fromTemplate(it, room, youId, today) }
        }.take(6)
    }

    fun fromTemplate(t: ChoreTemplate, room: Room, assignee: String?, start: Long = DateUtil.today()): Chore = Chore(
        id = GameRules.newId(),
        title = t.title,
        roomId = room.id,
        kind = t.kind,
        weight = t.weight,
        frequency = t.frequency,
        intervalDays = t.intervalDays,
        startEpochDay = start,
        assigneeId = assignee,
        checklist = t.checklist.map { ChecklistItem(GameRules.newId(), it) },
    )

    // endregion

    // region Chores

    fun saveChore(chore: Chore) = mutate { d ->
        val assigned = if (chore.autoAssign) chore.copy(assigneeId = leastBusy(d, chore.id)) else chore
        val exists = d.chores.any { it.id == chore.id }
        d.copy(chores = if (exists) d.chores.map { if (it.id == chore.id) assigned else it } else d.chores + assigned)
    }

    fun deleteChore(id: String) = mutate { d -> d.copy(chores = d.chores.filterNot { it.id == id }) }

    private fun leastBusy(d: AppData, excludeChore: String): String? {
        val weekday = DateUtil.dayOfWeek(DateUtil.today()) + 1
        val pool = d.members.filter { weekday in it.availableDays }.ifEmpty { d.members }
        return pool.minByOrNull { m -> d.chores.count { it.assigneeId == m.id && it.id != excludeChore } }?.id
    }

    private fun nextInRotation(d: AppData, chore: Chore): String? {
        if (d.members.isEmpty()) return chore.assigneeId
        val idx = d.members.indexOfFirst { it.id == chore.assigneeId }
        return d.members[(idx + 1).mod(d.members.size)].id
    }

    fun completeChore(choreId: String, memberId: String? = null) {
        val before = _data.value
        val day = DateUtil.today()
        val chore = before.chores.firstOrNull { it.id == choreId } ?: return
        if (before.completions.any { it.choreId == choreId && it.epochDay == day }) return
        val doer = memberId ?: chore.assigneeId?.takeIf { id -> before.members.any { it.id == id } } ?: you(before)?.id ?: return
        val isYou = before.members.firstOrNull { it.id == doer }?.isYou == true
        val reward = GameRules.reward(chore.weight)
        val streakNow = GameRules.streak(before.completions, doer, day)
        val firstToday = before.completions.none { it.memberId == doer && it.epochDay == day }
        val streakBonus = if (isYou && firstToday && streakNow >= 1) minOf(streakNow, 7) * 2 else 0
        val completion = Completion(
            id = GameRules.newId(), choreId = choreId, epochDay = day, memberId = doer,
            xp = reward.xp + streakBonus, coins = reward.coins, energy = reward.energy,
            confirmed = !chore.needsConfirm, atMillis = System.currentTimeMillis(),
        )
        val levelBefore = computeStats(before, day).level.level

        mutate { d ->
            var next = d.copy(completions = d.completions + completion)
            if (isYou && completion.confirmed) next = next.copy(profile = next.profile.copy(coins = next.profile.coins + reward.coins))
            val updatedChore = when {
                chore.rotate -> chore.copy(assigneeId = nextInRotation(next, chore))
                chore.autoAssign -> chore.copy(assigneeId = leastBusy(next, chore.id))
                else -> chore
            }
            next = next.copy(chores = next.chores.map { if (it.id == chore.id) updatedChore else it })
            val room = next.rooms.firstOrNull { it.id == chore.roomId }
            if (room != null) {
                val dueInRoom = next.chores.filter { it.roomId == room.id && GameRules.isDue(it, day) }
                val allDone = dueInRoom.all { c -> next.completions.any { it.choreId == c.id && it.epochDay == day } }
                val alreadyRemembered = next.memories.any { it.roomId == room.id && it.epochDay == day }
                if (allDone && !alreadyRemembered) {
                    next = next.copy(memories = (listOf(Memory(GameRules.newId(), room.id, room.name, day, dueInRoom.map { it.kind })) + next.memories).take(40))
                    _celebration.value = Celebration("${room.name} is sparkling!", "Saved to your house memories.")
                }
            }
            next
        }
        _recentClean.update { it + (choreId to System.currentTimeMillis()) }
        _avatarFocus.value = chore.roomId to System.currentTimeMillis()

        val levelAfter = computeStats(_data.value, day).level.level
        if (levelAfter > levelBefore) {
            _celebration.value = Celebration("Level $levelAfter!", "New closet items may be ready in Rewards.", big = true)
        }
    }

    fun undoCompletion(choreId: String) = mutate { d ->
        val day = DateUtil.today()
        val c = d.completions.firstOrNull { it.choreId == choreId && it.epochDay == day } ?: return@mutate d
        val wasYou = d.members.firstOrNull { it.id == c.memberId }?.isYou == true
        val coins = if (wasYou && c.confirmed) (d.profile.coins - c.coins).coerceAtLeast(0) else d.profile.coins
        d.copy(completions = d.completions - c, profile = d.profile.copy(coins = coins))
    }

    fun confirmCompletion(completionId: String) = mutate { d ->
        val c = d.completions.firstOrNull { it.id == completionId } ?: return@mutate d
        val wasYou = d.members.firstOrNull { it.id == c.memberId }?.isYou == true
        d.copy(
            completions = d.completions.map { if (it.id == completionId) it.copy(confirmed = true) else it },
            profile = if (wasYou) d.profile.copy(coins = d.profile.coins + c.coins) else d.profile,
        )
    }

    // endregion

    // region Household

    fun addMember(name: String) = mutate { d ->
        if (name.isBlank()) return@mutate d
        d.copy(members = d.members + Member(GameRules.newId(), name.trim(), d.members.size % Catalog.memberColors.size))
    }

    fun removeMember(id: String) = mutate { d ->
        val you = you(d)
        if (you?.id == id) return@mutate d
        d.copy(
            members = d.members.filterNot { it.id == id },
            chores = d.chores.map { if (it.assigneeId == id) it.copy(assigneeId = you?.id) else it },
        )
    }

    fun setWeeklyGoal(goal: Int) = mutate { it.copy(weeklyGoal = goal.coerceIn(5, 300)) }

    fun renameHousehold(name: String) = mutate { it.copy(householdName = name.ifBlank { "Our Home" }) }

    /** Master switch for gentle reminders; turning it on clears any pause. */
    fun setReminders(on: Boolean) = mutate { d ->
        d.copy(settings = d.settings.copy(reminders = on, reminderPausedUntil = if (on) 0L else d.settings.reminderPausedUntil))
    }

    /** Silence all reminder alarms for the next seven days; they resume automatically. */
    fun pauseRemindersForWeek() = mutate { d ->
        d.copy(settings = d.settings.copy(reminderPausedUntil = DateUtil.today() + 7))
    }

    /** End a reminder pause early. */
    fun resumeReminders() = mutate { d -> d.copy(settings = d.settings.copy(reminderPausedUntil = 0L)) }

    // endregion

    // region Rewards

    fun buy(itemId: String): String? {
        val item = Catalog.cosmetic(itemId) ?: return "Item not found"
        val d = _data.value
        val s = stats.value
        if (itemId in d.profile.owned) return null
        if (item.pro && !d.isPro) return "Part of Task Titan Pro"
        if (s.level.level < item.unlockLevel) return "Unlocks at level ${item.unlockLevel}"
        if (d.profile.coins < item.price) return "You need ${item.price - d.profile.coins} more coins"
        mutate { it.copy(profile = it.profile.copy(coins = it.profile.coins - item.price, owned = it.profile.owned + itemId)) }
        equip(itemId)
        return null
    }

    fun equip(itemId: String) = mutate { d ->
        val item = Catalog.cosmetic(itemId) ?: return@mutate d
        if (itemId !in d.profile.owned) return@mutate d
        val look = d.profile.look
        val p = d.profile
        val newProfile = when (item.category) {
            ItemCategory.HAIR -> p.copy(look = look.copy(hair = itemId))
            ItemCategory.OUTFIT -> p.copy(look = look.copy(outfit = itemId))
            ItemCategory.SHOES -> p.copy(look = look.copy(shoes = itemId))
            ItemCategory.ACCESSORY -> p.copy(look = look.copy(accessory = if (look.accessory == itemId) null else itemId))
            ItemCategory.PET -> p.copy(look = look.copy(pet = if (look.pet == itemId) null else itemId))
            ItemCategory.AURA -> p.copy(look = look.copy(aura = if (look.aura == itemId) null else itemId))
            ItemCategory.DECOR -> p.copy(equippedDecor = if (itemId in p.equippedDecor) p.equippedDecor - itemId else p.equippedDecor + itemId)
        }
        d.copy(profile = newProfile)
    }

    fun setLook(look: AvatarLook) = mutate { it.copy(profile = it.profile.copy(look = look)) }

    fun claimQuest(id: String) {
        val q = stats.value.quests.firstOrNull { it.id == id } ?: return
        if (q.claimed || q.progress < 1f) return
        val key = "${DateUtil.today()}:$id"
        mutate { d -> d.copy(profile = d.profile.copy(coins = d.profile.coins + q.coins, bonusXp = d.profile.bonusXp + q.xp, claimedQuests = (d.profile.claimedQuests + key).takeLast(60))) }
        _celebration.value = Celebration("Quest complete!", "+${q.coins} coins · +${q.xp} XP")
    }

    fun claimAchievement(id: String) {
        val a = Catalog.achievements.firstOrNull { it.id == id } ?: return
        val d = _data.value
        if (id in d.profile.claimedAchievements || id !in stats.value.unlockedAchievements) return
        mutate { it.copy(profile = it.profile.copy(coins = it.profile.coins + a.coins, claimedAchievements = it.profile.claimedAchievements + id)) }
        _celebration.value = Celebration(a.title, "Milestone reached! +${a.coins} coins", big = true)
    }

    fun claimSeason() {
        val s = stats.value
        if (!s.seasonUnlocked) return
        if (s.season.decorId in _data.value.profile.owned) return
        mutate { d -> d.copy(profile = d.profile.copy(owned = d.profile.owned + s.season.decorId, equippedDecor = d.profile.equippedDecor + s.season.decorId)) }
        _celebration.value = Celebration("${s.season.name} reward!", "${Catalog.cosmetic(s.season.decorId)?.name} now decorates your home.", big = true)
    }

    // endregion

    // region Settings & privacy

    fun updateSettings(block: (AppSettings) -> AppSettings) = mutate { it.copy(settings = block(it.settings)) }

    fun exportJson(): String = store.export(_data.value)

    fun deleteEverything() {
        val chores = _data.value.chores
        store.wipe()
        _data.value = AppData()
        _recentClean.value = emptyMap()
        chores.forEach { ReminderScheduler.cancel(getApplication(), it.id) }
    }

    // endregion

    companion object {
        fun you(d: AppData): Member? = d.members.firstOrNull { it.isYou }

        fun computeStats(d: AppData, today: Long): Stats {
            val you = you(d)
            val validIds = d.chores.map { it.id }.toSet()
            val confirmed = d.completions.filter { it.confirmed }
            val yours = confirmed.filter { it.memberId == you?.id }
            val totalXp = yours.sumOf { it.xp } + d.profile.bonusXp
            val level = GameRules.levelInfo(totalXp)
            val energy = yours.filter { it.epochDay == today }.sumOf { it.energy }.coerceAtMost(GameRules.MAX_ENERGY)
            val roomsById = d.rooms.associateBy { it.id }
            val todayCompletions = d.completions.filter { it.epochDay == today }.associateBy { it.choreId }
            val todayChores = d.chores.filter { GameRules.isDue(it, today) }
                .map { ChoreStatus(it, roomsById[it.roomId], todayCompletions[it.id]) }
                .sortedWith(compareBy<ChoreStatus> { it.isDone }.thenByDescending { it.chore.weight })
            // Chores assigned to you, or to nobody, count toward your health.
            val yourDue = todayChores.filter { it.chore.assigneeId == null || it.chore.assigneeId == you?.id }
            val yourDone = yourDue.count { it.isDone }
            val dueCount = todayChores.size
            val doneCount = todayChores.count { it.isDone }
            val condition = if (dueCount == 0) 100 else doneCount * 100 / dueCount
            val roomCondition = d.rooms.associate { r ->
                val due = todayChores.filter { it.chore.roomId == r.id }
                r.id to if (due.isEmpty()) 100 else due.count { it.isDone } * 100 / due.size
            }
            val weekStart = DateUtil.weekStart(today)
            val weekCompletions = confirmed.filter { it.epochDay in weekStart..today }
            val sparklingDays = (weekStart..today).count { day ->
                val due = d.chores.filter { GameRules.isDue(it, day) }
                due.isNotEmpty() && due.all { c -> d.completions.any { it.choreId == c.id && it.epochDay == day } }
            }
            val contributors = d.members.filter { m -> weekCompletions.any { it.memberId == m.id } }

            val yourToday = yours.filter { it.epochDay == today && it.choreId in validIds }
            val choresById = d.chores.associateBy { it.id }
            val claimed = d.profile.claimedQuests.filter { it.startsWith("$today:") }.map { it.substringAfter(":") }.toSet()
            val quests = Catalog.dailyQuests.map { q ->
                val progress = when (q.id) {
                    "q_three" -> yourToday.size / 3f
                    "q_heavy" -> if (yourToday.any { (choresById[it.choreId]?.weight ?: 0) >= 3 }) 1f else 0f
                    else -> if (d.memories.any { it.epochDay == today }) 1f else 0f
                }
                QuestStatus(q.id, q.title, q.coins, q.xp, progress.coerceAtMost(1f), q.id in claimed)
            }

            val month = DateUtil.month(today)
            val season = Catalog.seasonFor(month)
            val monthStart = today - (DateUtil.dayOfMonth(today) - 1)
            val seasonProgress = confirmed.count { it.epochDay in monthStart..today }
            val seasonGoal = 25
            val daysLeft = DateUtil.lengthOfMonth(today) - DateUtil.dayOfMonth(today)

            val streak = you?.let { GameRules.streak(d.completions, it.id, today) } ?: 0
            val heavyCount = yours.count { (choresById[it.choreId]?.weight ?: 0) >= 3 }
            val unlocked = buildSet {
                if (yours.isNotEmpty()) add("first")
                if (yours.size >= 10) add("ten")
                if (yours.size >= 50) add("fifty")
                if (heavyCount >= 5) add("heavy")
                if (streak >= 3) add("streak3")
                if (streak >= 7) add("streak7")
                if (d.rooms.size >= 6) add("builder")
                if (d.floors >= 3 && (0..2).all { f -> d.rooms.any { it.floor == f } }) add("tower")
            }

            return Stats(
                today = today, you = you, level = level, totalXp = totalXp, coins = d.profile.coins,
                energy = energy, tier = GameRules.tier(energy), streak = streak,
                health = if (you == null) Health.HEALTHY else GameRules.health(yourDue.size, yourDone),
                yourDueToday = yourDue.size, yourDoneToday = yourDone,
                todayChores = todayChores, waitingCount = dueCount - doneCount,
                houseCondition = condition, roomCondition = roomCondition,
                householdWeek = weekCompletions.size,
                weekStreak = GameRules.weeklyStreak(confirmed, d.weeklyGoal, today),
                yourWeek = weekCompletions.count { it.memberId == you?.id },
                yourTotal = yours.size, sparklingDaysThisWeek = sparklingDays, contributors = contributors,
                quests = quests, season = season, seasonProgress = seasonProgress, seasonGoal = seasonGoal,
                seasonDaysLeft = daysLeft, seasonUnlocked = seasonProgress >= seasonGoal,
                unlockedAchievements = unlocked,
            )
        }
    }
}
