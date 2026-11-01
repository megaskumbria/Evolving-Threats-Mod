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
 * Random slot armor. Each slot (helmet, breastplate, pants, shoes)
 * is thrown separately - so there is a "diamond breastplate + leather pants".
 Separately, there is a rare complete set of one material: it grows with a threat scale.
 There's no nonseritic armor here.
 */
public final class RandomArmor {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * Chance (0.0–1.0) for EVERY Zombies Armor Slot on Day 100 (end of threat scale).
	 0.75 = 75% on the helmet, 75% on the breastplate, etc. (regardless of each other).
	 At the beginning of the game, the chance is smaller (ThreatTable.ARMOR PIECE CHANCE START) and increases with the scale and the moon.
	 */
	public static final float ZOMBIE_ARMOR_PIECE_CHANCE = 0.75f;

	/*Same for normal skeleton and wanderer (end of scale). */
	public static final float SKELETON_ARMOR_PIECE_CHANCE = 0.75f;

	/**
	 Nonserite armor is not used for spawning (only leather ... diamond).
	 Constant for clarity; do not change until you add Neserite to ARMOR PIECES.
	 */
	public static final boolean ALLOW_NETHERITE_ARMOR = false;

	/**
	 * Chance (0.0-1.0) full set on the 100th day: helmet, breastplate, pants and shoes of the same material.
	 0.02 = 2% zombies or skeletons At the beginning of the game there is no chance (ThreatTable.FULL ARMOR SET CHANCE START = 0)
	 And it grows with the scale and the moon. 0 = no such mobs even at the end.
	 * The material inside the kit - equally: leather, gold, mail, copper, iron or diamond.
	 Ordinary armor in pieces, if the set did not fall out, is considered separately.
	 */
	public static final float FULL_SET_CHANCE = 0.02f;

	/**
	 * Chance (0.0-1.0) that a mob with leather armor will be in dyed leather armor. 0.50 = 50%.
	 Thrown once on the mob, not on each piece: so the shape is whole.
	 Only the skin is painted (the rest of the armor in the game is not painted). 0 = The skin is always normal brown.
	 */
	public static final float LEATHER_DYE_CHANCE = 0.50f;

	/**
	 * Chance (0.0–1.0) that a single piece of leather armor from a dyed mob will have its own color, rather than the total.
	 0.25 = approximately one in four pieces in a different color. 0 = the entire set of strictly one color.
	 */
	public static final float LEATHER_PIECE_OWN_COLOR_CHANCE = 0.25f;

	/**
	 Where to get the color.
	 False = any of 16,777,216 shades (full range; dull gray-brown)
	 True = only one of the 16 colors of Minecraft dyes (pure bright colors).
	 If the full range is “dirty”, put true.
	 */
	public static final boolean LEATHER_USE_ONLY_DYE_COLORS = false;

	/**
	 * Chances that a fallen item of armor will be removed from the mob at death (like vanilla mobs).
	 * 0.085 - 8.5%. Put on 1.0f if you want to keep your armor down.
	 */
	public static final float ARMOR_DROP_CHANCE = 0.085f;

	// ========================================================================

