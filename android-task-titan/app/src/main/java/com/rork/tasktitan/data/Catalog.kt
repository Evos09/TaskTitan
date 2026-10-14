package com.rork.tasktitan.data

/** A reusable chore preset offered in the template picker and at setup. */
data class ChoreTemplate(
    val title: String,
    val roomType: RoomType,
    val kind: ChoreKind,
    val weight: Int,
    val frequency: Frequency,
    val intervalDays: Int = 1,
    val checklist: List<String> = emptyList(),
)

enum class ItemCategory(val label: String) {
    HAIR("Hair"), OUTFIT("Outfits"), SHOES("Shoes"), ACCESSORY("Extras"), PET("Pets"), AURA("Auras"), DECOR("Decor")
}

/** A cosmetic unlock. [colorHex] drives its 3D appearance. */
data class Cosmetic(
    val id: String,
    val name: String,
    val category: ItemCategory,
    val unlockLevel: Int,
    val price: Int,
    val colorHex: Long,
    val pro: Boolean = false,
    val seasonal: Boolean = false,
)

data class Achievement(val id: String, val title: String, val detail: String, val coins: Int)

data class SeasonEvent(val name: String, val reward: String, val decorId: String, val tintHex: Long)

data class DailyQuest(val id: String, val title: String, val coins: Int, val xp: Int)

object Catalog {
    val skinTones: List<Long> = listOf(0xFFFFE0C7, 0xFFF6C9A0, 0xFFE3A777, 0xFFC68650, 0xFF95603A, 0xFF5F3B24)
    val hairColors: List<Long> = listOf(0xFF2B1B12, 0xFF6B3E1F, 0xFFC98A3C, 0xFFF2D27A, 0xFFE0573E, 0xFF7A5CFF)
    val memberColors: List<Long> = listOf(0xFFFF6FB5, 0xFF2BB5FF, 0xFF34C46B, 0xFFFF8A1F, 0xFF8E6CFF, 0xFFFFC21A)
    val bodyLabels: List<String> = listOf("Petite", "Classic", "Sturdy")

    val roomColors: Map<RoomType, Long> = mapOf(
        RoomType.KITCHEN to 0xFFFFE3C6,
        RoomType.LIVING to 0xFFD8EEFF,
        RoomType.DINING to 0xFFDDF5DA,
        RoomType.BEDROOM to 0xFFE9E0FF,
        RoomType.BATHROOM to 0xFFD3F3F6,
        RoomType.LAUNDRY to 0xFFEDE3FA,
        RoomType.TOY to 0xFFFFDDEC,
        RoomType.OFFICE to 0xFFFFEFD2,
        RoomType.GARAGE to 0xFFE6E8EC,
        RoomType.CUSTOM to 0xFFF1EDE6,
    )

    val roomAccents: Map<RoomType, Long> = mapOf(
        RoomType.KITCHEN to 0xFFE8720C,
        RoomType.LIVING to 0xFF1C8FD6,
        RoomType.DINING to 0xFF2E9A44,
        RoomType.BEDROOM to 0xFF7354D9,
        RoomType.BATHROOM to 0xFF0F94A3,
        RoomType.LAUNDRY to 0xFF8B55C9,
        RoomType.TOY to 0xFFE0418F,
        RoomType.OFFICE to 0xFFC27A0A,
        RoomType.GARAGE to 0xFF5D6675,
        RoomType.CUSTOM to 0xFF8A6D4C,
    )

