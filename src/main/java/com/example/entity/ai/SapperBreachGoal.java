package com.example.entity.ai;

import com.example.creeper.SapperCreeper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Подрывник идёт к блоку под ногами игрока и там взрывается. Взрывается ВСЕГДА:
 * <ul>
 *   <li>путь есть — подбегает к опоре (пол, верх башни) и рвёт её;</li>
 *   <li>башня без пути наверх — рвёт основание;</li>
 *   <li>пути нет (дом, стена) — подходит как можно ближе, упирается и через 8–10 секунд взрывается у стены,
 *       даже если игрок ушёл вглубь дома или скрылся из виду.</li>
 * </ul>
 * Игрока помнит {@link SapperCreeper#MEMORY_SECONDS} секунд: ванильный ИИ быстро теряет цель без прямой видимости.
 */
public class SapperBreachGoal extends Goal {

	private final Creeper creeper;

	private UUID rememberedPlayerId;
	private long rememberedUntilGameTime;

	private int ticksUntilPathCheck;
	private boolean cantReach;

	private int ticksUntilStuckCheck;
	private Vec3 stuckCheckPos = Vec3.ZERO;
	private boolean stuck;

	private boolean wallTimerStarted;
	private long wallExplodeAt;

	public SapperBreachGoal(Creeper creeper) {
		this.creeper = creeper;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return SapperCreeper.isSapper(creeper) && resolveTarget() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		ticksUntilPathCheck = 0;
		ticksUntilStuckCheck = SapperCreeper.STUCK_CHECK_TICKS;
		stuckCheckPos = creeper.position();
		stuck = false;
		cantReach = false;
		wallTimerStarted = false;
	}

	@Override
	public void stop() {
		wallTimerStarted = false;
		if (!creeper.isIgnited()) {
			creeper.setSwellDir(-1);
		}
	}

	/** Цель крипера, а если он её потерял из виду — запомненный игрок (пока память не истекла). */
	private LivingEntity resolveTarget() {
		long now = creeper.level().getGameTime();
		LivingEntity current = creeper.getTarget();
		if (current instanceof Player player && isValidPlayer(player)) {
			rememberedPlayerId = player.getUUID();
			rememberedUntilGameTime = now + SapperCreeper.MEMORY_SECONDS * 20L;
			return player;
		}
		if (current != null && current.isAlive()) {
			return current;
		}
		if (rememberedPlayerId != null && now < rememberedUntilGameTime) {
			Player remembered = creeper.level().getPlayerByUUID(rememberedPlayerId);
			if (remembered != null && isValidPlayer(remembered)
					&& creeper.distanceTo(remembered) <= SapperCreeper.CHASE_MAX_DISTANCE) {
				return remembered;
			}
		}
		return null;
	}

	private boolean isValidPlayer(Player player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator()
				&& creeper.distanceTo(player) <= SapperCreeper.CHASE_MAX_DISTANCE;
	}

	@Override
	public void tick() {
		LivingEntity target = resolveTarget();
		if (target == null) {
			return;
		}
		creeper.getLookControl().setLookAt(target, 30.0f, 30.0f);
		if (creeper.isIgnited()) {
			creeper.getNavigation().stop();
			return;
		}

		long now = creeper.level().getGameTime();
		BlockPos support = SapperCreeper.supportUnder(target);
		double horizontal = SapperCreeper.horizontalDistance(creeper, target);
		boolean elevated = SapperCreeper.isElevated(creeper, target);
		boolean atSupport = creeper.distanceToSqr(support.getX() + 0.5, support.getY(), support.getZ() + 0.5)
				<= SapperCreeper.DETONATE_HORIZONTAL * SapperCreeper.DETONATE_HORIZONTAL;

		updateStuck();

		boolean recheckPath = ticksUntilPathCheck-- <= 0;
		if (recheckPath) {
			ticksUntilPathCheck = SapperCreeper.PATH_CHECK_TICKS;
			cantReach = !SapperCreeper.canReach(creeper, target) && !SapperCreeper.canReach(creeper, support);
		}

		// Башня или блок под ногами. Шипение можно сбить, отойдя, как у обычного крипера.
		// ignite() здесь не вызывается: после него запал уже не погасить, и удар с отходом всегда кончался взрывом.
		boolean seesPlayer = SapperCreeper.canSee(creeper, target);
		if ((elevated && horizontal <= SapperCreeper.DETONATE_HORIZONTAL) || (!cantReach && atSupport)) {
			wallTimerStarted = false;
			creeper.getNavigation().stop();
			hiss(true);
			return;
		}

		if (!cantReach || seesPlayer) {
			// Чистое поле или игрок снова на виду: догоняем, у стены не ждём.
			wallTimerStarted = false;
			hiss(false);
			if (recheckPath || creeper.getNavigation().isDone()) {
				creeper.getNavigation().moveTo(
						support.getX() + 0.5, support.getY(), support.getZ() + 0.5, SapperCreeper.MOVE_SPEED);
			}
			return;
		}

		// Пути нет и игрока не видно (дом, стена). Подходим как можем, упираемся — потом 8–10 секунд и взрыв.
		// Пока идём, отсчёт не стартует: удар и короткая остановка в поле сюда не попадают.
		if (!wallTimerStarted && stuck) {
			wallTimerStarted = true;
			wallExplodeAt = now + SapperCreeper.randomWallDelayTicks(creeper);
		}

		if (wallTimerStarted) {
			creeper.getNavigation().stop();
			hiss(false);
			if (now >= wallExplodeAt) {
				SapperCreeper.detonate(creeper);
			}
			return;
		}

		hiss(false);
		if (recheckPath || creeper.getNavigation().isDone()) {
			creeper.getNavigation().moveTo(target, SapperCreeper.MOVE_SPEED);
		}
	}

	/** Шипит, пока условие взрыва держится. Отошёл — сдувается. Уже подожжённый запал не трогаем. */
	private void hiss(boolean swelling) {
		if (!creeper.isIgnited()) {
			creeper.setSwellDir(swelling ? 1 : -1);
		}
	}

	/** Раз в секунду проверяет, сдвинулся ли крипер. Не сдвинулся — упёрся в стену. */
	private void updateStuck() {
		if (ticksUntilStuckCheck-- > 0) {
			return;
		}
		ticksUntilStuckCheck = SapperCreeper.STUCK_CHECK_TICKS;
		Vec3 now = creeper.position();
		stuck = now.distanceTo(stuckCheckPos) < SapperCreeper.STUCK_MOVE_BLOCKS;
		stuckCheckPos = now;
	}
}
