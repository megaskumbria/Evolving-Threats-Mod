package com.example;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

/**
 Random charms on weapons, tools and armor mobs (zombies, skeletons, robbers).
 * Weapons: swords, axes, pickaxes, shovels, hoes, bow and crossbow. Armor: helmet, breastplate, knives, shoes
 On zombies, skeletons, pillagers, and vindicators. An illager set is not enchanted as a whole: each piece rolls alone.
 *
 How it works: after the mob has received weapons and armor, for each item
 There is a chance to “enchant”. If it falls out - from 1 to 5 charms suitable for this object are selected,
 Incompatible with each other (sharpness + heavenly punishment, protection + protection from fire, etc.)
 * They don't get together like in vanilla Minecraft.
 Charm levels can be up to the maximum (sharpness V on a wooden sword, protection IV on a leather helmet),
 *But high levels rarely fall out.
 *
 * <p>What really works in the battle of the mob: sharpness, fiery conspiracy, throwback (close battle),
 *force/drop/flame (bow), fast recharge/multi-charge/piercing arrow (bow),
 Protection of all kinds (mob harder to kill), thorns (beat the player back), weightlessness.
 Heavenly punishment and the scourge of arthropod damage to the player do not increase (the player is not undead and not a spider).
 The rest (mining, efficiency, strength, repair, curses, underwater breathing...) on the mob itself
 It does not change anything – they are needed to make the object that fell out an interesting loot.
 */
public final class MobEnchantments {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - Mobs do not receive charms (weapons are still issued as before). */
	public static final boolean ENABLED = true;

	/*
	 Important about the odds below: these are values on the 100th day (End of the threat scale).
	 At the beginning of the game, the chance is much smaller (ThreatTable.WEAPON ENCHANT CHANCE START / ARMOR ENCHANT CHANCE START)
	 It grows smoothly with the scale and the moon. The number of charms and their levels are also increasing.
	 Mechanics labeled FIXED (floating instrument, captain, raid, rituals) should not grow: they call
	 * rollHeldWeapons / rollArmorPieces with your chance, and there remain your old numbers (COUNT WEIGHTS and LEVEL BIAS FIXED).
	 */

	/**
	 Chance (0.0-1.0) that EVERY object in the hands of zombies will be enchanted on day 100. 0.70 = 70%.
	 This is a chance for zombies who have weapons (about 40% of zombies, see RandomWeapons).
	 * 0 = Zombies will have no charm.
	 */
	public static final float ZOMBIE_ENCHANT_CHANCE = 0.70f;

	/**
	 * Chance (0.0–1.0) for EVERY object in the hands of the skeleton/wanderer (bow and second weapon thrown separately).
	 0.70 = 70% on the 100th day. 0 = skeletons will have no charms.
	 */
	public static final float SKELETON_ENCHANT_CHANCE = 0.70f;

	/**
	 Chance (0.0-1.0) for the weapon of the robber (crossbow) and the thug (axe).
	 0.70 = 70% on the 100th day. In the raid, the robber has the same chance, but the throw after the crossbow of the game itself.
	 If it falls out, the charms of fashion replace the quick recharge of the raid. If not, she stays.
	 Gromil in the raid this does not affect: the game changes his axe after our throw.
	 * 0 = no spell.
	 */
	public static final float ILLAGER_ENCHANT_CHANCE = 0.70f;

	/**
	 Chance (0.0–1.0) that EVERY piece of zombie armor will be enchanted on Day 100. 0.65 = 65%.
	 * Considered separately for helmet, breastplate, knife and boots (armor itself falls out in zombies with a 50% chance of slot,
	 * see RandomArmor. Zombies will not have armor with charms.
	 */
	public static final float ZOMBIE_ARMOR_ENCHANT_CHANCE = 0.65f;

	/*Same for the armor of skeletons and wanderers. 0.65 = 65% for each piece. 0 = Charm on the armor of skeletons will not. */
	public static final float SKELETON_ARMOR_ENCHANT_CHANCE = 0.65f;