    val templates: List<ChoreTemplate> = listOf(
        ChoreTemplate("Wash dishes", RoomType.KITCHEN, ChoreKind.DISHES, 2, Frequency.DAILY, checklist = listOf("Wash or load every dish", "Wipe the sink", "Dry and put away")),
        ChoreTemplate("Wipe counters", RoomType.KITCHEN, ChoreKind.SURFACES, 1, Frequency.DAILY, checklist = listOf("Clear the counters", "Spray and wipe")),
        ChoreTemplate("Take out trash", RoomType.KITCHEN, ChoreKind.TRASH, 1, Frequency.CUSTOM, 2, listOf("Tie the bag", "New bag in the bin")),
        ChoreTemplate("Mop kitchen floor", RoomType.KITCHEN, ChoreKind.FLOOR, 3, Frequency.WEEKLY, checklist = listOf("Sweep first", "Mop corners too")),
        ChoreTemplate("Vacuum living room", RoomType.LIVING, ChoreKind.FLOOR, 3, Frequency.WEEKLY, checklist = listOf("Move chairs aside", "Vacuum under the sofa", "Empty the canister")),
        ChoreTemplate("Tidy cushions & blankets", RoomType.LIVING, ChoreKind.GENERAL, 1, Frequency.DAILY),
        ChoreTemplate("Dust shelves", RoomType.LIVING, ChoreKind.SURFACES, 2, Frequency.WEEKLY),
        ChoreTemplate("Water plants", RoomType.LIVING, ChoreKind.PLANTS, 1, Frequency.CUSTOM, 3),
        ChoreTemplate("Clear the table", RoomType.DINING, ChoreKind.DISHES, 1, Frequency.DAILY),
        ChoreTemplate("Wipe table & chairs", RoomType.DINING, ChoreKind.SURFACES, 1, Frequency.DAILY),
        ChoreTemplate("Make the bed", RoomType.BEDROOM, ChoreKind.BED, 1, Frequency.DAILY, checklist = listOf("Straighten sheets", "Fluff pillows")),
        ChoreTemplate("Put clothes away", RoomType.BEDROOM, ChoreKind.LAUNDRY, 2, Frequency.DAILY),
        ChoreTemplate("Change the sheets", RoomType.BEDROOM, ChoreKind.BED, 3, Frequency.WEEKLY),
        ChoreTemplate("Clean sink & mirror", RoomType.BATHROOM, ChoreKind.SURFACES, 2, Frequency.WEEKLY),
        ChoreTemplate("Scrub the tub", RoomType.BATHROOM, ChoreKind.SURFACES, 4, Frequency.WEEKLY),
        ChoreTemplate("Clean the toilet", RoomType.BATHROOM, ChoreKind.SURFACES, 3, Frequency.WEEKLY),
        ChoreTemplate("Restock essentials", RoomType.BATHROOM, ChoreKind.GENERAL, 1, Frequency.WEEKLY),
        ChoreTemplate("Run a laundry load", RoomType.LAUNDRY, ChoreKind.LAUNDRY, 2, Frequency.CUSTOM, 2),
        ChoreTemplate("Fold laundry", RoomType.LAUNDRY, ChoreKind.LAUNDRY, 2, Frequency.CUSTOM, 2, listOf("Fold everything", "Sort by person")),
        ChoreTemplate("Pick up toys", RoomType.TOY, ChoreKind.TOYS, 1, Frequency.DAILY, checklist = listOf("All toys back in bins", "Books on the shelf", "Rug is clear")),
        ChoreTemplate("Sort toy bins", RoomType.TOY, ChoreKind.TOYS, 2, Frequency.WEEKLY),
        ChoreTemplate("Clear the desk", RoomType.OFFICE, ChoreKind.GENERAL, 1, Frequency.DAILY),
        ChoreTemplate("Empty paper bin", RoomType.OFFICE, ChoreKind.TRASH, 1, Frequency.WEEKLY),
        ChoreTemplate("Sweep the garage", RoomType.GARAGE, ChoreKind.FLOOR, 3, Frequency.MONTHLY),
        ChoreTemplate("Sort recycling", RoomType.GARAGE, ChoreKind.TRASH, 2, Frequency.WEEKLY),
        ChoreTemplate("Quick tidy", RoomType.CUSTOM, ChoreKind.GENERAL, 1, Frequency.DAILY),
    )

