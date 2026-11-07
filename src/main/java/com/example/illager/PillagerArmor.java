package com.example.illager;

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
 * A pillager or vindicator may wear a full set: helmet, chestplate, leggings, and boots of one material.
 * Leather, iron, chain, copper, or diamond. No gold, no netherite. Diamond stays the rare one.
 * GROWING: few wear armor on day 0, most do by day 100. The moon nudges the chance.
 * The set is real equipment, so it also blocks damage.
 * The picture on the body is drawn separately: the illager model is not a player's, so the armor is glued on.
 * The axe or crossbow in hand is not replaced.
 */
public final class PillagerArmor {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — new pillagers and vindicators stay in their robes. Already spawned ones keep what they wear. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) a new pillager or vindicator wears a full set at day 100, full moon.
	 * Day 0 is {@link ThreatTable#ILLAGER_ARMOR_CHANCE_START} (12%). The moon moves the chance between them.
	 * 0.75 = 75%. 1 = every one of them. 0 = robes only, whatever the day.
	 */
	public static final float WEAR_CHANCE_END = 0.75f;

	/**
	 * Chance (0.0–1.0) that a leather set is dyed. 1.0 = every leather set has a color.
	 * 0 = leather stays brown. Other materials are never dyed.
	 * One color for the whole set, so it still looks like one outfit.
	 */
	public static final float LEATHER_DYE_CHANCE = 1.0f;

	/**
	 * true = one of the 16 dye colors (easy to tell leather from iron).
	 * false = any RGB shade, often muddy.
	 */
	public static final boolean LEATHER_USE_DYE_COLORS = true;

	/**
	 * Chance (0.0–1.0) each worn piece drops on death. 0.085 = 8.5%, same as zombie armor.
	 * 0 = nothing drops. 1 = the whole set drops.
	 */
	public static final float DROP_CHANCE = 0.085f;

	// ========================================================================

	/**
	 * Helmet, chestplate, leggings, boots. One row is one full set.
	 * Order matches {@link #MATERIAL_WEIGHTS}: leather, iron, chain, copper, diamond.
	 * Row 0 must stay leather — dyeing looks at that row. No gold, no netherite.
	 */
	private static final Item[][] SETS = {
			{ Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS },
			{ Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS },
			{ Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
			{ Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS },
			{ Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS },
	};

	/**
	 * How often each set is picked, same order as {@link #SETS}.
	 * These do not grow with the day: only the chance of wearing anything does.
	 * 34 / 28 / 22 / 13 / 3 out of 100. Diamond is 3, about 1 in 33 armored illagers.
	 * 0 drops that material. They need not sum to 100.
	 */
	private static final double[] MATERIAL_WEIGHTS = { 34, 28, 22, 13, 3 };

	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET,
	};

	private PillagerArmor() {
	}

	/**
	 * End of pillager or vindicator spawn, after the weapon is in hand.
	 * Patrol, raid, mansion, and the summon command all pass through here.
	 * A slot that already has something (a saved set, or a captain banner on the head) is left alone.
	 */
	public static void equipOnSpawn(Mob illager) {
		if (!ENABLED || illager.level().isClientSide()) {
			return;
		}
		RandomSource random = illager.getRandom();
		float chance = ThreatScale.lerp(illager.level(), ThreatTable.ILLAGER_ARMOR_CHANCE_START, WEAR_CHANCE_END);
		if (chance <= 0.0f || random.nextFloat() >= chance) {
			return;
		}
		Item[] set = SETS[pickMaterial(random)];
		boolean woreAny = false;
		for (int i = 0; i < SLOTS.length; i++) {
			EquipmentSlot slot = SLOTS[i];
			ItemStack already = illager.getItemBySlot(slot);
			if (!already.isEmpty()) {
				continue;
			}
			illager.setItemSlot(slot, new ItemStack(set[i]));
			illager.setDropChance(slot, Math.max(0.0f, DROP_CHANCE));
			woreAny = true;
		}
		if (woreAny) {
			dyeLeather(illager, random);
		}
	}

	/** Index into {@link #SETS}. Weights are relative, so 3 against 97 is 3%. */
	private static int pickMaterial(RandomSource random) {
		double total = 0.0;
		for (double weight : MATERIAL_WEIGHTS) {
			total += Math.max(0.0, weight);
		}
		if (total <= 0.0) {
			return 0;
		}
		double roll = random.nextDouble() * total;
		for (int i = 0; i < MATERIAL_WEIGHTS.length; i++) {
			roll -= Math.max(0.0, MATERIAL_WEIGHTS[i]);
			if (roll < 0.0) {
				return i;
			}
		}
		return MATERIAL_WEIGHTS.length - 1;
	}

	private static void dyeLeather(Mob illager, RandomSource random) {
		if (LEATHER_DYE_CHANCE <= 0.0f || random.nextFloat() >= LEATHER_DYE_CHANCE) {
			return;
		}
		int color = randomColor(random);
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = illager.getItemBySlot(slot);
			if (!isLeather(stack)) {
				continue;
			}
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
			illager.setItemSlot(slot, stack);
		}
	}

	private static boolean isLeather(ItemStack stack) {
		for (Item piece : SETS[0]) {
			if (stack.getItem() == piece) {
				return true;
			}
		}
		return false;
	}

	private static int randomColor(RandomSource random) {
		if (LEATHER_USE_DYE_COLORS) {
			DyeColor[] dyes = DyeColor.values();
			return dyes[random.nextInt(dyes.length)].getTextureDiffuseColor() & 0xFFFFFF;
		}
		return random.nextInt(0x1000000);
	}
}
