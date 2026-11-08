package com.example.illager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

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
	 * 10 = ten seconds. After hitting, the pause is random, from this number to {@link #COOLDOWN_MAX_SECONDS}.
	 */
	public static final int COOLDOWN_MIN_SECONDS = 10;

	/**
	 Maximum pause between head impacts, in seconds.
	 * 15 = fifteen seconds. If it equals the minimum, the pause is always the same.
	 */
	public static final int COOLDOWN_MAX_SECONDS = 15;

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

	/**
	 * True — a target hidden by a wall the ravager can break is not forgotten.
	 * He keeps walking up and headbutts until he can see the target again.
	 * Obsidian and bedrock do not count: those he still forgets, as in the normal game.
	 * False — hiding in a house makes him lose the target, and the next headbutt never comes.
	 */
	public static final boolean KEEP_TARGET_BEHIND_WALL = true;

	/**
	 * After the ravager has seen the target again, hiding where he cannot break through
	 * (obsidian, or no wall on the line of sight) drops the target after this many ticks.
	 * 60 = 3 seconds, same as the normal game.
	 * Does not apply while a breakable wall is between them, or while that chase is still on:
	 * the pause between headbutts is longer than 3 seconds, and he must not forget during it.
	 * 0 = once he can see the target again, the next such hide drops the target immediately.
	 */
	public static final int UNSEEN_FORGET_TICKS = 60;

	// ========================================================================

	/** Who the ravager was charging, and whether that chase is through a breakable wall. */
	private static final Map<Ravager, Memory> MEMORY = new WeakHashMap<>();

	private static final class Memory {
		private UUID targetId;
		/** True while a breakable wall hides this target and the ravager has not seen them since. */
		private boolean holding;
		/** True after a wall chase, so a later obsidian hide still expires like the normal game. */
		private boolean everHeld;
		/** Game time when the target was last hidden by something he cannot break. 0 = not counting. */
		private long lostSightAt;

		private Memory(UUID targetId) {
			this.targetId = targetId;
		}
	}

	/** Hardness of ordinary stone. A pickaxe block is not softer than this is considered a stone. */
	private static final float STONE_HARDNESS = 1.5f;

	/** 20 ticks in one second. */
	private static final int TICKS_PER_SECOND = 20;

	private RavagerWallBreak() {
	}

	/**
	 * Call just before movement and the headbutt. If the game cleared the target because a
	 * breakable wall is in the way, the target is put back. Obsidian and bedrock do not count.
	 */
	public static void keepTargetThroughWall(Ravager ravager) {
		if (!KEEP_TARGET_BEHIND_WALL || !canBreak(ravager) || !ravager.isAlive()) {
			return;
		}
		LivingEntity current = ravager.getTarget();
		Memory memory = MEMORY.get(ravager);
		if (current != null && current.isAlive()) {
			if (memory == null || !memory.targetId.equals(current.getUUID())) {
				memory = new Memory(current.getUUID());
				MEMORY.put(ravager, memory);
			}
		}
		if (memory == null) {
			return;
		}
		LivingEntity remembered = findRemembered(ravager, memory.targetId);
		if (!isChaseable(ravager, remembered)) {
			if (remembered != null && ravager.getTarget() == remembered) {
				ravager.setTarget(null);
			}
			MEMORY.remove(ravager);
			return;
		}
		if (ravager.getSensing().hasLineOfSight(remembered)) {
			memory.holding = false;
			memory.lostSightAt = 0L;
			return;
		}
		if (wallBlocksView(ravager, remembered)) {
			memory.holding = true;
			memory.everHeld = true;
			memory.lostSightAt = 0L;
			if (ravager.getTarget() != remembered) {
				ravager.setTarget(remembered);
			}
			return;
		}
		if (memory.holding) {
			if (ravager.getTarget() != remembered) {
				ravager.setTarget(remembered);
			}
			return;
		}
		if (memory.everHeld && ravager.getTarget() == remembered && forgetAfterUnseen(ravager, memory)) {
			ravager.setTarget(null);
			MEMORY.remove(ravager);
			return;
		}
		if (ravager.getTarget() == null) {
			MEMORY.remove(ravager);
		}
	}

	/** True when the unseen timer has run out. A timer of 0 forgets immediately. */
	private static boolean forgetAfterUnseen(Ravager ravager, Memory memory) {
		if (UNSEEN_FORGET_TICKS <= 0) {
			return true;
		}
		long now = ravager.level().getGameTime();
		if (memory.lostSightAt == 0L) {
			memory.lostSightAt = now;
			return false;
		}
		return now - memory.lostSightAt >= UNSEEN_FORGET_TICKS;
	}

	@Nullable
	private static LivingEntity findRemembered(Ravager ravager, UUID id) {
		if (!(ravager.level() instanceof ServerLevel level)) {
			return null;
		}
		if (level.getEntity(id) instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	/** Alive, same world, not a creative or spectator player, still inside the chase distance. */
	private static boolean isChaseable(Ravager ravager, @Nullable LivingEntity target) {
		if (target == null || !target.isAlive() || target.isRemoved() || target.level() != ravager.level()) {
			return false;
		}
		if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return false;
		}
		double range = keepRange(ravager);
		return ravager.distanceToSqr(target) <= range * range;
	}

	/**
	 * How far (blocks) a wall chase still holds. At least {@link #MAX_DISTANCE_TO_TARGET},
	 * or the ravager's own follow range if a raid made that longer.
	 */
	private static double keepRange(Ravager ravager) {
		var follow = ravager.getAttribute(Attributes.FOLLOW_RANGE);
		double followRange = follow == null ? 0.0 : follow.getValue();
		return Math.max(MAX_DISTANCE_TO_TARGET, followRange);
	}

	/**
	 * A wall he can break is what blocks the view: a ray to the eyes, the body or the feet
	 * hits dirt, wood, stone, glass, and the other blocks {@link #hitsNeeded} would strike.
	 * Obsidian and bedrock do not.
	 */
	private static boolean wallBlocksView(Ravager ravager, LivingEntity target) {
		Vec3 from = ravager.getEyePosition();
		Vec3 eyes = target.getEyePosition();
		Vec3 body = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 feet = new Vec3(target.getX(), target.getY() + 0.2, target.getZ());
		return rayHitsBreakable(ravager, from, eyes)
				|| rayHitsBreakable(ravager, from, body)
				|| rayHitsBreakable(ravager, from, feet);
	}

	private static boolean rayHitsBreakable(Ravager ravager, Vec3 from, Vec3 to) {
		BlockHitResult hit = ravager.level().clip(new ClipContext(
				from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ravager));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos pos = hit.getBlockPos();
		Level level = ravager.level();
		return hitsNeeded(level, pos, level.getBlockState(pos)) > 0;
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