	/**
	 * Chance (0.0–1.0) that ONE piece of pillager or vindicator armor is enchanted on day 100.
	 * Helmet, chest, legs, and boots each roll alone, so a set can have one enchanted piece or all four.
	 * 0.65 = 65% per piece. Day 0 is {@link ThreatTable#ARMOR_ENCHANT_CHANCE_START} (1%). 0 = plain armor.
	 */
	public static final float ILLAGER_ARMOR_ENCHANT_CHANCE = 0.65f;

	/**
	 How many spells at once on one object-weapons. Index 0 = one charm, 1 = two, 2 = three, etc. Numbers - "weight" (a chance relative to others).
	 * {55, 28, 12, 4, 1} = sum of 100: one charm at 55%, two at 28%, three at 12%, four at 4%, five at 1%.
	 If you want only one spell, put {1}. Do not add more than five without need: the object will become a “pumped sword” from the screen.
	 If suitable charms for the object are not enough, there will be less (for example, the hoe has few of them).
	 */
	public static final int[] COUNT_WEIGHTS = { 55, 28, 12, 4, 1 };

	/**
	 Same for Broney. Armor on the mob up to four pieces at once, so by default the spell per piece is slightly less:
	 * {60, 28, 10, 2} = one charm at 60%, two at 28%, three at 10%, four at 2%.
	 If you want "just protection IV and that's it," put {1}.
	 */
	public static final int[] ARMOR_COUNT_WEIGHTS = { 60, 28, 10, 2 };

	/**
	 How much the level of charm gravitates to low values - on the 100th day (end of the threat scale).
	 * For acuteness (maximum V): 0.0 = all levels are equally probable (V = 20%), 0.5 = V ≈ 12%,
	 1.0 = V ≈ 7%, 2.0 = V ≈ 2% (I ≈ 45%), 3.0 = V almost does not occur. Less than zero - high more often than low (now -0.3: V ≈ 26%, I ≈ 14%).
	 At the beginning of the game, the slope is stronger (ThreatTable.ENCHANT LEVEL BIAS START).
	 */
	public static final double LEVEL_BIAS = -0.3;

	/**
	 * Same bias for mechanics labeled FIXED (floating instrument, captain, raid, ritual groups):
	 They do not depend on the scale, so they remain on the previous 2.0. See the description of LEVEL BIAS.
	 */
	public static final double LEVEL_BIAS_FIXED = 2.0;

