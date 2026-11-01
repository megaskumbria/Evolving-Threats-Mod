package com.example;

import com.example.threat.ThreatScale;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 A skeleton with a spreader on the head is a rare option with a volley of arrows on a timer.
 Compatible with armor, dual-wield, etc.; the distributor occupies the HEAD slot (overlapping the helmet).
 */
public final class RandomDispenserSkull {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * Chance (0.0–1.0) that the skeleton will have a dispenser on its head on day 100
	 * (GROWING: 10 times smaller at the beginning of the game, see ThreatTable.DANGEROUS_START_RATIO). 0.02 = 2%.
	 */
	public static final float SKELETON_DISPENSER_HEAD_CHANCE = 0.02f;

	/*Minimal pause between volleys, seconds. */
	public static final int BARRAGE_COOLDOWN_MIN_SECONDS = 15;

	/*Maximum pause between volleys, seconds. */
	public static final int BARRAGE_COOLDOWN_MAX_SECONDS = 20;

	/*How many arrows are fired in one volley? */
	public static final int BARRAGE_ARROW_COUNT = 14;

	/*Each N tics (20 tics = 1 sec) is released another arrow in the volley. 2 = ~7 page/sec. */
	public static final int BARRAGE_SHOOT_INTERVAL_TICKS = 2;

	/** Duration of the volley(s) phase. If the arrows ran out earlier, the phase can still go to the end. */
	public static final int BARRAGE_PHASE_TICKS = 40;

	/*Speed of arrows (the more - the faster they fly). */
	public static final float BARRAGE_ARROW_VELOCITY = 1.35f;

	/**
	 Dispersion (like vanilla onions): the more - the less accurate.
	 12-18: “A lot of arrows, but not a sniper.”
	 */
	public static final float BARRAGE_INACCURACY = 14.0f;

	/** Chance of a dispenser drop from the head at death. */
	public static final float DISPENSER_HEAD_DROP_CHANCE = 0.085f;

	// ========================================================================

	private RandomDispenserSkull() {
	}

	public static boolean isDispenserSkull(Mob mob) {
		return mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.DISPENSER);
	}

	public static void tryEquipDispenserHead(Mob mob) {
		if (mob.getRandom().nextFloat() >= ThreatScale.dangerousChance(mob.level(), SKELETON_DISPENSER_HEAD_CHANCE)) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DISPENSER));
		mob.setDropChance(EquipmentSlot.HEAD, DISPENSER_HEAD_DROP_CHANCE);
	}

	public static int randomCooldownTicks(RandomSource random) {
		int minTicks = BARRAGE_COOLDOWN_MIN_SECONDS * 20;
		int maxTicks = BARRAGE_COOLDOWN_MAX_SECONDS * 20;
		if (maxTicks <= minTicks) {
			return minTicks;
		}
		return random.nextIntBetweenInclusive(minTicks, maxTicks);
	}
}
