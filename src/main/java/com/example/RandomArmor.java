package com.example;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Случайная броня по слотам. Каждый слот (шлем, нагрудник, штаны, ботинки)
 * бросается отдельно — поэтому бывает «алмазный нагрудник + кожаные штаны».
 * Незеритовой брони здесь нет.
 */
public final class RandomArmor {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/**
	 * Шанс (0.0–1.0) для КАЖДОГО слота брони у зомби на 100-й день (конец шкалы угрозы).
	 * 0.50 = 50% на шлем, 50% на нагрудник, и т.д. (независимо друг от друга).
	 * В начале игры шанс меньше (ThreatTable.ARMOR_PIECE_CHANCE_START) и растёт вместе со шкалой и луной.
	 */
	public static final float ZOMBIE_ARMOR_PIECE_CHANCE = 0.50f;

	/** То же для обычного скелета и странника (конец шкалы). */
	public static final float SKELETON_ARMOR_PIECE_CHANCE = 0.50f;

	/**
	 * Незеритовая броня при спавне не используется (только кожа…алмаз).
	 * Константа для ясности; менять не нужно, пока не добавишь незерит в ARMOR_PIECES.
	 */
	public static final boolean ALLOW_NETHERITE_ARMOR = false;

	/**
	 * Шанс (0.0–1.0), что моб с кожаной бронёй будет в КРАШЕНОЙ кожаной броне. 0.50 = 50%.
	 * Бросается один раз на моба, а не на каждый кусок: так форма получается цельной.
	 * Красится только кожа (остальная броня в игре не красится). 0 = кожа всегда обычная коричневая.
	 */
	public static final float LEATHER_DYE_CHANCE = 0.50f;

	/**
	 * Шанс (0.0–1.0), что отдельный кусок кожаной брони у красящегося моба получит СВОЙ цвет, а не общий.
	 * 0.25 = примерно каждый четвёртый кусок в другом цвете. 0 = весь комплект строго одного цвета.
	 */
	public static final float LEATHER_PIECE_OWN_COLOR_CHANCE = 0.25f;

	/**
	 * Откуда брать цвет.
	 * false = любой из 16 777 216 оттенков (полный диапазон; бывают тусклые серо-коричневые).
	 * true  = только один из 16 цветов красителей Майнкрафта (чистые яркие цвета).
	 * Если с полным диапазоном будет «грязно» — поставь true.
	 */
	public static final boolean LEATHER_USE_ONLY_DYE_COLORS = false;

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

	/**
	 * Веса материала на 100-й день (конец шкалы). В начале игры веса другие:
	 * ThreatTable.ARMOR_MATERIAL_WEIGHTS_START. Между ними идёт плавный переход по шкале угрозы и луне.
	 * Незерита нет: он только у Василиска.
	 */
	private static final int[] MATERIAL_WEIGHTS = {
		26, // кожа
		12, // золото
		18, // кольчуга
		12, // медь
		20, // железо
		12, // алмаз
	};

	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD,
		EquipmentSlot.CHEST,
		EquipmentSlot.LEGS,
		EquipmentSlot.FEET,
	};

	private RandomArmor() {
	}

	/**
	 * Это кусок брони из нашего списка (кожа…алмаз)? Нужен, чтобы чары не лезли на особые «головы»
	 * (спавнер, тыква-маяк, раздатчик), которые лежат в том же слоте шлема.
	 */
	public static boolean isModArmor(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Item item = stack.getItem();
		for (Item[] row : ARMOR_PIECES) {
			for (Item piece : row) {
				if (item == piece) {
					return true;
				}
			}
		}
		return false;
	}

	public static void rollForZombie(Mob mob) {
		rollPieces(mob, pieceChance(mob, ZOMBIE_ARMOR_PIECE_CHANCE));
	}

	public static void rollForSkeleton(Mob mob) {
		rollPieces(mob, pieceChance(mob, SKELETON_ARMOR_PIECE_CHANCE));
	}

	/** РАСТЁТ: шанс слота между ThreatTable.ARMOR_PIECE_CHANCE_START (день 0) и {@code endChance} (день 100). */
	private static float pieceChance(Mob mob, float endChance) {
		return ThreatScale.lerp(mob.level(), ThreatTable.ARMOR_PIECE_CHANCE_START, endChance);
	}

	/**
	 * Только шлем, с тем же шансом и теми же материалами, что слот шлема у обычного скелета.
	 * Нагрудник, штаны и ботинки не трогает. Незерита в списке нет.
	 */
	public static void rollHelmet(Mob mob) {
		RandomSource random = mob.getRandom();
		if (random.nextFloat() >= pieceChance(mob, SKELETON_ARMOR_PIECE_CHANCE)) {
			return;
		}
		Item helmet = ARMOR_PIECES[pickMaterialIndex(mob, random)][0];
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
		mob.setDropChance(EquipmentSlot.HEAD, ARMOR_DROP_CHANCE);
		dyeLeather(mob, random);
	}

	private static void rollPieces(Mob mob, float pieceChance) {
		RandomSource random = mob.getRandom();

		for (int slotIndex = 0; slotIndex < SLOTS.length; slotIndex++) {
			if (random.nextFloat() >= pieceChance) {
				continue;
			}

			int materialIndex = pickMaterialIndex(mob, random);
			Item armorItem = ARMOR_PIECES[materialIndex][slotIndex];
			EquipmentSlot slot = SLOTS[slotIndex];

			mob.setItemSlot(slot, new ItemStack(armorItem));
			mob.setDropChance(slot, ARMOR_DROP_CHANCE);
		}

		dyeLeather(mob, random);
	}

	/**
	 * Красит кожаные куски на мобе (включая те, что выдала игра): один общий цвет на комплект,
	 * иногда отдельный кусок получает свой. Остальные материалы не трогает. Цвет остаётся и на выпавшей броне.
	 */
	private static void dyeLeather(Mob mob, RandomSource random) {
		if (LEATHER_DYE_CHANCE <= 0.0f || random.nextFloat() >= LEATHER_DYE_CHANCE) {
			return;
		}
		int setColor = randomLeatherColor(random);
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = mob.getItemBySlot(slot);
			if (!isLeather(stack)) {
				continue;
			}
			int color = random.nextFloat() < LEATHER_PIECE_OWN_COLOR_CHANCE ? randomLeatherColor(random) : setColor;
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
			mob.setItemSlot(slot, stack);
		}
	}

	/** Это кожаный шлем/нагрудник/штаны/ботинки? (Первая строка списка ARMOR_PIECES — кожа.) */
	private static boolean isLeather(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		for (Item piece : ARMOR_PIECES[0]) {
			if (stack.getItem() == piece) {
				return true;
			}
		}
		return false;
	}

	/** Случайный цвет RGB: из 16 красителей или любой из 16 777 216 (см. LEATHER_USE_ONLY_DYE_COLORS). */
	private static int randomLeatherColor(RandomSource random) {
		if (LEATHER_USE_ONLY_DYE_COLORS) {
			DyeColor[] dyes = DyeColor.values();
			return dyes[random.nextInt(dyes.length)].getTextureDiffuseColor() & 0xFFFFFF;
		}
		return random.nextInt(0x1000000);
	}

	/** Материал по весам, которые плавно меняются от начала шкалы угрозы к концу. */
	private static int pickMaterialIndex(Mob mob, RandomSource random) {
		return ThreatScale.pickWeighted(mob.level(), random, ThreatTable.ARMOR_MATERIAL_WEIGHTS_START, MATERIAL_WEIGHTS);
	}
}
