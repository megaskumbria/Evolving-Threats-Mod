package com.example;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Случайная броня по слотам. Каждый слот (шлем, нагрудник, штаны, ботинки)
 * бросается отдельно — поэтому бывает «алмазный нагрудник + кожаные штаны».
 * Незеритовой брони здесь нет.
 */
public final class RandomArmor {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/**
	 * Шанс (0.0–1.0) для КАЖДОГО слота брони у зомби.
	 * 0.50 = 50% на шлем, 50% на нагрудник, и т.д. (независимо друг от друга).
	 */
	public static final float ZOMBIE_ARMOR_PIECE_CHANCE = 0.50f;

	/** То же для обычного скелета и странника. */
	public static final float SKELETON_ARMOR_PIECE_CHANCE = 0.50f;

	/**
	 * Незеритовая броня при спавне не используется (только кожа…алмаз).
	 * Константа для ясности; менять не нужно, пока не добавишь незерит в ARMOR_PIECES.
	 */
	public static final boolean ALLOW_NETHERITE_ARMOR = false;

	/**
	 * Шанс, что выпавший предмет брони будет снят с моба при смерти (как у ванильных мобов).
	 * 0.085 ≈ 8.5%. Поставь 1.0f, если хочешь, чтобы броня падала всегда.
	 */
	public static final float ARMOR_DROP_CHANCE = 0.085f;

	// ========================================================================

	/** Порядок: шлем, нагрудник, поножи, ботинки. Строки — материал (см. MATERIAL_WEIGHTS). */
	private static final Item[][] ARMOR_PIECES = {
		{ Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS },
		{ Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS },
		{ Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
		{ Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS },
		{ Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS },
		{ Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS },
	};

	private static final int[] MATERIAL_WEIGHTS = {
		28, // кожа
		12, // золото
		18, // кольчуга
		12, // медь
		22, // железо
		8,  // алмаз
	};

	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD,
		EquipmentSlot.CHEST,
		EquipmentSlot.LEGS,
		EquipmentSlot.FEET,
	};

	private RandomArmor() {
	}

	public static void rollForZombie(Mob mob) {
		rollPieces(mob, ZOMBIE_ARMOR_PIECE_CHANCE);
	}

	public static void rollForSkeleton(Mob mob) {
		rollPieces(mob, SKELETON_ARMOR_PIECE_CHANCE);
	}

	private static void rollPieces(Mob mob, float pieceChance) {
		RandomSource random = mob.getRandom();

		for (int slotIndex = 0; slotIndex < SLOTS.length; slotIndex++) {
			if (random.nextFloat() >= pieceChance) {
				continue;
			}

			int materialIndex = pickMaterialIndex(random);
			Item armorItem = ARMOR_PIECES[materialIndex][slotIndex];
			EquipmentSlot slot = SLOTS[slotIndex];

			mob.setItemSlot(slot, new ItemStack(armorItem));
			mob.setDropChance(slot, ARMOR_DROP_CHANCE);
		}
	}

	private static int pickMaterialIndex(RandomSource random) {
		int totalWeight = 0;
		for (int weight : MATERIAL_WEIGHTS) {
			totalWeight += weight;
		}

		int roll = random.nextInt(totalWeight);
		for (int i = 0; i < MATERIAL_WEIGHTS.length; i++) {
			roll -= MATERIAL_WEIGHTS[i];
			if (roll < 0) {
				return i;
			}
		}
		return 0;
	}
}
