package com.example.sight;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Hostile mobs that normally notice the player only at short range get extended follow range.
 * Mobs that already see far (zombie, blaze, enderman, warden, siege mobs) are not shortened or stretched.
 * Walls still block line of sight: no direct visibility means no target.
 * Active from day one; does not scale with the threat meter.
 */
public final class MobSight {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — follow range is vanilla again. Already spawned mobs are not reverted. */
	public static final boolean ENABLED = true;

	/**
	 * If a mob's vanilla follow range is at most this many blocks, it is raised to {@link #SEE_BLOCKS}.
	 * 16 = skeleton, creeper, spider, witch in vanilla. Vindicator and evoker see from 12 and qualify too.
	 * Values above 16 are left alone: zombie already sees from 35.
	 */
	public static final double RAISE_IF_AT_MOST = 16.0;

	/**
	 * New follow range in blocks. 32 = twice the usual 16.
	 * 24 = slightly farther. 48 = almost blaze range. If less than {@link #RAISE_IF_AT_MOST}, nothing changes.
	 */
	public static final double SEE_BLOCKS = 32.0;

	// ========================================================================

	private MobSight() {
	}

	/** End of spawn. Special mobs may set a higher range afterward. */
	public static void raiseShortSight(Mob mob) {
		if (!ENABLED || SEE_BLOCKS <= RAISE_IF_AT_MOST || mob.getType().getCategory() != MobCategory.MONSTER) {
			return;
		}
		var follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow == null || follow.getBaseValue() > RAISE_IF_AT_MOST) {
			return;
		}
		follow.setBaseValue(SEE_BLOCKS);
	}
}
