package com.example.threat;

/**
 * Single threat-scale table: which day turns what on, and starting values at day 0.
 * Edit here without opening mob classes. FIXED/GROWING tags come from mobs.txt.
 *
 * <p>How to read:
 * <ul>
 *   <li><b>"From day N"</b> — mechanic is off before N; from N onward chance is exactly as in the mob class
 *       (marked FIXED there). Moon does not affect these.</li>
 *   <li><b>"Start value"</b> — chance at world day 0. By day 100 it reaches the number in the mob class
 *       (now labeled "end of scale"). Along the way, moon shifts the chance (see {@link ThreatScale}).</li>
 * </ul>
 */
public final class ThreatTable {

	// ===================== FROM-DAY THRESHOLDS =====================

	/** First siege. Gap until the next — in {@code SiegeNight}. */
	public static final int SIEGE_FIRST_DAY = 25;

	/**
	 * From this day a siege may be illager-themed (vindicators, pillagers, evokers, ravagers).
	 * Before that, all sieges are undead and monsters. Illager siege chance — in {@code SiegeComposition}.
	 */
	public static final int SIEGE_ILLAGER_DAY = 40;

	/** Skeletons with Punch bow enchant V–VI ("stronger than vanilla"). None before this day. */
	public static final int STRONG_BOW_DAY = 20;

	/** Zombies seek light and break it. */
	public static final int LIGHT_SEEK_DAY = 40;

	/** Zombies break blocks toward the player. Break speed does not scale by day. Siege zombies too from this day. */
	public static final int BLOCK_BREAK_DAY = 40;

	/** Zombie sorcerer. */
	public static final int SORCERER_DAY = 40;

	/** Zombie Excalibur: sword with Sharpness 20. Chance in {@code ZombieExcalibur}, does not grow by day. */
	public static final int EXCALIBUR_DAY = 40;

	/**
	 * Spiky: normal zombie or skeleton, one armor piece deals 7–8 hearts through armor.
	 * Chance in {@code Spiky}, does not grow by day.
	 */
	public static final int SPIKY_DAY = 40;

	/** Rotten overseer. */
	public static final int OVERSEER_DAY = 60;

	/** Skeleton captain with escort. */
	public static final int SKELETON_CAPTAIN_DAY = 60;

	/** Bastion: normal zombie or skeleton with high health pool. Chance in {@code Bastion}, FIXED by day. */
	public static final int BASTION_DAY = 60;

	/** Raid anchor: from this day it can spawn naturally (chance and cooldown in RaidAnchorNatural). */
	public static final int RAID_ANCHOR_DAY = 80;

	/** Basilisk skeleton. */
	public static final int BASILISK_DAY = 80;

	// ===================== DAY-0 START VALUES =====================

	/**
	 * "Dangerous abilities" (spawner head, tactician, fast bow, TNT, pumpkin, dispenser, harmful arrows,
	 * punch bow): at start, chance = max × this ratio. 0.10 = one tenth of max at day 100.
	 * 0 = no dangerous abilities at game start.
	 */
	public static final float DANGEROUS_START_RATIO = 0.10f;

	/** Any weapon in hand: at start, chance = max × this ratio. 0.25 = one quarter of max. */
	public static final float WEAPON_START_RATIO = 0.25f;

	/** Shield: at start, chance = max × this ratio. 0.10 = one tenth of max. */
	public static final float SHIELD_START_RATIO = 0.10f;

	/** Totem of undying: at start, chance = max × this ratio. 0.05 = one twentieth of max. */
	public static final float TOTEM_START_RATIO = 0.05f;

	/** Per-slot armor piece chance at game start. 0.08 = 8%. End of scale — in {@code RandomArmor}. */
	public static final float ARMOR_PIECE_CHANCE_START = 0.08f;

	/**
	 * Full armor set chance at game start. 0 = no full-set mobs on day one.
	 * End of scale — {@code RandomArmor.FULL_SET_CHANCE}. Grows with scale and moon.
	 */
	public static final float FULL_ARMOR_SET_CHANCE_START = 0.0f;

	/** Main-hand weapon enchant chance at game start. 0.01 = 1%. End — in {@code MobEnchantments}. */
	public static final float WEAPON_ENCHANT_CHANCE_START = 0.01f;

	/** Armor piece enchant chance at game start. 0.01 = 1%. */
	public static final float ARMOR_ENCHANT_CHANCE_START = 0.01f;

	/**
	 * How much enchant level rolls favor low values at start. 3.0 = high levels almost never roll.
	 * 0.0 = all levels equally likely; below 0 — high levels more common. End — in {@code MobEnchantments}.
	 */
	public static final double ENCHANT_LEVEL_BIAS_START = 3.0;

	/** Weapon enchant count weights at start (one, two, three, four, five enchants). End — {@code COUNT_WEIGHTS}. */
	public static final double[] ENCHANT_COUNT_WEIGHTS_START = { 85, 11, 3, 1, 0 };

	/** Armor enchant count weights at start (one, two, three, four). */
	public static final double[] ARMOR_ENCHANT_COUNT_WEIGHTS_START = { 90, 8, 2, 0 };

	/**
	 * Potion buff count weights at start (none, one, two, three, four, five).
	 * End — {@code MobPotionBuffs.COUNT_WEIGHTS}.
	 */
	public static final double[] POTION_COUNT_WEIGHTS_START = { 96, 3, 1, 0, 0, 0 };

	/**
	 * Armor material weights at start: leather, gold, chain, copper, iron, diamond.
	 * Diamond 0.3 of 100 ≈ vanilla diamond zombie rarity. End — {@code RandomArmor.MATERIAL_WEIGHTS}.
	 */
	public static final double[] ARMOR_MATERIAL_WEIGHTS_START = { 60, 20, 9, 6, 4.7, 0.3 };

	/**
	 * Weapon material weights at start: wood, stone, gold, copper, iron, diamond.
	 * Wood and stone in zombie hands are not that rare early. End — {@code RandomWeapons.MATERIAL_WEIGHTS}.
	 */
	public static final double[] WEAPON_MATERIAL_WEIGHTS_START = { 50, 30, 10, 6, 3.7, 0.3 };

	/**
	 * Zombie and skeleton max health at game start. Minimum always 20 (vanilla);
	 * maximum smoothly rises to {@code MobHealth.ZOMBIE_SKELETON_MAX} by day 100.
	 */
	public static final int ZOMBIE_SKELETON_HEALTH_MAX_START = 20;

	// ===================== HUD ICON STAGES =====================

	/**
	 * Day when each skull icon appears right of the bar (4 values, ascending):
	 * 0 — sleeping zombie, then helmet zombie, red-eye skull, crowned skull.
	 * Defaults tie to milestones: first siege, captain and overseer, basilisk and anchor.
	 */
	public static final int[] STAGE_ICON_FROM_DAY = { 0, SIEGE_FIRST_DAY, SKELETON_CAPTAIN_DAY, BASILISK_DAY };

	private ThreatTable() {
	}
}
