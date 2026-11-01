package com.example.illager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.List;

/**
 The destroyer punches the wall with a blow of the head, if because of it there is no way to the goal.
 One blow immediately demolishes soft blocks in the opening. Stone and all that hard.
 * cracks and falls from the second impact. Obsidian doesn't take a thigh.
 */
public final class RavagerWallBreak {

	// ===================== SETTINGS (change these numbers) =====================

	/*False - the walls do not break, walks like in a normal game. */
	public static final boolean ENABLED = true;

	/**
	 Minimum pause between head impacts, in seconds.
	 * 20 = twenty seconds. After hitting, the pause is random, from this number to {@link #COOLDOWN MAX SECONDS}.
	 */
	public static final int COOLDOWN_MIN_SECONDS = 20;

	/**
	 Maximum pause between head impacts, in seconds.
	 * 25 = twenty-five seconds. If it is the minimum, the pause is always the same.
	 */
	public static final int COOLDOWN_MAX_SECONDS = 25;

	/**
	 How many strokes does the stone need? 2 = the first blow only cracks, the second breaks.
	 1 = the stone falls as soon as the earth. And there's a cobblestone, a deep slate.
	 bricks, ores and metal blocks: they are broken with a pickaxe and they are not softer than stone.
	 */
	public static final int STONE_HITS = 2;

	/**
	 * Block width. 3 = block directly in front of the muzzle, one on the left and one on the right.
	 The carcass of the destroyer is wider than one block, it does not fit into the hole from one block.
	 * 1 = central column only.
	 */
	public static final int HOLE_WIDTH = 3;

	/**
	 The height of the opening in the blocks, below the legs. 3 = legs, body and head.
	 The growth of the destroyer is more than two blocks, the opening in two blocks is low.
	 */
	public static final int HOLE_HEIGHT = 3;

	/**
	 How many blocks does the strike look forward to? 2 = wall close or block further.
	 Only the nearest layer breaks, not the entire thickness at once. It doesn't hit the wall any further.
	 */
	public static final int REACH = 2;

	/*Next to this (blocks) from the target wall does not break. 32 = how far he sees the target. */
	public static final double MAX_DISTANCE_TO_TARGET = 32.0;

	/*How often do you check if the path has opened? 10 tics = half a second. */
	public static final int CHECK_INTERVAL_TICKS = 10;

	/**
	 * Whether the block itself falls out. False - the wall disappears, boards and stone do not fall to the ground.
	 Things from the chest, barrels and ovens still fall out: the block breaks, the contents remain to pick up.
	 */
	public static final boolean DROP_ITEMS = false;

	/**
	 * True - the command /gamerule mobGriefing false prohibits breaking through walls.
	 * False - breaks even when the rule is turned off.
	 */
	public static final boolean RESPECT_MOB_GRIEFING = true;

	// ========================================================================

	/** Hardness of ordinary stone. A pickaxe block is not softer than this is considered a stone. */
	private static final float STONE_HARDNESS = 1.5f;

	/** 20 ticks in one second. */
	private static final int TICKS_PER_SECOND = 20;

	private RavagerWallBreak() {
	}

	public static boolean canBreak(Ravager ravager) {
		if (!ENABLED) {
			return false;
		}
		if (RESPECT_MOB_GRIEFING
				&& !(ravager.level() instanceof ServerLevel level && level.getGameRules().get(GameRules.MOB_GRIEFING))) {
			return false;
		}
		return true;
	}

	/*Accidental pause after impact, in ticks. */
	public static int cooldownTicks(Ravager ravager) {
		int min = Math.max(0, COOLDOWN_MIN_SECONDS);
		int max = Math.max(min, COOLDOWN_MAX_SECONDS);
		int seconds = min + ravager.getRandom().nextInt(max - min + 1);
		return seconds * TICKS_PER_SECOND;
	}

