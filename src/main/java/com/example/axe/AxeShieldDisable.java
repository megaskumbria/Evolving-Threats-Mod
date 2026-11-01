package com.example.axe;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A blow from a zombie, a skeleton or a brutal piglin with an axe knocks down the shield,
 * as a champion strike: the shield cannot be raised for a few seconds.
 * Wooden axe shield does not knock down.
 *
 * <p>The defender and the brute with a gold axe in the main hand already have the game itself.
 * Here it is the same for an axe in the second hand and a ban for a wooden one.
 * Does not depend on days.
 */
public final class AxeShieldDisable {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — the shield again knocks down only what the game itself can do. Wooden axe in the main hand too. */
	public static final boolean ENABLED = true;

	/**
	 * For how many seconds the shield cannot be lifted after being hit with an axe.
	 * 5 = like a champion and an axe in a normal game. 2 = shorter. 0 = the shield does not shoot down.
	 */
	public static final float COOLDOWN_SECONDS = 5.0f;

	// ========================================================================

	/** Stone, copper, gold, iron, diamond, Netherite. No wood. */
	private static final Item[] AXES = {
			Items.STONE_AXE,
			Items.COPPER_AXE,
			Items.GOLDEN_AXE,
			Items.IRON_AXE,
			Items.DIAMOND_AXE,
			Items.NETHERITE_AXE
	};

	private AxeShieldDisable() {
	}

	/**
	 * How many seconds to give the game when the blow hit the shield.
	 * -1 = this mob is not ours, let the game decide.
	 * 0 = do not knock down the shield (wooden axe).
	 */
	public static float seconds(LivingEntity attacker) {
		if (!ENABLED || COOLDOWN_SECONDS < 0.0f || !applies(attacker)) {
			return -1.0f;
		}
		if (holds(attacker, false)) {
			return COOLDOWN_SECONDS;
		}
		if (holds(attacker, true)) {
			return 0.0f;
		}
		return -1.0f;
	}

	/** Zombies (and its species), skeleton (and its species), brutal piglin. A champion is not included. */
	private static boolean applies(LivingEntity attacker) {
		return attacker instanceof Zombie
				|| attacker instanceof AbstractSkeleton
				|| attacker instanceof PiglinBrute;
	}

	/** {@code wooden} — looking for a wooden axe. Otherwise, any other one from the list. We look at both hands. */
	private static boolean holds(LivingEntity attacker, boolean wooden) {
		return isAxe(attacker.getItemBySlot(EquipmentSlot.MAINHAND), wooden)
				|| isAxe(attacker.getItemBySlot(EquipmentSlot.OFFHAND), wooden);
	}

	private static boolean isAxe(ItemStack stack, boolean wooden) {
		if (stack.isEmpty()) {
			return false;
		}
		if (wooden) {
			return stack.is(Items.WOODEN_AXE);
		}
		for (Item axe : AXES) {
			if (stack.is(axe)) {
				return true;
			}
		}
		return false;
	}
}
