package com.example.client.skeleton;

import com.example.RandomWeapons;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;

/**
 * The game draws a bow only when it is in the right hand.
 * These skeletons keep the bow in the left hand, so the pose is chosen here.
 * Up close (see RandomWeapons.SKELETON_BOW_SUPPRESS_DISTANCE_BLOCKS) he raises the right-hand weapon instead.
 */
public final class TwoLeadHandsPose {

	private TwoLeadHandsPose() {
	}

	/** True — show the bow draw. False — he is in melee range, so the sword arm stays up. */
	public static boolean showBowPose(AbstractSkeleton skeleton) {
		if (!RandomWeapons.holdsBowInLeftHand(skeleton)) {
			return false;
		}
		Player player = skeleton.level().getNearestPlayer(skeleton, RandomWeapons.SKELETON_BOW_SUPPRESS_DISTANCE_BLOCKS);
		return player == null;
	}
}
