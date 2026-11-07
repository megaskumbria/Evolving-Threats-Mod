package com.example;

import com.example.advancement.AdvancementMoments;
import com.example.advancement.ModAdvancements;
import com.example.threat.ThreatScale;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
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

	/**
	 * How many arrows from one volley must hit the same player.
	 * 2 = at least two. 1 would also count a single ordinary-looking shot.
	 */
	public static final int BARRAGE_HITS_FOR_CARD = 2;

	/** Mark on each arrow of a volley. A normal bow shot does not have it. */
	public static final String BARRAGE_TAG_PREFIX = "evolvingthreats_barrage_";

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

	/** Hits of one volley on one player. Cleared once the card counts. */
	private static final java.util.Map<String, Integer> VOLLEY_HITS = new java.util.HashMap<>();

	private RandomDispenserSkull() {
	}

	/** A volley arrow hit. Other arrows have no mark. One hit is not enough. */
	public static void onArrowHit(AbstractArrow arrow, Entity hit) {
		if (!(hit instanceof ServerPlayer player)) {
			return;
		}
		String mark = null;
		for (String tag : arrow.entityTags()) {
			if (tag.startsWith(BARRAGE_TAG_PREFIX)) {
				mark = tag;
				break;
			}
		}
		if (mark == null) {
			return;
		}
		String key = player.getUUID() + mark;
		int hits = VOLLEY_HITS.merge(key, 1, Integer::sum);
		if (AdvancementMoments.volleyEnough(hits, BARRAGE_HITS_FOR_CARD)) {
			ModAdvancements.onVolley(player);
			VOLLEY_HITS.remove(key);
		}
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
