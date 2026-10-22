package com.example.entity.ai;

import com.example.digging.BlockBreaking;
import com.example.grudge.PlayerGrudge;
import com.example.light.LightSeeking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Зомби без цели идёт к ближайшему яркому свету, стоит там и идёт дальше.
 * Как только появляется цель (игрок), ванильная атака (приоритет выше) перебивает эту цель.
 */
public class LightSeekGoal extends Goal {

	private final Zombie zombie;

	/** Посещённые позиции света → игровое время, до которого их игнорируем. */
	private final Map<BlockPos, Long> ignoredUntil = new HashMap<>();

	private BlockPos lightPos;
	private long nextScanGameTime;
	private int walkTicks;
	private int waitTicks;
	private boolean arrived;
	private boolean finished;

	public LightSeekGoal(Zombie zombie) {
		this.zombie = zombie;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!LightSeeking.isEligible(zombie) || zombie.isPassenger()) {
			return false;
		}
		// Игрок в цели или в «памяти» — свет не интересен.
		if (zombie.getTarget() != null || PlayerGrudge.isPursuingPlayer(zombie)) {
			return false;
		}
		long now = zombie.level().getGameTime();
		if (now < nextScanGameTime) {
			return false;
		}
		// Случайный сдвиг, чтобы все зомби не сканировали в один и тот же тик.
		nextScanGameTime = now + LightSeeking.SCAN_INTERVAL_TICKS + zombie.getRandom().nextInt(20);

		cleanupIgnored(now);
		BlockPos found = LightSeeking.findNearestLight(zombie, pos -> isIgnored(pos, now));
		if (found == null) {
			return false;
		}
		lightPos = found;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return !finished && zombie.getTarget() == null && !PlayerGrudge.isPursuingPlayer(zombie)
				&& LightSeeking.isEligible(zombie);
	}

	@Override
	public void start() {
		walkTicks = 0;
		waitTicks = 0;
		arrived = false;
		finished = false;
		// reachRange 2: достаточно подойти вплотную — свет может висеть в стене или на потолке.
		boolean ok = zombie.getNavigation().moveTo(
				lightPos.getX() + 0.5, lightPos.getY(), lightPos.getZ() + 0.5, 2, LightSeeking.MOVE_SPEED);
		if (!ok) {
			// Пути нет — запоминаем, чтобы не пытаться снова сразу.
			markVisited();
			finished = true;
		}
	}

	@Override
	public void tick() {
		if (finished || lightPos == null) {
			return;
		}
		// Свет убрали (сломали факел) — идти больше некуда.
		if (!LightSeeking.isAttractiveLight(zombie.level().getBlockState(lightPos))) {
			markVisited();
			finished = true;
			return;
		}

		double distSqr = zombie.distanceToSqr(lightPos.getX() + 0.5, lightPos.getY() + 0.5, lightPos.getZ() + 0.5);
		double arrive = LightSeeking.ARRIVE_DISTANCE;
		// «Пришёл»: подошёл близко, либо путь кончился, но рукой до света дотянуться можно (свет на потолке).
		double eyeDistSqr = zombie.getEyePosition().distanceToSqr(lightPos.getX() + 0.5, lightPos.getY() + 0.5, lightPos.getZ() + 0.5);
		boolean inReach = eyeDistSqr <= BlockBreaking.REACH * BlockBreaking.REACH;
		if (!arrived && (distSqr <= arrive * arrive || (zombie.getNavigation().isDone() && inReach))) {
			arrived = true;
			zombie.getNavigation().stop();
		}

		if (arrived) {
			waitTicks++;
			BlockState lightState = zombie.level().getBlockState(lightPos);
			boolean willBreak = LightSeeking.BREAK_LIGHT && inReach
					&& BlockBreaking.isBreaker(zombie)
					&& BlockBreaking.canBreak(zombie.level(), lightPos, lightState);

			if (willBreak) {
				// Ждём BREAK_LIGHT_DELAY_TICKS, на блоке растут трещины, потом ломаем.
				zombie.getLookControl().setLookAt(Vec3.atCenterOf(lightPos));
				int delay = LightSeeking.BREAK_LIGHT_DELAY_TICKS;
				if (waitTicks >= delay) {
					clearCracks();
					zombie.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
					zombie.level().destroyBlock(lightPos, BlockBreaking.DROP_ITEMS, zombie);
					markVisited();
					finished = true;
				} else if (waitTicks % 10 == 0) {
					int stage = Math.min(9, waitTicks * 10 / delay);
					zombie.level().destroyBlockProgress(zombie.getId(), lightPos, stage);
				}
			} else if (waitTicks >= LightSeeking.WAIT_AT_LIGHT_TICKS) {
				markVisited();
				finished = true;
			}
			return;
		}

		walkTicks++;
		if (walkTicks >= LightSeeking.MAX_WALK_TICKS || zombie.getNavigation().isDone()) {
			// Не дошёл (застрял / путь закончился) — считаем визит, идём дальше.
			markVisited();
			finished = true;
		}
	}

	@Override
	public void stop() {
		zombie.getNavigation().stop();
		clearCracks();
		lightPos = null;
		finished = false;
	}

	/** Убрать трещины с блока света (зомби бросил его или уже сломал). */
	private void clearCracks() {
		if (lightPos != null) {
			zombie.level().destroyBlockProgress(zombie.getId(), lightPos, -1);
		}
	}

	private void markVisited() {
		if (lightPos != null) {
			ignoredUntil.put(lightPos, zombie.level().getGameTime() + LightSeeking.IGNORE_VISITED_LIGHT_TICKS);
		}
	}

	private boolean isIgnored(BlockPos pos, long now) {
		Long until = ignoredUntil.get(pos);
		return until != null && until > now;
	}

	private void cleanupIgnored(long now) {
		if (ignoredUntil.size() > 32) {
			ignoredUntil.values().removeIf(until -> until <= now);
		}
	}
}
