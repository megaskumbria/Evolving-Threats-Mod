package com.example.illager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/**
 A champion cuts a tree if it stands between him and the goal.
 * Fence, gate, door, hatch, boards, logs, steps, plates, plates.
 Stone, Nezer fence and the ground does not touch.
 There is no chest and barrel either: otherwise things would disappear inside.
 */
public final class VindicatorWoodBreak {

	// ===================== SETTINGS (change these numbers) =====================

	/** False - the champion does not cut a tree. Doors in the raid the game breaks in its own way. */
	public static final boolean ENABLED = true;

	/**
	 Share of the speed of a player with the same axe. 0.2 = 20%.
	 The material of the axe is already within this speed: iron is faster than the hand, diamond is faster than iron.
	 Efficiency on the axe also accelerates, sharpness - no, it is only about the blow.
	 Oak fence (hardness 2): iron axe about 2.5 seconds, diamond about 1.9 seconds.
	 * 1.0 = as a player. 0 = the tree does not break.
	 */
	public static final float SPEED_MULTIPLIER = 0.2f;

	/** Whether the tree falls out. False: The block just disappears. */
	public static final boolean DROP_ITEMS = false;

	/** Beyond this (blocks) from the target fence is not cut. */
	public static final double MAX_DISTANCE_TO_TARGET = 32.0;

	/*How often do you check if the path has opened? 10 tics = half a second. */
	public static final int CHECK_INTERVAL_TICKS = 10;

	/**
	 How many tics the crack does not shed if the champion is distracted and returned.
	 * 100 = 5 seconds.
	 */
	public static final int KEEP_PROGRESS_TICKS = 100;

	/** Arm range (blocks). Beyond that, the block doesn't cut. */
	public static final double REACH = 3.5;

	/**
	 * True - the command /gamerule mobGriefing false prohibits cutting.
	 * False - cut even when the rule is turned off.
	 */
	public static final boolean RESPECT_MOB_GRIEFING = true;

	// ========================================================================

	private VindicatorWoodBreak() {
	}

	public static boolean canChop(Vindicator vindicator) {
		if (!ENABLED || SPEED_MULTIPLIER <= 0.0f) {
			return false;
		}
		if (RESPECT_MOB_GRIEFING
				&& !(vindicator.level() instanceof ServerLevel level && level.getGameRules().get(GameRules.MOB_GRIEFING))) {
			return false;
		}
		return true;
	}

	/*A tree that prevents you from passing: there is a collision and it is a wooden block. */
	public static boolean isWoodenObstacle(BlockGetter level, BlockPos pos, BlockState state) {
		if (!isWood(state) || state.getCollisionShape(level, pos).isEmpty()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0f;
	}

	/**
	 * Boards, logs, wooden fence and gate, door, hatch, steps, plates,
	 * button, stove, shelf, sign, bamboo.
	 */
	public static boolean isWood(BlockState state) {
		return state.is(BlockTags.PLANKS)
				|| state.is(BlockTags.LOGS)
				|| state.is(BlockTags.WOODEN_FENCES)
				|| state.is(BlockTags.FENCE_GATES)
				|| state.is(BlockTags.WOODEN_DOORS)
				|| state.is(BlockTags.WOODEN_TRAPDOORS)
				|| state.is(BlockTags.WOODEN_STAIRS)
				|| state.is(BlockTags.WOODEN_SLABS)
				|| state.is(BlockTags.WOODEN_BUTTONS)
				|| state.is(BlockTags.WOODEN_PRESSURE_PLATES)
				|| state.is(BlockTags.WOODEN_SHELVES)
				|| state.is(BlockTags.ALL_SIGNS)
				|| state.is(BlockTags.BAMBOO_BLOCKS);
	}

	/**
	 Progress in one tick, 1.0 = block broken.
	 * Formula like a player: axe speed / hardness / 30, then {@link #SPEED MULTIPLIER}
	 */
	public static float progressPerTick(Vindicator vindicator, BlockGetter level, BlockPos pos, BlockState state) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness <= 0.0f) {
			return 1.0f;
		}
		ItemStack axe = vindicator.getMainHandItem();
		float toolSpeed = Math.max(1.0f, axe.getDestroySpeed(state));
		boolean canHarvest = !state.requiresCorrectToolForDrops() || axe.isCorrectToolForDrops(state);
		float perTick = toolSpeed / hardness / (canHarvest ? 30.0f : 100.0f);
		return perTick * SPEED_MULTIPLIER;
	}
}
