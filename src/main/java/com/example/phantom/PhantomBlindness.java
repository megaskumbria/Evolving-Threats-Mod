package com.example.phantom;

import com.example.entity.BlindingPhantomEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;

/**
 Part of the phantom hangs blindness when the bite relieved health.
 He who can, is decided once upon appearance, apart from weakness.
 One phantom can do both, one thing or nothing.
 A shield that repulsed the blow, and a short blink after the blow do not give blindness.
 The chance does not grow with the days: what is written below, and remains.
 */
public final class PhantomBlindness {

	// ===================== SETTINGS (change these numbers) =====================

	/*False - Bite again without blindness, even in already labeled phantoms. Weakness remains. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0-1.0) that the phantom can hang blindness when it appears.
	 * 0.20 = 20%, approximately one in five. Throw one and separate from weakness:
	 Night pack, egg and team spawn.
	 This phantom blinds every bite that has passed. The rest never.
	 The days of peace do not depend. 0 = no blindness. 1 = each phantom hangs it.
	 */
	public static final float SPAWN_CHANCE = 0.20f;

	/**
	 Blind, seconds. A random number from less to more inclusive.
	 *7 and 12 = 7 to 12 seconds. The screen is getting dark, the phantom is not visible.
	 Repeated bite updates the time if the player's blindness is already shorter or the same.
	 Longer blindness (for example, from a potion) is not shortened by this bite.
	 Milk removes the effect immediately. Both numbers 0 or less = no effect.
	 */
	public static final int BLINDNESS_MIN_SECONDS = 7;
	public static final int BLINDNESS_MAX_SECONDS = 12;

	// ========================================================================

	private PhantomBlindness() {
	}

	/** Once when a phantom appears. Repeated call already marked does not throw the chance. */
	public static void rollOnSpawn(Phantom phantom) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f || !(phantom instanceof BlindingPhantomEntity data)) {
			return;
		}
		if (data.evolvingthreats$blindsOnBite()) {
			return;
		}
		if (phantom.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		data.evolvingthreats$setBlindsOnBite(true);
	}

	/**
	 The bite has taken its toll.
	 The player does not get here: golem, animal and other mob do not get blindness.
	 */
	public static void onBite(Phantom phantom, LivingEntity target) {
		if (!ENABLED || !(target instanceof Player)) {
			return;
		}
		if (!(phantom instanceof BlindingPhantomEntity data) || !data.evolvingthreats$blindsOnBite()) {
			return;
		}
		int seconds = rollSeconds(phantom);
		if (seconds <= 0) {
			return;
		}
		target.addEffect(new MobEffectInstance(
				MobEffects.BLINDNESS,
				seconds * 20,
				0,
				false,
				true,
				true), phantom);
	}

	/*The smaller and larger of the two settings. If both are no more than zero, there is no blindness. */
	private static int rollSeconds(Phantom phantom) {
		int low = Math.min(BLINDNESS_MIN_SECONDS, BLINDNESS_MAX_SECONDS);
		int high = Math.max(BLINDNESS_MIN_SECONDS, BLINDNESS_MAX_SECONDS);
		if (high <= 0) {
			return 0;
		}
		if (low < 0) {
			low = 0;
		}
		if (high <= low) {
			return high;
		}
		return low + phantom.getRandom().nextInt(high - low + 1);
	}
}