	/*Order: helmet, breastplate, knives, shoes. Lines are material (see MATERIAL WEIGHTS). */
	private static final Item[][] ARMOR_PIECES = {
		{ Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS },
		{ Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS },
		{ Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
		{ Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS },
		{ Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS },
		{ Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS },
	};

	/**
	 * Material weight at day 100 (end of scale) At the beginning of the game, the weights are different:
	 ThreatTable. ARMOR MATERIAL WEIGHTS START. Between them is a smooth transition on the scale of threat and the moon.
	 There is no Neserite: only Vasilisk has it.
	 */
	private static final int[] MATERIAL_WEIGHTS = {
		10, // skin
		5, // gold
		10, // mail
		8, // copper
		32, // iron
		35, // Diamond (35% of all armor worn on day 100)
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
	 Is this a piece of armor from our list (skin...diamond)? It is necessary that the charms do not climb on special "heads"
	 * (spawner, pumpkin beacon, dispenser), which lie in the same slot of the helmet.
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

	/**
	 * Zombie armor. True = worn full set: helmet not to replace pumpkin or spawner.
	 */
	public static boolean rollForZombie(Mob mob) {
		if (tryFullSet(mob)) {
			return true;
		}
		rollPieces(mob, pieceChance(mob, ZOMBIE_ARMOR_PIECE_CHANCE));
		return false;
	}

	/**
	 Armor of the skeleton and the wanderer. True = Wear the full set: the helmet does not replace.
	 */
	public static boolean rollForSkeleton(Mob mob) {
		if (tryFullSet(mob)) {
			return true;
		}
		rollPieces(mob, pieceChance(mob, SKELETON_ARMOR_PIECE_CHANCE));
		return false;
	}

	/**
	 * Rare single set. true = all four slots are occupied by the same material
	 * There is no need to make a regular throw on pieces.
	 */
	private static boolean tryFullSet(Mob mob) {
		float chance = ThreatScale.lerp(mob.level(), ThreatTable.FULL_ARMOR_SET_CHANCE_START, FULL_SET_CHANCE);
		RandomSource random = mob.getRandom();
		if (chance <= 0.0f || random.nextFloat() >= chance) {
			return false;
		}
		int materialIndex = random.nextInt(ARMOR_PIECES.length);
		for (int slotIndex = 0; slotIndex < SLOTS.length; slotIndex++) {
			EquipmentSlot slot = SLOTS[slotIndex];
			mob.setItemSlot(slot, new ItemStack(ARMOR_PIECES[materialIndex][slotIndex]));
			mob.setDropChance(slot, ARMOR_DROP_CHANCE);
		}
		dyeLeather(mob, random);
		return true;
	}

	/*GROWING: Slot chance between ThreatTable. ARMOR PIECE CHANCE START (day 0) and {@code endChance} (day 100). */
	private static float pieceChance(Mob mob, float endChance) {
		return ThreatScale.lerp(mob.level(), ThreatTable.ARMOR_PIECE_CHANCE_START, endChance);
	}

	/**
	 One random piece in the already selected slot. Material as in ordinary armor moba (skin...diamond).
	 Spiky calls it if the prickly slot is empty: without a piece of armor, there is nothing to prick.
	 * What is already worn in other slots does not touch.
	 */
	public static void equipRandomPiece(Mob mob, EquipmentSlot slot) {
		int slotIndex = -1;
		for (int i = 0; i < SLOTS.length; i++) {
			if (SLOTS[i] == slot) {
				slotIndex = i;
				break;
			}
		}
		if (slotIndex < 0) {
			return;
		}
		RandomSource random = mob.getRandom();
		Item armorItem = ARMOR_PIECES[pickMaterialIndex(mob, random)][slotIndex];
		mob.setItemSlot(slot, new ItemStack(armorItem));
		mob.setDropChance(slot, ARMOR_DROP_CHANCE);
	}

	/**
	 Only a helmet, with the same chance and the same materials as a helmet slot on a normal skeleton.
	 Breast, pants and shoes do not touch. Neserite's not on the list.
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
	 Paints leather pieces on the mob (including the ones the game gave out): one common color per set,
	 Sometimes a single piece gets its own. The rest of the material doesn't touch. The color remains on the fallen armor.
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

	/*Is it a leather helmet/breather/pants/shoes? (The first line of the ARMOR PIECES list is leather.) */
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

	/*Random color RGB: from 16 dyes or any of 16,777,216 (see LEATHER USE ONLY DYE COLORS). */
	private static int randomLeatherColor(RandomSource random) {
		if (LEATHER_USE_ONLY_DYE_COLORS) {
			DyeColor[] dyes = DyeColor.values();
			return dyes[random.nextInt(dyes.length)].getTextureDiffuseColor() & 0xFFFFFF;
		}
		return random.nextInt(0x1000000);
	}

	/*Material by weights that change smoothly from the beginning of the threat scale to the end. */
	private static int pickMaterialIndex(Mob mob, RandomSource random) {
		return ThreatScale.pickWeighted(mob.level(), random, ThreatTable.ARMOR_MATERIAL_WEIGHTS_START, MATERIAL_WEIGHTS);
	}
}
