package com.example.skeleton;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;

/**
 * Rare small skeleton: shorter, faster, less health.
 * Bow is normal. Longbow, totem, and dual melee stay adult-only.
 * Burns in sunlight like a normal skeleton; no separate rule here.
 *
 * Size uses the SCALE attribute. The game shrinks model and hitbox together.
 * Health is set here after the global 20–40 roll, so small skeletons keep their own range.
 */
public final class SmallSkeleton {

	// ===================== SETTINGS (change these numbers) =====================

	/** Enable mechanic. false — no new small skeletons. Existing ones stay small. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a normal skeleton or stray spawns small.
	 * 0.10 = 10%. 0 = none.
	 */
	public static final float SPAWN_CHANCE = 0.10f;

	/**
	 * Scale: 1.0 = normal skeleton, 0.5 = half height (like a baby mob).
	 * Below 0.5 — very hard to hit. Above 0.7 — almost adult-sized.
	 */
	public static final double SCALE = 0.5;

	/**
	 * Movement speed multiplier vs normal skeleton. 1.5 = 50% faster, like baby zombies.
	 * 1.0 = adult speed. 2.0 = twice as fast.
	 */
	public static final double SPEED_MULTIPLIER = 1.5;

	/**
	 * Small skeleton health, inclusive range.
	 * Adults in this mod use 20–40. 10–16 = 5 to 8 hearts.
	 */
	public static final int HEALTH_MIN = 10;
	public static final int HEALTH_MAX = 16;

	// ========================================================================

	/** Vanilla skeleton movement speed. Multiplier applies to this base. */
	private static final double VANILLA_MOVEMENT_SPEED = 0.25;

	private SmallSkeleton() {
	}

	/**
	 * Roll spawn chance and shrink the skeleton if it succeeds.
	 * true — this skeleton is small: do not give longbow, totem, or dual melee.
	 */
	public static boolean tryRoll(AbstractSkeleton skeleton) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f || !(skeleton.level() instanceof ServerLevel)) {
			return false;
		}
		if (skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		apply(skeleton);
		return true;
	}

	private static void apply(AbstractSkeleton skeleton) {
		var scale = skeleton.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(SCALE);
		}
		var speed = skeleton.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(VANILLA_MOVEMENT_SPEED * SPEED_MULTIPLIER);
		}
		int low = Math.min(HEALTH_MIN, HEALTH_MAX);
		int high = Math.max(HEALTH_MIN, HEALTH_MAX);
		int health = low + skeleton.getRandom().nextInt(high - low + 1);
		var maxHealth = skeleton.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(health);
			skeleton.setHealth(health);
		}
	}
}
