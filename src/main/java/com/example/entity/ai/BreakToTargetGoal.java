package com.example.entity.ai;

import com.example.digging.BlockBreaking;
import com.example.grudge.PlayerGrudge;
import net.minecraft.core.BlockPos;
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

import java.util.EnumSet;

/**
 * Зомби ломает блоки, только если к игроку нет пути и впереди реальная стена (не «ошибка» pathfinder).
 * Не копает, если игрок уже в зоне удара. Может ломать до {@link BlockBreaking#DIG_UP_MAX_BLOCKS_ABOVE_SELF}
 * блоков выше себя в клетке к игроку (игрок на подставке).
 */
public class BreakToTargetGoal extends Goal {

	private final Zombie zombie;

	private long nextCheckGameTime;
	private boolean blocked;

	private BlockPos breakPos;
	private float progress;
	private int ticksSinceSwing;

	public BreakToTargetGoal(Zombie zombie) {
		this.zombie = zombie;
		// MOVE: не работает параллельно с melee (приоритет 2) — иначе зомби стоит и ломает, хотя может ударить.
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

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = resolveDigTarget();
		return blocked && target != null && target.isAlive() && BlockBreaking.isBreaker(zombie)
				&& isBlockedFromTarget();
	}

	@Override
	public void start() {
		blocked = true;
		breakPos = null;
		progress = 0.0f;
		ticksSinceSwing = 0;
	}

	@Override
	public void tick() {
		LivingEntity target = resolveDigTarget();
		if (target == null) {
			return;
		}
		Level level = zombie.level();

		// Каждый тик: если путь появился или можно ударить — бросаем копать и идём.
		if (!isBlockedFromTarget()) {
			blocked = false;
			clearCracks();
			breakPos = null;
			progress = 0.0f;
			zombie.getNavigation().moveTo(target, 1.0);
			return;
		}
		blocked = true;

		long now = level.getGameTime();
		if (now >= nextCheckGameTime) {
			nextCheckGameTime = now + BlockBreaking.STUCK_CHECK_INTERVAL_TICKS;
		}

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
				zombie.getNavigation().moveTo(target, 1.0);
				return;
			}
		}

		mine(level);
	}

	@Override
	public void stop() {
		clearCracks();
		breakPos = null;
		progress = 0.0f;
		blocked = false;
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
			level.destroyBlock(breakPos, BlockBreaking.DROP_ITEMS, zombie);
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

	/** Нужно ли вообще копать к этому игроку. */
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
	 * Впереди по направлению к игроку есть блок с коллизией (стена/ступенька под игроком),
	 * а не пустой проход — иначе pathfinder иногда ошибается, а зомби зря ломает землю.
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

	/** Клетки «столбиком» перед зомби к игроку (снизу вверх), которые можно ломать. */
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

	/** Можно ли ломать этот блок (не под ногами на ровной местности). */
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
			// Сначала нижний блок столбика — подставка, потом выше.
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
