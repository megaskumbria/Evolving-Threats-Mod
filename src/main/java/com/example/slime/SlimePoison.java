package com.example.slime;

import com.example.advancement.ModAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;

/**
 * Slime melee hits can poison the player.
 * Large slimes — Poison II, medium — Poison I. Small slimes never apply poison.
 * Magma cubes are excluded: not a slime, they use their own attack.
 */
public final class SlimePoison {

	// ===================== SETTINGS (change these numbers) =====================

	/** Enable poison. false — slime hits deal damage only, no poison. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a large slime hit applies Poison II.
	 * 0.30 = 30%. One roll per hit that actually landed (shield blocks and iframes do not count).
	 * 0 = large slimes never poison.
	 */
	public static final float LARGE_CHANCE = 0.30f;

	/**
	 * Chance (0.0–1.0) that a medium slime hit applies Poison I.
	 * 0.20 = 20%. 0 = medium slimes never poison.
	 */
	public static final float MEDIUM_CHANCE = 0.20f;

	/**
	 * Poison duration in seconds. 15 = fifteen seconds.
	 * Same for large and medium. 0 = effect never appears.
	 */
	public static final int POISON_SECONDS = 15;

	/**
	 * Size value for a medium slime. 2 = medium, as in vanilla.
	 * 1 and below = small: no poison even if chance is above zero.
	 */
	public static final int MEDIUM_SIZE = 2;

	/**
	 * From this size upward the slime counts as large.
	 * Vanilla large is size 4. Size 3+ also gets Poison II.
	 */
	public static final int LARGE_MIN_SIZE = 3;

	/** Effect strength: 0 = Poison I, 1 = Poison II. */
	public static final int MEDIUM_AMPLIFIER = 0;

	/** Large slime effect strength. 1 = Poison II. */
	public static final int LARGE_AMPLIFIER = 1;

	// ========================================================================

	private SlimePoison() {
	}

	/** Hit already landed. Small slime, magma cube, and non-players do not reach here. */
	public static void onHit(Slime slime, LivingEntity target) {
		if (!ENABLED || !(target instanceof Player)) {
			return;
		}
		if (POISON_SECONDS <= 0) {
			return;
		}
		int size = slime.getSize();
		if (size >= LARGE_MIN_SIZE) {
			tryPoison(slime, target, LARGE_CHANCE, LARGE_AMPLIFIER);
		} else if (size == MEDIUM_SIZE) {
			tryPoison(slime, target, MEDIUM_CHANCE, MEDIUM_AMPLIFIER);
		}
	}

	private static void tryPoison(Slime slime, LivingEntity target, float chance, int amplifier) {
		if (chance <= 0.0f || slime.getRandom().nextFloat() >= chance) {
			return;
		}
		boolean applied = target.addEffect(new MobEffectInstance(
				MobEffects.POISON,
				POISON_SECONDS * 20,
				Math.max(0, amplifier),
				false,
				true,
				true), slime);
		if (applied && target instanceof ServerPlayer serverPlayer) {
			ModAdvancements.onSlimePoison(serverPlayer);
		}
	}
}
