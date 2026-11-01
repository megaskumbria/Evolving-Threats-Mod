package com.example.piglin;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;

/**
 * Brute piglin max health. Vanilla uses 50.
 * Normal piglins and zombified piglins keep their vanilla health.
 */
public final class PiglinBruteHealth {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — back to vanilla 50. */
	public static final boolean ENABLED = true;

	/**
	 * Max health on spawn. 60 = 30 hearts. Vanilla is 50 (25 hearts).
	 * Bar is full immediately. Brutes already alive keep old health until they respawn.
	 */
	public static final double MAX_HEALTH = 60.0;

	// ========================================================================

	private PiglinBruteHealth() {
	}

	/** Call once when the brute has just spawned. */
	public static void onSpawn(PiglinBrute brute) {
		if (!ENABLED) {
			return;
		}
		var maxHealth = brute.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		maxHealth.setBaseValue(MAX_HEALTH);
		brute.setHealth((float) MAX_HEALTH);
	}
}
