package com.example.entity.ai;

import com.example.creeper.DynamiteCreeper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Метатель держит дистанцию: близко — отступает, далеко — подходит,
 * в зоне броска — кружит боком вокруг игрока и кидает TNT на ходу.
 * Занимает MOVE, поэтому обычный бег вплотную и подрыв (SwellGoal) его не перебивают,
 * пока цель жива. Сам подрыв дополнительно выключен в {@code SwellGoalMixin}.
 */
public class DynamiteThrowGoal extends Goal {

	private final Creeper creeper;
	private long nextThrowGameTime;
	private int repathTicks;
	private int ticksUntilStrafeSwitch;
	private int strafeDir = 1;

	public DynamiteThrowGoal(Creeper creeper) {
		this.creeper = creeper;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!DynamiteCreeper.isDynamite(creeper)) {
			return false;
		}
		LivingEntity target = creeper.getTarget();
		return target != null && target.isAlive() && creeper.distanceTo(target) <= DynamiteCreeper.CHASE_MAX_DISTANCE;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		repathTicks = 0;
		if (nextThrowGameTime == 0L) {
			nextThrowGameTime = creeper.level().getGameTime() + 20L;
		}
	}

	@Override
	public void tick() {
		LivingEntity target = creeper.getTarget();
		if (target == null) {
			return;
		}
		creeper.getLookControl().setLookAt(target, 30.0f, 30.0f);
		// На всякий случай гасим набор к самоподрыву, если он уже начался.
		if (creeper.getSwellDir() > 0) {
			creeper.setSwellDir(-1);
		}

		double distance = creeper.distanceTo(target);
		repathTicks--;
		if (distance < DynamiteCreeper.RETREAT_DISTANCE) {
			if (repathTicks <= 0) {
				repathTicks = 8;
				moveAway(target);
			}
			return;
		}
		if (distance > DynamiteCreeper.THROW_MAX_DISTANCE) {
			if (repathTicks <= 0) {
				repathTicks = 8;
				creeper.getNavigation().moveTo(target, DynamiteCreeper.APPROACH_SPEED);
			}
			return;
		}

		if (ticksUntilStrafeSwitch-- <= 0) {
			strafeDir = creeper.getRandom().nextBoolean() ? 1 : -1;
			int min = DynamiteCreeper.STRAFE_SWITCH_MIN_SECONDS * 20;
			int max = DynamiteCreeper.STRAFE_SWITCH_MAX_SECONDS * 20;
			ticksUntilStrafeSwitch = max <= min ? min : creeper.getRandom().nextIntBetweenInclusive(min, max);
		}
		if (repathTicks <= 0) {
			repathTicks = 8;
			circleAround(target);
		}
		if (!creeper.getSensing().hasLineOfSight(target)) {
			return;
		}
		long now = creeper.level().getGameTime();
		if (now < nextThrowGameTime) {
			return;
		}
		DynamiteCreeper.throwTnt(creeper, target);
		nextThrowGameTime = now + DynamiteCreeper.cooldownTicks();
	}

	/** Точка сбоку от игрока на удобной дистанции — крипер идёт к ней. */
	private void circleAround(LivingEntity target) {
		double dx = creeper.getX() - target.getX();
		double dz = creeper.getZ() - target.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 0.01) {
			dx = 1.0;
			dz = 0.0;
			len = 1.0;
		}
		double sideX = -dz / len * strafeDir;
		double sideZ = dx / len * strafeDir;
		double destX = target.getX() + dx / len * DynamiteCreeper.HOLD_DISTANCE + sideX * DynamiteCreeper.STRAFE_SIDE_BLOCKS;
		double destZ = target.getZ() + dz / len * DynamiteCreeper.HOLD_DISTANCE + sideZ * DynamiteCreeper.STRAFE_SIDE_BLOCKS;
		creeper.getNavigation().moveTo(destX, target.getY(), destZ, DynamiteCreeper.STRAFE_SPEED);
	}

	private void moveAway(LivingEntity target) {
		Vec3 away = creeper.position().subtract(target.position());
		away = new Vec3(away.x, 0.0, away.z);
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(1.0, 0.0, 0.0);
		}
		away = away.normalize().scale(6.0);
		creeper.getNavigation().moveTo(
				creeper.getX() + away.x,
				creeper.getY(),
				creeper.getZ() + away.z,
				DynamiteCreeper.RETREAT_SPEED);
	}
}
