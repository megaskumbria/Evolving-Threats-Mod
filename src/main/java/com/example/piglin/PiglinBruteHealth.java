package com.example.piglin;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;

/**
 * Здоровье брутального пиглина. В обычной игре у него 50.
 * Обычный пиглин и зомби-пиглин остаются со своим здоровьем.
 */
public final class PiglinBruteHealth {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — снова 50, как в обычной игре. */
	public static final boolean ENABLED = true;

	/**
	 * Максимальное здоровье при появлении. 60 = 30 сердец. В обычной игре 50 (25 сердец).
	 * Полоска сразу полная. Уже живущий брут это число не получает, пока не появится заново.
	 */
	public static final double MAX_HEALTH = 60.0;

	// ========================================================================

	private PiglinBruteHealth() {
	}

	/** Вызвать один раз, когда брут только появился. */
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
