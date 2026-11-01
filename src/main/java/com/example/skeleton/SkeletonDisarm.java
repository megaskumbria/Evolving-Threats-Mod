package com.example.skeleton;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/**
 * Skeleton arrows sometimes knock items out of the player's hands.
 * Items fly forward like a Q drop and cannot be picked up for a couple of seconds.
 * Armor and the rest of the inventory are untouched.
 */
public final class SkeletonDisarm {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — arrows never disarm. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a hitting arrow disarms the hands.
	 * 0.05 = 5%, roughly every twentieth arrow that dealt damage.
	 * 0.02 is barely noticeable in a fight. 0.15 is too often.
	 * 0 = never. 1 = every such arrow.
	 * One roll per hit: either nothing or both occupied hands at once.
	 */
	public static final float DISARM_CHANCE = 0.05f;

	// ========================================================================

	private SkeletonDisarm() {
	}

	/**
	 * Arrow already dealt damage, or the player is in creative and damage did not apply.
	 * Shield blocks and post-hit invulnerability frames do not reach here.
	 * Player and other mob arrows do not count: shooter must be skeleton, stray, or bogged.
	 */
	public static void onArrowHit(AbstractArrow arrow, Player player) {
		if (!ENABLED || DISARM_CHANCE <= 0.0f || !player.isAlive()) {
			return;
		}
		if (!(arrow.level() instanceof ServerLevel) || !(arrow.getOwner() instanceof AbstractSkeleton)) {
			return;
		}
		if (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
			return;
		}
		if (arrow.getRandom().nextFloat() >= DISARM_CHANCE) {
			return;
		}
		throwHand(player, InteractionHand.MAIN_HAND);
		throwHand(player, InteractionHand.OFF_HAND);
	}

	/** Full stack from the hand flies forward. Empty hand drops nothing. */
	private static void throwHand(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			return;
		}
		ItemStack thrown = held.copy();
		player.setItemInHand(hand, ItemStack.EMPTY);
		ItemEntity entity = player.createItemStackToDrop(thrown, false, true);
		if (entity != null) {
			player.level().addFreshEntity(entity);
		}
	}
}
