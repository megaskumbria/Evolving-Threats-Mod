package com.example.vex;

import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;

/**
 * Vexes hit slightly harder than vanilla and sometimes apply Weakness.
 * Extra damage is the same on easy, normal, and hard: vanilla already scales by difficulty;
 * this adds enough so the player loses exactly the configured hearts after that scaling.
 */
public final class VexDamage {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — vanilla vex damage. */
	public static final boolean ENABLED = true;

	/**
	 * Extra hearts removed per hit with no armor.
	 * 1 = one heart (2 HP). 0 = no bonus. 2 = two hearts.
	 * Easy and hard lose the same felt amount: difficulty scaling is compensated below.
	 */
	public static final float EXTRA_HEARTS = 1.0f;

	/** false — no Weakness; extra heart damage only. */
	public static final boolean WEAKNESS_ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a hit that already dealt damage applies Weakness.
	 * 0.20 = 20%, roughly every fifth such hit. 0.25 = every fourth.
	 * Fresh roll each hit. Shield blocks and iframes do not count.
	 * 0 = no Weakness. 1 = every landed hit applies it.
	 */
	public static final float WEAKNESS_CHANCE = 0.20f;

	/**
	 * Weakness duration in seconds. 10 = ten seconds.
	 * A new hit refreshes duration if current Weakness is shorter or equal.
	 * Longer Weakness (e.g. from a witch potion) is not shortened by this hit.
	 * Milk removes the effect immediately. 0 = effect never appears.
	 */
	public static final int WEAKNESS_SECONDS = 10;

	/**
	 * Weakness amplifier. 0 = Weakness I: melee hits weaker by 4 HP (2 hearts).
	 * 1 = Weakness II: 8 HP (4 hearts) weaker; swords often barely scratch.
	 */
	public static final int WEAKNESS_AMPLIFIER = 0;

	// ========================================================================

	/** Two HP per heart. */
	private static final float HP_PER_HEART = 2.0f;

	private VexDamage() {
	}

	/**
	 * Melee damage with bonus. {@code incoming} is what the vex would already deal
	 * (base damage plus iron sword and Sharpness if present).
	 */
	public static float attackDamage(Vex vex, float incoming) {
		if (!ENABLED || EXTRA_HEARTS <= 0.0f) {
			return incoming;
		}
		float feltBonus = EXTRA_HEARTS * HP_PER_HEART;
		if (!(vex.getTarget() instanceof Player)) {
			return incoming + feltBonus;
		}
		return incoming + beforeDifficultyScale(vex.level().getDifficulty(), feltBonus);
	}

	/**
	 * Raw damage to add so that after difficulty scaling the player loses {@code feltHp}.
	 * Easy roughly halves big hits, hard multiplies by 1.5, normal leaves damage unchanged.
	 */
	private static float beforeDifficultyScale(Difficulty difficulty, float feltHp) {
		return switch (difficulty) {
			case EASY -> feltHp * 2.0f;
			case HARD -> feltHp * 2.0f / 3.0f;
			default -> feltHp;
		};
	}

	/**
	 * Hit already dealt damage.
	 * Non-players do not reach here: villagers, golems, and other mobs never get Weakness from this.
	 */
	public static void onHit(Vex vex, LivingEntity target) {
		if (!WEAKNESS_ENABLED || WEAKNESS_CHANCE <= 0.0f || WEAKNESS_SECONDS <= 0) {
			return;
		}
		if (!(target instanceof Player)) {
			return;
		}
		if (vex.getRandom().nextFloat() >= WEAKNESS_CHANCE) {
			return;
		}
		target.addEffect(new MobEffectInstance(
				MobEffects.WEAKNESS,
				WEAKNESS_SECONDS * 20,
				Math.max(0, WEAKNESS_AMPLIFIER),
				false,
				true,
				true), vex);
	}
}
