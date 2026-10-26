package com.example.entity.ai;

import com.example.siege.SiegeNight;
import com.example.siege.SiegeWaves;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Ведёт моба осады к игроку короткими отрезками, пока тот далеко.
 *
 * Приоритет 1: выше драки, блуждания и «беги от солнца». Пока моб далеко, эти поведения
 * не перебивают путь. Ближе {@link SiegeWaves#MARCH_STOP_DISTANCE} цель выключается,
 * и моб дерётся или стреляет как обычно.
 *
 * Путь, заданный снаружи раз в секунду, игра затирает своим блужданием на следующем же тике.
 * Поэтому идти к игроку можно только отдельной целью, как здесь.
 */
public class SiegeMarchGoal extends Goal {

	private final Mob mob;
	private int nextRepathTick;
	private double lastX = Double.NaN;
	private double lastZ = Double.NaN;

	public SiegeMarchGoal(Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (mob.isPassenger() || !SiegeWaves.isSiegeMob(mob) || !SiegeNight.isWindowOpen(mob.level())) {
			return false;
		}
		ServerPlayer player = SiegeWaves.nearestHuntTarget(mob);
		// Дальше этой дистанции снова идём. Ближе — уже дерётся или стреляет, и мы его не дёргаем.
		return player != null && mob.distanceToSqr(player) > resumeSqr();
	}

	@Override
	public boolean canContinueToUse() {
		if (mob.isPassenger() || !SiegeWaves.isSiegeMob(mob) || !SiegeNight.isWindowOpen(mob.level())) {
			return false;
		}
		ServerPlayer player = SiegeWaves.nearestHuntTarget(mob);
		// Порог ниже, чем у canUse: иначе на границе цель включается и выключается каждый тик,
		// моб дёргается, а скелет не может довести выстрел.
		return player != null && mob.distanceToSqr(player) > stopSqr();
	}

	@Override
	public void start() {
		lastX = mob.getX();
		lastZ = mob.getZ();
		nextRepathTick = 0;
	}

	@Override
	public void tick() {
		if (mob.tickCount < nextRepathTick) {
			return;
		}
		boolean done = mob.getNavigation().isDone();
		if (!done && !isStuck()) {
			return;
		}
		ServerPlayer player = SiegeWaves.nearestHuntTarget(mob);
		if (player == null) {
			return;
		}
		// Даже если путь не построился, следующая попытка не раньше чем через паузу.
		// Иначе каждый моб осады ищет путь каждый тик, и вся игра идёт рывками.
		nextRepathTick = mob.tickCount + Math.max(1, SiegeWaves.REPATH_COOLDOWN_TICKS);
		lastX = mob.getX();
		lastZ = mob.getZ();

		double dx = player.getX() - mob.getX();
		double dz = player.getZ() - mob.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 1.0E-4) {
			return;
		}
		if (!step(dx, dz, distance, SiegeWaves.MARCH_STEP_BLOCKS)) {
			step(dx, dz, distance, 8.0);
		}
	}

	/** Стоит почти на месте, хотя путь ещё числится. Тогда строим новый. */
	private boolean isStuck() {
		if (Double.isNaN(lastX)) {
			return false;
		}
		double movedX = mob.getX() - lastX;
		double movedZ = mob.getZ() - lastZ;
		return movedX * movedX + movedZ * movedZ < 0.04;
	}

	/** Отрезок на той же высоте, где стоит моб. Точка на поверхности крыш и холмов ломает путь. */
	private boolean step(double dx, double dz, double distance, double length) {
		double travel = Math.max(4.0, Math.min(length, distance - SiegeWaves.MARCH_STOP_DISTANCE));
		double x = mob.getX() + dx / distance * travel;
		double z = mob.getZ() + dz / distance * travel;
		return mob.getNavigation().moveTo(x, mob.getY(), z, SiegeWaves.MARCH_SPEED);
	}

	private static double stopSqr() {
		double stop = SiegeWaves.MARCH_STOP_DISTANCE;
		return stop * stop;
	}

	private static double resumeSqr() {
		double resume = Math.max(SiegeWaves.MARCH_STOP_DISTANCE + 4.0, SiegeWaves.MARCH_RESUME_DISTANCE);
		return resume * resume;
	}
}
