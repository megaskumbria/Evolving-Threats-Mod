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
 * Жители важнее света: если рядом есть житель, зомби берёт его в цель (см. LightSeeking.VILLAGER_BEATS_LIGHT).
 */
public class LightSeekGoal extends Goal {

	private final Zombie zombie;

	/** Посещённые позиции света → игровое время, до которого их игнорируем. */
	private final Map<BlockPos, Long> ignoredUntil = new HashMap<>();

	private BlockPos lightPos;
	private long nextScanGameTime;
	private int walkTicks;
	private int waitTicks;
	/** Сколько света уже «сломано» (0.0–1.0, 1.0 = блок разрушен). */
	private float breakProgress;
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
		// Житель рядом важнее света: делаем его целью и свет не трогаем.
		if (LightSeeking.targetNearbyVillager(zombie)) {
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
		breakProgress = 0.0f;
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
		// Пока идёт к свету или ломает его — периодически проверяем, не появился ли житель.
		// Нашёл → цель назначена, цель «свет» завершается (canContinueToUse увидит target != null), трещины убираются в stop().
		if (zombie.tickCount % LightSeeking.VILLAGER_RECHECK_TICKS == 0 && LightSeeking.targetNearbyVillager(zombie)) {
			finished = true;
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
				// Ломаем по тем же правилам, что и обычные блоки (BlockBreaking.progressPerTick):
				// зависит от твёрдости блока и инструмента в руке зомби. На блоке растут трещины.
				zombie.getLookControl().setLookAt(Vec3.atCenterOf(lightPos));
				breakProgress += BlockBreaking.progressPerTick(zombie, zombie.level(), lightPos, lightState);
				if (breakProgress >= 1.0f) {
					clearCracks();
					zombie.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
					zombie.level().destroyBlock(lightPos, BlockBreaking.DROP_ITEMS, zombie);
					markVisited();
					finished = true;
				} else {
					if (waitTicks % 6 == 0) {
						zombie.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
					}
					if (waitTicks % 5 == 0) {
						int stage = Math.min(9, (int) (breakProgress * 10.0f));
						zombie.level().destroyBlockProgress(zombie.getId(), lightPos, stage);
					}
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
