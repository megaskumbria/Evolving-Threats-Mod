package com.example.entity.ai;

import com.example.entity.IllagerPatrolMember;
import com.example.illager.IllagerPatrol;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.raid.Raider;

import java.util.EnumSet;

/**
 * Идёт в точку, где игрок стоял, когда отряд появился.
 * Точка не двигается за игроком. Если по дороге заметят его вблизи, дерутся как обычные мобы,
 * а когда потеряют из виду — снова идут в ту же точку.
 */
public class IllagerPatrolMarchGoal extends Goal {

	private final Raider mob;
	private final IllagerPatrolMember patrol;

	public IllagerPatrolMarchGoal(Raider mob, IllagerPatrolMember patrol) {
		this.mob = mob;
		this.patrol = patrol;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		BlockPos destination = patrol.evolvingthreats$getPatrolDestination();
		if (destination == null || mob.getTarget() != null) {
			return false;
		}
		return !arrived(destination);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void tick() {
		BlockPos destination = patrol.evolvingthreats$getPatrolDestination();
		if (destination == null) {
			return;
		}
		if (arrived(destination)) {
			patrol.evolvingthreats$setPatrolDestination(null);
			mob.getNavigation().stop();
			return;
		}
		if (mob.getNavigation().isDone()) {
			mob.getNavigation().moveTo(
					destination.getX() + 0.5,
					destination.getY(),
					destination.getZ() + 0.5,
					IllagerPatrol.MARCH_SPEED);
		}
	}

	private boolean arrived(BlockPos destination) {
		double radius = IllagerPatrol.ARRIVE_BLOCKS;
		return mob.blockPosition().distSqr(destination) <= radius * radius;
	}
}
