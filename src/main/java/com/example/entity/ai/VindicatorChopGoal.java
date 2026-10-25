package com.example.entity.ai;

import com.example.illager.VindicatorWoodBreak;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Поборник рубит деревянный блок, если из-за него нет пути к цели.
 * Камень и землю не трогает. Если игрок уже под ударом — рубить не начинает.
 * Пока рубит, топор остаётся в руке. Как только путь открыт или цель рядом,
 * рубка отпускает его, и обычный удар топором снова работает.
 */
public class VindicatorChopGoal extends Goal {

	private final Vindicator vindicator;

	private long nextCheckGameTime;
	private long nextPathCheckGameTime;
	private long lastStopGameTime;

	/** true, пока эта рубка ещё нужна. false — отдать ход обычному удару. */
	private boolean chopping;

	private BlockPos breakPos;
	private float progress;
	private int ticksSinceSwing;

	public VindicatorChopGoal(Vindicator vindicator) {
		this.vindicator = vindicator;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!VindicatorWoodBreak.canChop(vindicator) || vindicator.isPassenger()) {
			return false;
		}
		long now = vindicator.level().getGameTime();
		if (now < nextCheckGameTime) {
			return false;
		}
		nextCheckGameTime = now + VindicatorWoodBreak.CHECK_INTERVAL_TICKS;
		return isBlockedByWood();
	}

	@Override
	public boolean canContinueToUse() {
		if (!chopping || !VindicatorWoodBreak.canChop(vindicator)) {
			return false;
		}
		LivingEntity target = vindicator.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		if (vindicator.distanceTo(target) > VindicatorWoodBreak.MAX_DISTANCE_TO_TARGET) {
			return false;
		}
		// Рядом уже можно бить топором, рубка не должна это перехватывать.
		return !vindicator.isWithinMeleeAttackRange(target);
	}

	@Override
	public void start() {
		chopping = true;
		ticksSinceSwing = 0;
		// Скрещённые руки прячут топор. Пока рубит, он остаётся в боевой стойке, топор виден.
		vindicator.setAggressive(true);
		long now = vindicator.level().getGameTime();
		nextPathCheckGameTime = now + VindicatorWoodBreak.CHECK_INTERVAL_TICKS;
		if (breakPos != null && now - lastStopGameTime > VindicatorWoodBreak.KEEP_PROGRESS_TICKS) {
			clearCracks();
			breakPos = null;
			progress = 0.0f;
		}
	}

	@Override
	public void tick() {
		LivingEntity target = vindicator.getTarget();
		if (target == null) {
			return;
		}
		Level level = vindicator.level();
		long now = level.getGameTime();
		if (vindicator.distanceTo(target) > VindicatorWoodBreak.MAX_DISTANCE_TO_TARGET
				|| vindicator.isWithinMeleeAttackRange(target)) {
			finish();
			return;
		}
		if (now >= nextPathCheckGameTime) {
			nextPathCheckGameTime = now + VindicatorWoodBreak.CHECK_INTERVAL_TICKS;
			if (hasReachablePath(target)) {
				finish();
				vindicator.getNavigation().moveTo(target, 1.0);
				return;
			}
		}
		if (breakPos != null) {
			BlockState state = level.getBlockState(breakPos);
			if (!isChopTarget(level, breakPos, state)) {
				clearCracks();
				breakPos = null;
				progress = 0.0f;
			}
		}
		if (breakPos == null) {
			breakPos = pickWood(target);
			progress = 0.0f;
			if (breakPos == null) {
				finish();
				vindicator.getNavigation().moveTo(target, 1.0);
				return;
			}
		}
		chop(level);
	}

	@Override
	public void stop() {
		chopping = false;
		lastStopGameTime = vindicator.level().getGameTime();
		// Трещину на блоке не стираем сразу: если его отвлекли, прогресс ещё живёт несколько секунд.
		// Цель ещё есть — обычный удар сам поднимет топор. Цели нет — руки снова скрещены.
		if (vindicator.getTarget() == null) {
			vindicator.setAggressive(false);
		}
	}

	private void finish() {
		chopping = false;
		clearCracks();
		breakPos = null;
		progress = 0.0f;
	}

	private void chop(Level level) {
		BlockState state = level.getBlockState(breakPos);
		vindicator.setAggressive(true);
		vindicator.getLookControl().setLookAt(Vec3.atCenterOf(breakPos));
		vindicator.getNavigation().stop();
		progress += VindicatorWoodBreak.progressPerTick(vindicator, level, breakPos, state);
		ticksSinceSwing++;
		if (ticksSinceSwing >= 6) {
			ticksSinceSwing = 0;
			vindicator.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
			level.playSound(null, breakPos, state.getSoundType().getHitSound(), SoundSource.HOSTILE, 0.6f, 0.8f);
		}
		if (progress >= 1.0f) {
			level.destroyBlock(breakPos, VindicatorWoodBreak.DROP_ITEMS, vindicator);
			clearCracks();
			breakPos = null;
			progress = 0.0f;
		} else {
			level.destroyBlockProgress(vindicator.getId(), breakPos, Math.min(9, (int) (progress * 10.0f)));
		}
	}

	private void clearCracks() {
		if (breakPos != null) {
			vindicator.level().destroyBlockProgress(vindicator.getId(), breakPos, -1);
		}
	}

	private boolean isBlockedByWood() {
		LivingEntity target = vindicator.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		if (vindicator.distanceTo(target) > VindicatorWoodBreak.MAX_DISTANCE_TO_TARGET) {
			return false;
		}
		if (vindicator.isWithinMeleeAttackRange(target)) {
			return false;
		}
		if (hasReachablePath(target)) {
			return false;
		}
		return pickWood(target) != null;
	}

	private boolean hasReachablePath(LivingEntity target) {
		Path path = vindicator.getNavigation().createPath(target, 0);
		return path != null && path.canReach();
	}

	private BlockPos pickWood(LivingEntity target) {
		Level level = vindicator.level();
		BlockPos feet = vindicator.blockPosition();
		Direction primary = horizontalDirectionTo(target);
		Direction secondary = secondaryDirection(target, primary);
		for (Direction direction : new Direction[] { primary, secondary }) {
			BlockPos front = feet.relative(direction);
			for (int y = feet.getY(); y <= feet.getY() + 1; y++) {
				BlockPos candidate = new BlockPos(front.getX(), y, front.getZ());
				if (!isChopTarget(level, candidate, level.getBlockState(candidate))) {
					continue;
				}
				if (vindicator.distanceToSqr(Vec3.atCenterOf(candidate)) > reachSqr()) {
					continue;
				}
				return candidate;
			}
		}
		return null;
	}

	private boolean isChopTarget(Level level, BlockPos pos, BlockState state) {
		return VindicatorWoodBreak.isWoodenObstacle(level, pos, state);
	}

	private Direction horizontalDirectionTo(LivingEntity target) {
		double dx = target.getX() - vindicator.getX();
		double dz = target.getZ() - vindicator.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0.0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
	}

	private Direction secondaryDirection(LivingEntity target, Direction primary) {
		if (primary.getAxis() == Direction.Axis.X) {
			return target.getZ() >= vindicator.getZ() ? Direction.SOUTH : Direction.NORTH;
		}
		return target.getX() >= vindicator.getX() ? Direction.EAST : Direction.WEST;
	}

	private static double reachSqr() {
		double reach = VindicatorWoodBreak.REACH;
		return reach * reach;
	}
}