    val cosmetics: List<Cosmetic> = listOf(
        Cosmetic("hair_bob", "Bouncy Bob", ItemCategory.HAIR, 1, 0, 0),
        Cosmetic("hair_short", "Fresh Crop", ItemCategory.HAIR, 1, 0, 0),
        Cosmetic("hair_bun", "Top Bun", ItemCategory.HAIR, 3, 60, 0),
        Cosmetic("hair_long", "Long Waves", ItemCategory.HAIR, 5, 90, 0),
        Cosmetic("hair_spiky", "Spiky Hero", ItemCategory.HAIR, 8, 140, 0),
        Cosmetic("outfit_tangerine", "Tangerine Tee", ItemCategory.OUTFIT, 1, 0, 0xFFFF8A1F),
        Cosmetic("outfit_sky", "Sky Hoodie", ItemCategory.OUTFIT, 1, 0, 0xFF2BB5FF),
        Cosmetic("outfit_bubblegum", "Bubblegum Overalls", ItemCategory.OUTFIT, 2, 50, 0xFFFF6FB5),
        Cosmetic("outfit_sunny", "Sunny Hoodie", ItemCategory.OUTFIT, 4, 80, 0xFFFFC21A),
        Cosmetic("outfit_mint", "Mint Apron", ItemCategory.OUTFIT, 6, 110, 0xFF3ED598),
        Cosmetic("outfit_galaxy", "Galaxy Jumpsuit", ItemCategory.OUTFIT, 10, 220, 0xFF6C4BFF, pro = true),
        Cosmetic("shoes_sky", "Sky Kicks", ItemCategory.SHOES, 1, 0, 0xFF2BB5FF),
        Cosmetic("shoes_white", "Cloud Steps", ItemCategory.SHOES, 1, 0, 0xFFF5F5F5),
        Cosmetic("shoes_rocket", "Rocket Sneakers", ItemCategory.SHOES, 5, 100, 0xFFE8372C),
        Cosmetic("shoes_gold", "Golden Slippers", ItemCategory.SHOES, 12, 260, 0xFFFFC21A, pro = true),
        Cosmetic("acc_cap", "Pixel Cap", ItemCategory.ACCESSORY, 3, 70, 0xFFFF6FB5),
        Cosmetic("acc_glasses", "Round Specs", ItemCategory.ACCESSORY, 4, 70, 0xFF2B2B3A),
        Cosmetic("acc_crown", "Tiny Crown", ItemCategory.ACCESSORY, 15, 300, 0xFFFFC21A),
        Cosmetic("pet_corgi", "Corgi Pal", ItemCategory.PET, 6, 150, 0xFFF0A04B),
        Cosmetic("pet_cat", "Cloud Cat", ItemCategory.PET, 9, 180, 0xFFEDEDF2),
        Cosmetic("pet_bunny", "Bun Bun", ItemCategory.PET, 14, 240, 0xFFFFD1E3, pro = true),
        Cosmetic("aura_spark", "Spark Ring", ItemCategory.AURA, 2, 40, 0xFFFFC21A),
        Cosmetic("aura_sky", "Breeze Ring", ItemCategory.AURA, 7, 120, 0xFF2BB5FF),
        Cosmetic("aura_rainbow", "Rainbow Halo", ItemCategory.AURA, 13, 260, 0xFFFF6FB5, pro = true),
        Cosmetic("decor_plant", "Leafy Friend", ItemCategory.DECOR, 2, 40, 0xFF3BB55C),
        Cosmetic("decor_lamp", "Glow Lamp", ItemCategory.DECOR, 4, 60, 0xFFFFE08A),
        Cosmetic("decor_rug", "Cozy Rug", ItemCategory.DECOR, 5, 90, 0xFFFF8A1F),
        Cosmetic("decor_pumpkin", "Autumn Pumpkin", ItemCategory.DECOR, 1, 0, 0xFFFF7A1A, seasonal = true),
        Cosmetic("decor_snowman", "Snow Buddy", ItemCategory.DECOR, 1, 0, 0xFFFFFFFF, seasonal = true),
        Cosmetic("decor_tulips", "Spring Tulips", ItemCategory.DECOR, 1, 0, 0xFFFF6FB5, seasonal = true),
        Cosmetic("decor_beachball", "Beach Ball", ItemCategory.DECOR, 1, 0, 0xFF2BB5FF, seasonal = true),
        Cosmetic("decor_lights", "Star Lights", ItemCategory.DECOR, 8, 150, 0xFFFFE45C, pro = true),
    )