	/**
	 What charms can fall and how often (weight; 0 = off)
	 * Only those that fit the subject are taken from this list (sharpness - swords and axes, strength - bow, etc.),
	 vanilla incompatibility (sharpness + cara, silk touch + luck, ...) is taken into account itself.
	 The scales are taken from the Minecraft enchantment table, you can change as you like.
	 */
	private static final Candidate[] CANDIDATES = {
		// Swords and axes--
		new Candidate(Enchantments.SHARPNESS, 10),           // Acuteness (up to V): + Damage
		new Candidate(Enchantments.SMITE, 5),                // Heavenly punishment (up to V): damage to the undead, the player does not hit harder
		new Candidate(Enchantments.BANE_OF_ARTHROPODS, 5),   // Scourge of arthropods (up to V): damage to spiders, the player does not hit harder
		new Candidate(Enchantments.KNOCKBACK, 5),            // Rejection (up to II): Rejects the player
		new Candidate(Enchantments.FIRE_ASPECT, 5),          // Fire conspiracy (up to II): sets the player on fire
		new Candidate(Enchantments.LOOTING, 5),              // Mining (up to III): for loot only
		new Candidate(Enchantments.SWEEPING_EDGE, 5),        // Breaking blade (up to III): only for loot
		// Picks, shovels, hoes, axes.
		new Candidate(Enchantments.EFFICIENCY, 10),          // Efficiency (up to V): for loot only
		new Candidate(Enchantments.SILK_TOUCH, 1),           // Silk touch: for loot only
		new Candidate(Enchantments.FORTUNE, 5),              // Luck (up to III): Loot only
		// Onions ----
		new Candidate(Enchantments.POWER, 10),               // Force (up to V): +arrow damage
		new Candidate(Enchantments.PUNCH, 2),                // Arrow drop (up to II)
		new Candidate(Enchantments.FLAME, 2),                // Burning arrow
		new Candidate(Enchantments.INFINITY, 1),             // Infinity: for loot only
		// Crossbow ----
		new Candidate(Enchantments.QUICK_CHARGE, 5),         // Fast recharge (up to III)
		new Candidate(Enchantments.MULTISHOT, 2),            // Multi-charge: three arrows
		new Candidate(Enchantments.PIERCING, 10),            // Piercing arrow (up to IV)
		// ---- armor ----
		new Candidate(Enchantments.PROTECTION, 10),          // Defense (up to IV): Mob harder to kill
		new Candidate(Enchantments.FIRE_PROTECTION, 5),      // Fire protection (up to IV): burns and almost no lava
		new Candidate(Enchantments.BLAST_PROTECTION, 2),     // Explosion protection (up to IV): the cryper hardly hits
		new Candidate(Enchantments.PROJECTILE_PROTECTION, 5),// Protection against shells (up to IV): arrows hardly hit
		new Candidate(Enchantments.THORNS, 3),               // Thorns (up to III): hit the player in response to each punch
		new Candidate(Enchantments.FEATHER_FALLING, 5),      // Weightlessness (up to IV, boots): no fall damage
		new Candidate(Enchantments.RESPIRATION, 2),          // Underwater Breathing (up to III, helmet): Loot only
		new Candidate(Enchantments.AQUA_AFFINITY, 2),        // Submarine (helmet): loot only
		new Candidate(Enchantments.DEPTH_STRIDER, 2),        // Scuba walking (up to III, boots): faster in the water
		new Candidate(Enchantments.FROST_WALKER, 0),         // Icebreaker (shoes): OFF, mob would freeze water underneath
		new Candidate(Enchantments.SOUL_SPEED, 1),           // Speed of the soul (up to III, shoes): faster on the sand of the shower
		new Candidate(Enchantments.SWIFT_SNEAK, 1),          // Agility (up to III, knives): only for loot
		new Candidate(Enchantments.BINDING_CURSE, 1),        // The curse of removability: wearing armor from a fallen object can not be removed
		// - For everyone--
		new Candidate(Enchantments.UNBREAKING, 10),          // Strength (up to III): only for loot
		new Candidate(Enchantments.MENDING, 2),              // Fixing: Loot only
		new Candidate(Enchantments.VANISHING_CURSE, 2),      // The Curse of Loss: For Loot Only
	};

	// ========================================================================

