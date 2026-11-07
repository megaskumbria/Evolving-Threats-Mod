package com.example.client.skeleton;

import com.example.RandomWeapons;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;

/**
 * The game draws a bow only when it is in the right hand, and only while the skeleton is aggressive.
 * These skeletons keep the bow in the left hand, so the pose is chosen here.
 * Idle (not aggressive): both arms stay down. A bow pose with no target sticks both arms straight
 * forward, and he looks like a zombie.
 * Up close (see RandomWeapons.SKELETON_BOW_SUPPRESS_DISTANCE_BLOCKS) the sword pose is left to the game.
 */
public final class TwoLeadHandsPose {

	private TwoLeadHandsPose() {
	}

	/**
	 * True — he has a target and the player is not in melee range, so show the bow draw.
	 * False — arms down (idle) or the sword pose (player is close).
	 */
	public static boolean showBowPose(AbstractSkeleton skeleton) {
		// Same gate as a normal skeleton: the bow arm comes up only while he is actually fighting.
		if (!skeleton.isAggressive() || !RandomWeapons.holdsBowInLeftHand(skeleton)) {
			return false;
		}
		Player player = skeleton.level().getNearestPlayer(skeleton, RandomWeapons.SKELETON_BOW_SUPPRESS_DISTANCE_BLOCKS);
		return player == null;
	}
}
