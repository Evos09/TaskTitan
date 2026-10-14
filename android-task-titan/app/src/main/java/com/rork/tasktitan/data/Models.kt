package com.rork.tasktitan.data

import kotlinx.serialization.Serializable

@Serializable
enum class RoomType(val label: String) {
    KITCHEN("Kitchen"),
    LIVING("Living room"),
    DINING("Dining room"),
    BEDROOM("Bedroom"),
    BATHROOM("Bathroom"),
    LAUNDRY("Laundry"),
    TOY("Toy room"),
    OFFICE("Home office"),
    GARAGE("Garage"),
    CUSTOM("Custom room"),
}

@Serializable
enum class ChoreKind { DISHES, TOYS, LAUNDRY, FLOOR, TRASH, SURFACES, BED, PLANTS, GENERAL }

@Serializable
enum class Frequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    CUSTOM("Every few days"),
}

@Serializable
enum class HomeSize(val label: String) { STUDIO("Studio"), ONE("1BR"), TWO("2BR"), THREE("3BR"), FOUR("4BR+") }

@Serializable
data class Room(
    val id: String,
    val name: String,
    val type: RoomType,
    val floor: Int,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

@Serializable
data class Member(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val isYou: Boolean = false,
    val availableDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
)

@Serializable
data class ChecklistItem(val id: String, val text: String)

@Serializable
data class Chore(
    val id: String,
    val title: String,
    val roomId: String,
    val kind: ChoreKind,
    val weight: Int,
    val frequency: Frequency = Frequency.DAILY,
    val intervalDays: Int = 1,
    val startEpochDay: Long,
    val assigneeId: String? = null,
    val rotate: Boolean = false,
    val autoAssign: Boolean = false,
    val timerMinutes: Int? = null,
    val needsConfirm: Boolean = false,
    val checklist: List<ChecklistItem> = emptyList(),
    val notes: String = "",
    /** Local minute-of-day (0–1439) for the gentle reminder alarm; null = off. */
    val remindMinuteOfDay: Int? = null,
)

@Serializable
data class Completion(
    val id: String,
    val choreId: String,
    val epochDay: Long,
    val memberId: String,
    val xp: Int,
    val coins: Int,
    val energy: Int,
    val confirmed: Boolean = true,
    val atMillis: Long = 0L,
)

@Serializable
data class Memory(
    val id: String,
    val roomId: String,
    val roomName: String,
    val epochDay: Long,
    val messKinds: List<ChoreKind>,
)

@Serializable
data class AvatarLook(
    /** "boy" or "girl"; picks the character model and its default hair. */
    val style: String = "boy",
    val body: Int = 1,
    val skin: Int = 2,
    val hairColor: Int = 1,
    val hair: String = "hair_bob",
    val outfit: String = "outfit_tangerine",
    val shoes: String = "shoes_sky",
    val accessory: String? = null,
    val pet: String? = null,
    val aura: String? = null,
)

@Serializable
data class Profile(
    val name: String = "You",
    val coins: Int = 50,
    val bonusXp: Int = 0,
    val owned: List<String> = listOf("hair_bob", "hair_short", "outfit_tangerine", "outfit_sky", "shoes_sky", "shoes_white"),
    val look: AvatarLook = AvatarLook(),
    val claimedQuests: List<String> = emptyList(),
    val claimedAchievements: List<String> = emptyList(),
    val equippedDecor: List<String> = emptyList(),
    val lastSeenLevel: Int = 1,
)

@Serializable
data class AppSettings(
    val consentGiven: Boolean = false,
    val ageGroup: String = "adult",
    val parentApproved: Boolean = false,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val largeText: Boolean = false,
    val shareAvatar: Boolean = false,
    val shareHouse: Boolean = false,
    val shareActivity: Boolean = false,
    val shareProgress: Boolean = false,
    val interHouseholdChallenge: Boolean = false,
    val reminders: Boolean = true,
    /** Epoch day until which reminder alarms are paused; 0 = not paused. Auto-expires. */
    val reminderPausedUntil: Long = 0L,
)

@Serializable
data class AppData(
    val version: Int = 1,
    val setupDone: Boolean = false,
    val householdName: String = "Our Home",
    val inviteCode: String = "",
    val homeSize: HomeSize = HomeSize.TWO,
    val floors: Int = 1,
    val rooms: List<Room> = emptyList(),
    val members: List<Member> = emptyList(),
    val chores: List<Chore> = emptyList(),
    val completions: List<Completion> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val profile: Profile = Profile(),
    val settings: AppSettings = AppSettings(),
    val weeklyGoal: Int = 40,
    val isPro: Boolean = false,
)
