package com.example.raid;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 Lot servants raid: his rand with a slope in strong sets and charms.
 * Regular {@link com.example.RandomArmor}/{@link com.example.RandomWeapons} are not called.
 */
public final class RaidEliteLoadout {

	// ===================== SETTINGS (change these numbers) =====================

	/** Chance (0.0-1.0) to wear each slot of armor from the raider. 0.75 = 75% per slot. */
	public static final float ARMOR_PIECE_CHANCE = 0.75f;

	/**
	 Weight of armor material: leather, gold, chain mail, copper, iron, diamond.
	 The amount does not have to be 100 – the relative shares are important (iron / diamond is higher than in the world).
	 */
	public static final int[] ARMOR_MATERIAL_WEIGHTS = { 8, 4, 10, 6, 28, 18 };

	/*A chance to enchant a fallen piece of armor. 0.55 = 55%. */
	public static final float ARMOR_ENCHANT_CHANCE = 0.55f;

	/*Protection level on the raid armor (1-2). */
	public static final int ARMOR_PROTECTION_MIN = 1;
	public static final int ARMOR_PROTECTION_MAX = 2;

	/*The chance that a zombie servant will get a gun in the main hand. */
	public static final float ZOMBIE_WEAPON_CHANCE = 0.90f;

	/*Weight of material weapons zombies: wood, stone, gold, copper, iron, diamond (without netherite). */
	public static final int[] WEAPON_MATERIAL_WEIGHTS = { 5, 8, 4, 6, 30, 21 };

	/*A chance to enchant zombie weapons. */
	public static final float WEAPON_ENCHANT_CHANCE = 0.50f;

	public static final int WEAPON_SHARPNESS_MIN = 1;
	public static final int WEAPON_SHARPNESS_MAX = 3;

	/*Power chance on the bow of a servant skeleton. */
	public static final float SKELETON_BOW_ENCHANT_CHANCE = 0.65f;

	public static final int SKELETON_POWER_MIN = 1;
	public static final int SKELETON_POWER_MAX = 3;

	public static final float ARMOR_DROP_CHANCE = 0.085f;

	// ========================================================================

	private static final Item[][] ARMOR_PIECES = {
			{ Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS },
			{ Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS },
			{ Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
			{ Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS },
			{ Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS },
			{ Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS },
	};

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
	};

	private static final Item[][] WEAPONS = {
			{ Items.WOODEN_SWORD, Items.WOODEN_AXE },
			{ Items.STONE_SWORD, Items.STONE_AXE },
			{ Items.GOLDEN_SWORD, Items.GOLDEN_AXE },
			{ Items.COPPER_SWORD, Items.COPPER_AXE },
			{ Items.IRON_SWORD, Items.IRON_AXE },
			{ Items.DIAMOND_SWORD, Items.DIAMOND_AXE },
	};

	private RaidEliteLoadout() {
	}

	public static void equipZombie(Mob mob, ServerLevel level) {
		rollArmor(mob, level);
		RandomSource random = mob.getRandom();
		if (random.nextFloat() < ZOMBIE_WEAPON_CHANCE) {
			Item weapon = pickWeapon(random);
			ItemStack stack = new ItemStack(weapon);
			maybeEnchantWeapon(level, stack, random);
			mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
			mob.setDropChance(EquipmentSlot.MAINHAND, ARMOR_DROP_CHANCE);
		}
	}

	public static void equipSkeleton(Skeleton skeleton, ServerLevel level) {
		rollArmor(skeleton, level);
		ItemStack bow = new ItemStack(Items.BOW);
		RandomSource random = skeleton.getRandom();
		if (random.nextFloat() < SKELETON_BOW_ENCHANT_CHANCE) {
			int power = random.nextInt(SKELETON_POWER_MAX - SKELETON_POWER_MIN + 1) + SKELETON_POWER_MIN;
			Holder<Enchantment> powerEnch = level.registryAccess()
					.lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(Enchantments.POWER);
			EnchantmentHelper.updateEnchantments(bow, mutable -> mutable.set(powerEnch, power));
		}
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
		skeleton.setDropChance(EquipmentSlot.MAINHAND, ARMOR_DROP_CHANCE);
	}

	private static void rollArmor(Mob mob, ServerLevel level) {
		RandomSource random = mob.getRandom();
		for (int slotIndex = 0; slotIndex < ARMOR_SLOTS.length; slotIndex++) {
			if (random.nextFloat() >= ARMOR_PIECE_CHANCE) {
				continue;
			}
			int mat = pickWeightedIndex(random, ARMOR_MATERIAL_WEIGHTS);
			ItemStack stack = new ItemStack(ARMOR_PIECES[mat][slotIndex]);
			maybeEnchantArmor(level, stack, random);
			EquipmentSlot slot = ARMOR_SLOTS[slotIndex];
			mob.setItemSlot(slot, stack);
			mob.setDropChance(slot, ARMOR_DROP_CHANCE);
		}
	}

	private static void maybeEnchantArmor(ServerLevel level, ItemStack stack, RandomSource random) {
		if (random.nextFloat() >= ARMOR_ENCHANT_CHANCE) {
			return;
		}
		int levelValue = random.nextInt(ARMOR_PROTECTION_MAX - ARMOR_PROTECTION_MIN + 1) + ARMOR_PROTECTION_MIN;
		Holder<Enchantment> protection = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.PROTECTION);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(protection, levelValue));
	}

	private static void maybeEnchantWeapon(ServerLevel level, ItemStack stack, RandomSource random) {
		if (random.nextFloat() >= WEAPON_ENCHANT_CHANCE) {
			return;
		}
		int sharp = random.nextInt(WEAPON_SHARPNESS_MAX - WEAPON_SHARPNESS_MIN + 1) + WEAPON_SHARPNESS_MIN;
		Holder<Enchantment> sharpEnch = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.SHARPNESS);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(sharpEnch, sharp));
	}

	private static Item pickWeapon(RandomSource random) {
		int mat = pickWeightedIndex(random, WEAPON_MATERIAL_WEIGHTS);
		Item[] row = WEAPONS[mat];
		return row[random.nextInt(row.length)];
	}

	private static int pickWeightedIndex(RandomSource random, int[] weights) {
		int total = 0;
		for (int w : weights) {
			total += w;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];
			if (roll < 0) {
				return i;
			}
		}
		return 0;
	}
}
