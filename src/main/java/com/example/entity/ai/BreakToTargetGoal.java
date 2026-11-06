package com.example.entity.ai;

import com.example.advancement.ModAdvancements;
import com.example.digging.BlockBreaking;
import com.example.grudge.PlayerGrudge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 Zombie breaks blocks only if there is no way to the player and there is a real wall ahead (not a pathfinder error).
 Do not dig if the player is already in the impact zone. Can break to {@link BlockBreaking#DIG UP MAX BLOCKS ABOVE SELF}
 Blocks above themselves in the cage to the player (player on the stand).
 */
public class BreakToTargetGoal extends Goal {

	/**
	 The zombies that are breaking the wall. While the zombies are here, the usual attack doesn't stop him:
	 Otherwise, once a second she finds a fragment of the path to the wall and leads him away from the block.
	 */
	private static final Set<Zombie> DIGGING = Collections.newSetFromMap(new WeakHashMap<>());

	private final Zombie zombie;

	private long nextCheckGameTime;
	private long nextPathCheckGameTime;
	private long lastStopGameTime;
	private boolean blocked;

	private BlockPos breakPos;
	private float progress;
	private int ticksSinceSwing;

	/*Does this zombie now break the wall to the player? */
	public static boolean isDigging(Zombie zombie) {
		return DIGGING.contains(zombie);
	}

	public BreakToTargetGoal(Zombie zombie) {
		this.zombie = zombie;
		// MOVE: does not work in parallel with the attack (priority 3) - otherwise the zombie stands and breaks, although it can strike.
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!BlockBreaking.isBreaker(zombie) || zombie.isPassenger()) {
			return false;
		}
		long now = zombie.level().getGameTime();
		if (now < nextCheckGameTime) {
			return false;
		}
		nextCheckGameTime = now + BlockBreaking.STUCK_CHECK_INTERVAL_TICKS;
		return isBlockedFromTarget();
	}

	/** The path itself is recalculated to tick() not every tick, but once in STUCK CHECK INTERVAL TICKS. */
	@Override
	public boolean canContinueToUse() {
		LivingEntity target = resolveDigTarget();
		return blocked && target != null && target.isAlive() && BlockBreaking.isBreaker(zombie);
	}

	@Override
	public void start() {
		blocked = true;
		ticksSinceSwing = 0;
		long now = zombie.level().getGameTime();
		nextPathCheckGameTime = now + BlockBreaking.STUCK_CHECK_INTERVAL_TICKS;
		// A short pause (hit, distracted) does not shed a crack on the same block.
		if (breakPos != null && now - lastStopGameTime > BlockBreaking.KEEP_PROGRESS_TICKS) {
			clearCracks();
			breakPos = null;
			progress = 0.0f;
		}
		DIGGING.add(zombie);
	}

	@Override
	public void tick() {
		LivingEntity target = resolveDigTarget();
		if (target == null) {
			return;
		}
		Level level = zombie.level();
		long now = level.getGameTime();

		// The player is in the strike zone or has gone far - throw digging.
		if (zombie.distanceTo(target) > BlockBreaking.DIG_MAX_DISTANCE_TO_TARGET
				|| BlockBreaking.shouldPreferMeleeOverDig(zombie, target)) {
			finishDigging();
			return;
		}
		// The path to the player is checked once in half a second: there is - go on it.
		if (now >= nextPathCheckGameTime) {
			nextPathCheckGameTime = now + BlockBreaking.STUCK_CHECK_INTERVAL_TICKS;
			if (hasReachablePath(target)) {
				finishDigging();
				zombie.getNavigation().moveTo(target, 1.0);
				return;
			}
		}
		blocked = true;

		if (breakPos != null) {
			BlockState state = level.getBlockState(breakPos);
			if (!isValidBreakTarget(level, breakPos, state, target)) {
				clearCracks();
				breakPos = null;
				progress = 0.0f;
			}
		}
		if (breakPos == null) {
			breakPos = pickBlockToBreak(target);
			progress = 0.0f;
			if (breakPos == null) {
				// There is nothing to break - let the zombies go to the usual attack, not keep him here forever.
				finishDigging();
				zombie.getNavigation().moveTo(target, 1.0);
				return;
			}
		}

		mine(level);
	}

	@Override
	public void stop() {
		blocked = false;
		lastStopGameTime = zombie.level().getGameTime();
		DIGGING.remove(zombie);
	}

	/*The wall no longer interferes: remove the crack and release the zombies to the usual attack. */
	private void finishDigging() {
		blocked = false;
		clearCracks();
		breakPos = null;
		progress = 0.0f;
		DIGGING.remove(zombie);
	}

	private void mine(Level level) {
		BlockState state = level.getBlockState(breakPos);
		zombie.getLookControl().setLookAt(Vec3.atCenterOf(breakPos));
		zombie.getNavigation().stop();

		progress += BlockBreaking.progressPerTick(zombie, level, breakPos, state);

		ticksSinceSwing++;
		if (ticksSinceSwing >= 6) {
			ticksSinceSwing = 0;
			zombie.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
			level.playSound(null, breakPos, state.getSoundType().getHitSound(), SoundSource.HOSTILE, 0.6f, 0.8f);
		}

		if (progress >= 1.0f) {
			if (level.destroyBlock(breakPos, BlockBreaking.DROP_ITEMS, zombie)
					&& zombie.getTarget() instanceof ServerPlayer player) {
				ModAdvancements.onBlockBroken(player);
			}
			breakPos = null;
			progress = 0.0f;
		} else {
			level.destroyBlockProgress(zombie.getId(), breakPos, Math.min(9, (int) (progress * 10.0f)));
		}
	}

	private void clearCracks() {
		if (breakPos != null) {
			zombie.level().destroyBlockProgress(zombie.getId(), breakPos, -1);
		}
	}

	private LivingEntity resolveDigTarget() {
		LivingEntity target = zombie.getTarget();
		if (target != null && target.isAlive()) {
			return target;
		}
		return PlayerGrudge.resolveGrudgePlayer(zombie);
	}

	/*Do I have to dig into this player at all? */
	private boolean isBlockedFromTarget() {
		LivingEntity target = resolveDigTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		if (zombie.distanceTo(target) > BlockBreaking.DIG_MAX_DISTANCE_TO_TARGET) {
			return false;
		}
		if (BlockBreaking.shouldPreferMeleeOverDig(zombie, target)) {
			return false;
		}
		if (hasReachablePath(target)) {
			return false;
		}
		return hasWallTowardPlayer(target);
	}

	private boolean hasReachablePath(LivingEntity target) {
		Path path = zombie.getNavigation().createPath(target, 0);
		return path != null && path.canReach();
	}

	/**
	 In front of the player there is a collision block (wall / step under the player),
	 Not an empty passage, otherwise the pathfinder is sometimes wrong, and zombies are wasting their time breaking the earth.
	 */
	private boolean hasWallTowardPlayer(LivingEntity target) {
		Level level = zombie.level();
		BlockPos feet = zombie.blockPosition();

		if (shouldDigDown(target, feet)) {
			BlockPos below = feet.below();
			return isObstacle(level, below, level.getBlockState(below));
		}

		for (BlockPos pos : frontColumnTowardPlayer(target, feet)) {
			if (isObstacle(level, pos, level.getBlockState(pos))) {
				return true;
			}
		}
		return false;
	}

	private boolean shouldDigDown(LivingEntity target, BlockPos feet) {
		double dx = target.getX() - zombie.getX();
		double dz = target.getZ() - zombie.getZ();
		double dy = target.getY() - (feet.getY() + 0.5);
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		return dy <= -BlockBreaking.DIG_DOWN_MIN_BLOCKS_BELOW
				&& horizontal <= BlockBreaking.DIG_DOWN_MAX_HORIZONTAL;
	}

	private Direction horizontalDirectionTo(LivingEntity target) {
		double dx = target.getX() - zombie.getX();
		double dz = target.getZ() - zombie.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/*Cells "bar" in front of the zombie to the player (bottom up), which can be broken. */
	private BlockPos[] frontColumnTowardPlayer(LivingEntity target, BlockPos feet) {
		Direction dir = horizontalDirectionTo(target);
		BlockPos front = feet.relative(dir);
		int minY = feet.getY();
		if (BlockBreaking.DIG_UP_ALLOW_FRONT_AT_FEET && target.getY() > feet.getY() + 0.25) {
			minY = feet.getY();
		} else {
			minY = feet.getY() + 1;
		}
		int maxY = Math.min(
				feet.getY() + BlockBreaking.DIG_UP_MAX_BLOCKS_ABOVE_SELF,
				target.blockPosition().getY());
		if (maxY < minY) {
			maxY = minY;
		}
		int count = maxY - minY + 1;
		BlockPos[] out = new BlockPos[count];
		for (int i = 0; i < count; i++) {
			out[i] = new BlockPos(front.getX(), minY + i, front.getZ());
		}
		return out;
	}

	private static boolean isObstacle(Level level, BlockPos pos, BlockState state) {
		return !state.getCollisionShape(level, pos).isEmpty() && BlockBreaking.canBreak(level, pos, state);
	}

	/*Is it possible to break this block (not underfoot on flat terrain). */
	private boolean isValidBreakTarget(Level level, BlockPos pos, BlockState state, LivingEntity target) {
		if (!isObstacle(level, pos, state)) {
			return false;
		}
		BlockPos feet = zombie.blockPosition();
		if (pos.getY() <= feet.getY()) {
			if (shouldDigDown(target, feet)) {
				return pos.equals(feet.below());
			}
			if (BlockBreaking.DIG_UP_ALLOW_FRONT_AT_FEET
					&& target.getY() > feet.getY() + 0.25
					&& isFrontCellAtFeet(pos, target, feet)) {
				return true;
			}
			return false;
		}
		return true;
	}

	private boolean isFrontCellAtFeet(BlockPos pos, LivingEntity target, BlockPos feet) {
		Direction dir = horizontalDirectionTo(target);
		BlockPos front = feet.relative(dir);
		return pos.getX() == front.getX() && pos.getZ() == front.getZ() && pos.getY() == feet.getY();
	}

	private BlockPos pickBlockToBreak(LivingEntity target) {
		Level level = zombie.level();
		BlockPos feet = zombie.blockPosition();

		if (shouldDigDown(target, feet)) {
			BlockPos below = feet.below();
			if (isObstacle(level, below, level.getBlockState(below))) {
				return below;
			}
			return null;
		}

		Direction first = horizontalDirectionTo(target);
		Direction second = Math.abs(target.getX() - zombie.getX()) >= Math.abs(target.getZ() - zombie.getZ())
				? (target.getZ() > zombie.getZ() ? Direction.SOUTH : Direction.NORTH)
				: (target.getX() > zombie.getX() ? Direction.EAST : Direction.WEST);

		for (Direction dir : new Direction[] { first, second }) {
			BlockPos front = feet.relative(dir);
			int minY = BlockBreaking.DIG_UP_ALLOW_FRONT_AT_FEET && target.getY() > feet.getY() + 0.25
					? feet.getY()
					: feet.getY() + 1;
			int maxY = Math.min(
					feet.getY() + BlockBreaking.DIG_UP_MAX_BLOCKS_ABOVE_SELF,
					target.blockPosition().getY());
			if (maxY < minY) {
				maxY = minY;
			}
			// First, the lower block of the column is a stand, then higher.
			for (int y = minY; y <= maxY; y++) {
				BlockPos candidate = new BlockPos(front.getX(), y, front.getZ());
				BlockState state = level.getBlockState(candidate);
				if (isValidBreakTarget(level, candidate, state, target)) {
					return candidate;
				}
			}
		}
		return null;
	}
}
