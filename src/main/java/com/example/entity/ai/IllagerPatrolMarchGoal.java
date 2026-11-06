package com.example.entity.ai;

import com.example.entity.IllagerPatrolMember;
import com.example.illager.IllagerPatrol;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.raid.Raider;

import java.util.EnumSet;

/**
 Goes to the point where the player was standing when the squad arrived.
 The point does not move behind the player. If they see him on the road, they fight like mobs.
 When they lose sight, they go back to the same place.
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
			// Leave the house point set. The level tick grants the card only when
			// a pillager, a vindicator, and the ravager are all in the yard, then clears it.
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