	private static final EquipmentSlot[] HAND_SLOTS = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND };

	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
	};

	/*Charm from the list of settings above: key in the game registry and weight. */
	private record Candidate(ResourceKey<Enchantment> key, int weight) {
	}

	/*Charm already drawn from the game register and suitable for the subject. */
	private record Option(Holder<Enchantment> holder, int weight) {
	}

	private MobEnchantments() {
	}

	/*Called out of ZombieMixin after a zombie gets a gun. */
	public static void rollForZombie(Mob mob) {
		rollForHeldItems(mob, ZOMBIE_ENCHANT_CHANCE, true);
	}

	/*Called out of SkeletonMixin after the skeleton has received a bow/weapon. */
	public static void rollForSkeleton(Mob mob) {
		rollForHeldItems(mob, SKELETON_ENCHANT_CHANCE, true);
	}

	/*Called from IllagerWeaponEnchantMixin for robbers and thugs. */
	public static void rollForIllager(Mob mob) {
		rollForHeldItems(mob, ILLAGER_ENCHANT_CHANCE, true);
	}

	/*Called from ZombieMixin after the zombie received armor (RandomArmor). */
	public static void rollArmorForZombie(Mob mob) {
		rollForArmor(mob, ZOMBIE_ARMOR_ENCHANT_CHANCE, true);
	}

	/*Called from SkeletonMixin after the skeleton has received armor (RandomArmor). */
	public static void rollArmorForSkeleton(Mob mob) {
		rollForArmor(mob, SKELETON_ARMOR_ENCHANT_CHANCE, true);
	}

	/** After a pillager or vindicator has a set on. Each piece rolls on its own. */
	public static void rollArmorForIllager(Mob mob) {
		rollForArmor(mob, ILLAGER_ARMOR_ENCHANT_CHANCE, true);
	}

	/**
	 * Chance for each piece of armor. 0.45 = 45% on the helmet, 45% on the breastplate and so on.
	 * 0 = armor without enchantment. 1 = enchanted every piece.
	 For mechanic labeled FIXED (captain, raid, ritual): the odds and levels of the threat scale are independent.
	 */
	public static void rollArmorPieces(Mob mob, float chancePerPiece) {
		rollForArmor(mob, chancePerPiece, false);
	}

	/** Chance for each item in hand. 0 = weapon without charm. The threat scale does not depend on the FIXED label. */
	public static void rollHeldWeapons(Mob mob, float chancePerItem) {
		rollForHeldItems(mob, chancePerItem, false);
	}

	/**
	 * Throws a chance separately for each piece of armor. Special "heads" (savner, pumpkin, distributor) do not touch.
	 *
	 * @param growing true - {@code chance} is the value on day 100, at the beginning of the game it is smaller (GROWING)
	 */
	private static void rollForArmor(Mob mob, float chance, boolean growing) {
		if (!ENABLED || chance <= 0.0f) {
			return;
		}
		float actual = growing
				? ThreatScale.lerp(mob.level(), ThreatTable.ARMOR_ENCHANT_CHANCE_START, chance)
				: chance;
		RandomSource random = mob.getRandom();
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = mob.getItemBySlot(slot);
			if (!RandomArmor.isModArmor(stack) || random.nextFloat() >= actual) {
				continue;
			}
			if (enchant(mob, stack, random, true, growing)) {
				mob.setItemSlot(slot, stack);
			}
		}
	}

	/**
	 Sword spell in the main hand. Stone and unserite, too:
	 * The usual list of weapons fashion Neserite does not include.
	 * {@code endChance} is a chance on day 100 (0.70 = 70%).
	 * At the beginning it is equal to {@link ThreatTable#WEAPON ENCHANT CHANCE START} (1%).
	 The number of charms and their levels increase the same as the zombie sword. The moon does, too.
	 If the spell has fallen, it replaces those that the game has already hung on the sword.
	 */
	public static void rollMainHandGrowing(Mob mob, float endChance) {
		if (!ENABLED || endChance <= 0.0f) {
			return;
		}
		float actual = ThreatScale.lerp(mob.level(), ThreatTable.WEAPON_ENCHANT_CHANCE_START, endChance);
		ItemStack stack = mob.getMainHandItem();
		if (stack.isEmpty() || mob.getRandom().nextFloat() >= actual) {
			return;
		}
		if (enchant(mob, stack, mob.getRandom(), false, true)) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
		}
	}

	/*Throws a chance separately for each item in the main and second hand. {@code growing} - like a reservation. */
	private static void rollForHeldItems(Mob mob, float chance, boolean growing) {
		if (!ENABLED || chance <= 0.0f) {
			return;
		}
		float actual = growing
				? ThreatScale.lerp(mob.level(), ThreatTable.WEAPON_ENCHANT_CHANCE_START, chance)
				: chance;
		RandomSource random = mob.getRandom();
		for (EquipmentSlot slot : HAND_SLOTS) {
			ItemStack stack = mob.getItemBySlot(slot);
			if (!isEnchantableWeapon(stack) || random.nextFloat() >= actual) {
				continue;
			}
			if (enchant(mob, stack, random, false, growing)) {
				// Put it back in case the game gave away a copy instead of the item itself.
				mob.setItemSlot(slot, stack);
			}
		}
	}

	/** Only our weapons/tools (wood...diamond, iron axe of the thug) and bows/arbalets. Shields, armor, trident. */
	private static boolean isEnchantableWeapon(ItemStack stack) {
		return !stack.isEmpty()
				&& (RandomWeapons.isModMeleeWeapon(stack) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW));
	}

	/**
	 * Replaces the charm of an object with a random set. Vanilla charms (if the game has already given them) are erased.
	 Otherwise, the old and the new spell might not be compatible.
	 *
	 * @return true if something is hung on the subject
	 */
	private static boolean enchant(Mob mob, ItemStack stack, RandomSource random, boolean armor, boolean growing) {
		List<Option> pool = buildPool(mob, stack);
		if (pool.isEmpty()) {
			return false;
		}

		// GROWING: Both the number of charms and their levels depend on the threat scale. FIXED: former values.
		int[] endCounts = armor ? ARMOR_COUNT_WEIGHTS : COUNT_WEIGHTS;
		int wanted;
		double levelBias;
		if (growing) {
			double[] startCounts = armor
					? ThreatTable.ARMOR_ENCHANT_COUNT_WEIGHTS_START
					: ThreatTable.ENCHANT_COUNT_WEIGHTS_START;
			wanted = ThreatScale.pickWeighted(mob.level(), random, startCounts, endCounts) + 1;
			levelBias = ThreatScale.lerp(mob.level(), ThreatTable.ENCHANT_LEVEL_BIAS_START, LEVEL_BIAS);
		} else {
			wanted = pickCount(random, endCounts);
			levelBias = LEVEL_BIAS_FIXED;
		}
		List<Option> chosen = new ArrayList<>();
		while (chosen.size() < wanted && !pool.isEmpty()) {
			Option pick = pickWeighted(random, pool);
			chosen.add(pick);
			pool.remove(pick);
			// We throw out of the pool everything that is incompatible with the chosen charm (sharpness . Kara . Beach, etc.).
			pool.removeIf(other -> !Enchantment.areCompatible(pick.holder(), other.holder()));
		}

		EnchantmentHelper.updateEnchantments(stack, mutable -> {
			mutable.removeIf(holder -> true);
			for (Option option : chosen) {
				mutable.set(option.holder(), pickLevel(random, option.holder().value().getMaxLevel(), levelBias));
			}
		});
		return true;
	}

	/** Suitable subject charms from the list of settings. */
	private static List<Option> buildPool(Mob mob, ItemStack stack) {
		var registry = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<Option> pool = new ArrayList<>();
		for (Candidate candidate : CANDIDATES) {
			if (candidate.weight() <= 0) {
				continue;
			}
			Holder<Enchantment> holder = registry.getOrThrow(candidate.key());
			if (holder.value().isSupportedItem(stack)) {
				pool.add(new Option(holder, candidate.weight()));
			}
		}
		return pool;
	}

	private static int pickCount(RandomSource random, int[] countWeights) {
		int total = 0;
		for (int weight : countWeights) {
			total += weight;
		}
		if (total <= 0) {
			return 1;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < countWeights.length; i++) {
			roll -= countWeights[i];
			if (roll < 0) {
				return i + 1;
			}
		}
		return 1;
	}

	private static Option pickWeighted(RandomSource random, List<Option> pool) {
		int total = 0;
		for (Option option : pool) {
			total += option.weight();
		}
		int roll = random.nextInt(total);
		for (Option option : pool) {
			roll -= option.weight();
			if (roll < 0) {
				return option;
			}
		}
		return pool.get(0);
	}

	/** Level 1 to maxLevel; the higher the {@code bias}, the lower the higher levels (see LEVEL BIAS). */
	private static int pickLevel(RandomSource random, int maxLevel, double bias) {
		if (maxLevel <= 1) {
			return 1;
		}
		double[] weights = new double[maxLevel];
		double total = 0.0;
		for (int level = 1; level <= maxLevel; level++) {
			weights[level - 1] = Math.pow(maxLevel - level + 1, bias);
			total += weights[level - 1];
		}
		double roll = random.nextDouble() * total;
		for (int level = 1; level <= maxLevel; level++) {
			roll -= weights[level - 1];
			if (roll < 0.0) {
				return level;
			}
		}
		return 1;
	}
}