	/**
	 The nearest layer of the wall before the destroyer. Empty list - there is nothing to break ahead
	 * (air, obsidian or thigh). The chest, barrel and stove break like a normal wall.
	 */
	public static List<BlockPos> nearestWall(Level level, BlockPos feet, Direction forward) {
		if (forward.getAxis().isVertical()) {
			return List.of();
		}
		for (int depth = 1; depth <= Math.max(1, REACH); depth++) {
			BlockPos center = feet.relative(forward, depth);
			if (!layerBlocksMovement(level, center, forward)) {
				continue;
			}
			// First solid layer. Obsidian remains in it, only the rest breaks down.
			// Through obsidian to the blocks further impact does not pass.
			return layer(level, center, forward);
		}
		return List.of();
	}

	/**
	 How many punches does this block need?
	 * 0 = do not touch: air, obsidian, thigh and non-breakable.
	 Chest and barrel, one punch. The stove is hard as a rock: it needs {@link #STONE HITS}.
	 */
	public static int hitsNeeded(Level level, BlockPos pos, BlockState state) {
		if (!isObstacle(level, pos, state) || isNeverBroken(state)) {
			return 0;
		}
		if (STONE_HITS <= 1) {
			return 1;
		}
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.getDestroySpeed(level, pos) >= STONE_HARDNESS) {
			return STONE_HITS;
		}
		return 1;
	}

	/*Block prevents to pass: there is a collision. The hip and the barrier, too, although they cannot be broken. */
	private static boolean blocksMovement(Level level, BlockPos pos, BlockState state) {
		return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
	}

	/*Wall, fence, earth, wood: prevents to pass and the block can be broken. */
	private static boolean isObstacle(Level level, BlockPos pos, BlockState state) {
		if (!blocksMovement(level, pos, state)) {
			return false;
		}
		return state.getDestroySpeed(level, pos) >= 0.0f;
	}

	/*Obsidian, crying obsidian and thigh. The barrier and reinforced deep slate are cut off with a hardness below zero. */
	private static boolean isNeverBroken(BlockState state) {
		return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.BEDROCK);
	}

	/**
	 Breaks the block. The block itself does not fall if {@link #DROP ITEMS} is off.
	 The chest, barrel and stove at the same time pour things onto the ground, otherwise the contents would disappear with the block.
	 */
	public static void breakBlock(Level level, BlockPos pos, Ravager ravager) {
		if (!DROP_ITEMS && level.getBlockEntity(pos) instanceof Container container) {
			Containers.dropContents(level, pos, container);
		}
		level.destroyBlock(pos, DROP_ITEMS, ravager);
	}

	/*Is there at least one block in this layer that prevents you from passing, even obsidian? */
	private static boolean layerBlocksMovement(Level level, BlockPos center, Direction forward) {
		Direction left = forward.getCounterClockWise();
		int half = Math.max(0, HOLE_WIDTH - 1) / 2;
		int extra = Math.max(0, HOLE_WIDTH - 1 - half);
		int height = Math.max(1, HOLE_HEIGHT);
		for (int side = -half; side <= extra; side++) {
			BlockPos column = center.relative(left, side);
			for (int dy = 0; dy < height; dy++) {
				BlockPos pos = column.above(dy);
				if (blocksMovement(level, pos, level.getBlockState(pos))) {
					return true;
				}
			}
		}
		return false;
	}

	private static List<BlockPos> layer(Level level, BlockPos center, Direction forward) {
		Direction left = forward.getCounterClockWise();
		int half = Math.max(0, HOLE_WIDTH - 1) / 2;
		int extra = Math.max(0, HOLE_WIDTH - 1 - half);
		int height = Math.max(1, HOLE_HEIGHT);
		List<BlockPos> blocks = new ArrayList<>();
		for (int side = -half; side <= extra; side++) {
			BlockPos column = center.relative(left, side);
			for (int dy = 0; dy < height; dy++) {
				BlockPos pos = column.above(dy);
				BlockState state = level.getBlockState(pos);
				if (hitsNeeded(level, pos, state) > 0) {
					blocks.add(pos);
				}
			}
		}
		return blocks;
	}
}