    val achievements: List<Achievement> = listOf(
        Achievement("first", "First sparkle", "Finish your very first chore", 20),
        Achievement("ten", "Getting going", "Finish 10 chores", 40),
        Achievement("fifty", "Home hero", "Finish 50 chores", 120),
        Achievement("heavy", "Heavy lifter", "Finish 5 Heavy-or-harder chores", 60),
        Achievement("streak3", "Warming up", "Reach a 3-day streak", 30),
        Achievement("streak7", "Week of wins", "Reach a 7-day streak", 80),
        Achievement("builder", "Architect", "Design a home with 6+ rooms", 50),
        Achievement("tower", "Three stories", "Build all three floors", 60),
    )

    val dailyQuests: List<DailyQuest> = listOf(
        DailyQuest("q_three", "Finish 3 chores today", 25, 30),
        DailyQuest("q_heavy", "Finish one Heavy chore", 20, 25),
        DailyQuest("q_room", "Make one room sparkle", 15, 20),
    )

    /** Seasonal event for a calendar month (0-based). */
    fun seasonFor(month: Int): SeasonEvent = when (month) {
        11, 0, 1 -> SeasonEvent("Winter Glow", "Unlock the snow buddy decor", "decor_snowman", 0xFFD8EEFF)
        2, 3, 4 -> SeasonEvent("Spring Bloom", "Unlock spring tulips", "decor_tulips", 0xFFFFE3F0)
        5, 6, 7 -> SeasonEvent("Summer Splash", "Unlock the beach ball", "decor_beachball", 0xFFD3F3F6)
        else -> SeasonEvent("Autumn Leaves", "Unlock pumpkin decor", "decor_pumpkin", 0xFFFFE9D2)
    }

    fun cosmetic(id: String?): Cosmetic? = cosmetics.firstOrNull { it.id == id }

    fun templatesFor(type: RoomType): List<ChoreTemplate> = templates.filter { it.roomType == type }

    /** Picks a mess kind from free-text when the user writes a custom chore. */
    fun guessKind(title: String, roomType: RoomType?): ChoreKind {
        val t = title.lowercase()
        return when {
            listOf("dish", "plate", "table").any { it in t } -> ChoreKind.DISHES
            listOf("toy", "lego", "doll").any { it in t } -> ChoreKind.TOYS
            listOf("laundry", "fold", "cloth", "wash clothes").any { it in t } -> ChoreKind.LAUNDRY
            listOf("vacuum", "sweep", "mop", "floor").any { it in t } -> ChoreKind.FLOOR
            listOf("trash", "garbage", "bin", "recycl").any { it in t } -> ChoreKind.TRASH
            listOf("bed", "sheet").any { it in t } -> ChoreKind.BED
            listOf("plant", "water").any { it in t } -> ChoreKind.PLANTS
            listOf("wipe", "dust", "scrub", "clean").any { it in t } -> ChoreKind.SURFACES
            roomType == RoomType.TOY -> ChoreKind.TOYS
            roomType == RoomType.LAUNDRY -> ChoreKind.LAUNDRY
            roomType == RoomType.KITCHEN -> ChoreKind.DISHES
            else -> ChoreKind.GENERAL
        }
    }
}
