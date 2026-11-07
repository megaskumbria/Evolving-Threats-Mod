package com.example.skeleton;

import com.example.threat.ThreatScale;

import com.example.advancement.AdvancementMoments;
import com.example.advancement.ModAdvancements;
import com.example.entity.FastBowSkeletonEntity;
import com.example.mixin.RangedBowAttackGoalAccessor;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;

/**
 * Skeleton with a long-range fast bow. Vanilla sees the player at 16 blocks and shoots to 15.
 * This one notices farther and releases fully charged arrows more often.
 */
public final class FastBowSkeleton {

	// ===================== SETTINGS (change these numbers) =====================

	/** Enable mechanic. false — no new fast-bow skeletons; existing ones shoot normally. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a bow skeleton or stray shoots farther and faster.
	 * 0.15 = 15% on day 100 (GROWING: 10× lower at start, see ThreatTable.DANGEROUS_START_RATIO).
	 * Rolled after weapons: no bow (dual melee) — roll is skipped.
	 * 0 = none.
	 */
	public static final float SPAWN_CHANCE = 0.15f;

	/**
	 * Range at which it stops and shoots, blocks.
	 * Vanilla skeleton 15. 30 = twice as far. Higher = opens fire from farther away.
	 */
	public static final float ATTACK_RANGE_BLOCKS = 30.0f;

	/**
	 * How far a normal skeleton bow still reaches, blocks.
	 * A hit past this is the card. Exactly this far is still a normal shot.
	 */
	public static final float VANILLA_BOW_BLOCKS = 15.0f;

	/**
	 * Follow range — how far it spots and keeps the player, blocks.
	 * Vanilla skeleton 16. Should be ≥ bow range or it loses targets it could hit.
	 * 36 = slightly beyond 30-block bow range.
	 */
	public static final double FOLLOW_RANGE = 36.0;

	/**
	 * Bow draw ticks before release (20 = 1 second).
	 * Vanilla skeleton 20. 10 = half a second. Arrow still leaves at full power, not weak.
	 */
	public static final int DRAW_TICKS = 10;

	/**
	 * Post-shot pause on hard difficulty, ticks. Vanilla skeleton 20 (1 s).
	 * 10 = half a second. With 10-tick draw, ~one shot per second instead of every two.
	 */
	public static final int HARD_PAUSE_TICKS = 10;

	/**
	 * Post-shot pause on easy and normal, ticks.
	 * Vanilla skeleton 40 (2 s). 20 = 1 second.
	 */
	public static final int OTHER_PAUSE_TICKS = 20;

	/**
	 * Arrow speed. Vanilla skeleton 1.6. 2.4 = 1.5× faster
	 * so arrows reach the new range instead of dropping short.
	 * Aim adjusts automatically: faster arrow — less upward arc.
	 */
	public static final float ARROW_SPEED = 2.4f;

	/** Vanilla skeleton arrow speed. Used for aim calculation. */
	private static final float VANILLA_ARROW_SPEED = 1.6f;

	/** Vanilla upward arc tuned for speed 1.6. */
	private static final float VANILLA_ARC = 0.2f;

	// ========================================================================

	private FastBowSkeleton() {
	}

	/**
	 * Upward arc for arrows. Not a separate tunable: derived from {@link #ARROW_SPEED}.
	 * At 1.6 → 0.2 like vanilla. At 2.4 → lower arc to hit the body, not over the head.
	 */
	public static float arrowArc() {
		if (ARROW_SPEED <= 0.01f) {
			return VANILLA_ARC;
		}
		return (float) (VANILLA_ARC * Math.pow(VANILLA_ARROW_SPEED / ARROW_SPEED, 1.25));
	}

	/** Call on spawn after weapons are assigned. */
	public static void tryRoll(AbstractSkeleton skeleton) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f) {
			return;
		}
		if (!skeleton.isHolding(Items.BOW)) {
			return;
		}
		if (!(skeleton instanceof FastBowSkeletonEntity data)) {
			return;
		}
		if (skeleton.getRandom().nextFloat() >= ThreatScale.dangerousChance(skeleton.level(), SPAWN_CHANCE)) {
			return;
		}
		data.evolvingthreats$setFastBow(true);
	}

	/** Apply range and pause. Call after the game re-selects ranged bow goal. */
	public static void apply(AbstractSkeleton skeleton, RangedBowAttackGoal<?> bowGoal) {
		if (!(skeleton instanceof FastBowSkeletonEntity data) || !data.evolvingthreats$isFastBow()) {
			return;
		}
		var follow = skeleton.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(FOLLOW_RANGE);
		}
		int pause = skeleton.level().getDifficulty() == Difficulty.HARD ? HARD_PAUSE_TICKS : OTHER_PAUSE_TICKS;
		bowGoal.setMinAttackInterval(pause);
		((RangedBowAttackGoalAccessor) bowGoal).evolvingthreats$setAttackRadiusSqr(ATTACK_RANGE_BLOCKS * ATTACK_RANGE_BLOCKS);
	}

	/** Arrow already hit. A shot from within a normal bow's reach does not count. */
	public static void onArrowHit(AbstractArrow arrow, Entity hit) {
		if (!ENABLED || !(hit instanceof ServerPlayer player)) {
			return;
		}
		if (!(arrow.getOwner() instanceof FastBowSkeletonEntity data) || !data.evolvingthreats$isFastBow()) {
			return;
		}
		Entity owner = arrow.getOwner();
		if (owner != null && AdvancementMoments.fartherThanNormalBow(owner.distanceTo(player), VANILLA_BOW_BLOCKS)) {
			ModAdvancements.onFarShot(player);
		}
	}
}
