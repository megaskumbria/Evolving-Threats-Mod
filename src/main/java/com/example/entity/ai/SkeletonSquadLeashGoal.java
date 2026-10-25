package com.example.entity.ai;

import com.example.entity.SkeletonSquadMember;
import com.example.skeleton.SkeletonCaptain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.Skeleton;

import java.util.EnumSet;

/**
 * Помощник держится рядом с капитаном.
 * В бою поводок длиннее: можно добежать до игрока, но не уйти в другую сторону карты.
 * Капитан сам ни за кем не ходит — он точка, вокруг которой стоит отряд.
 */
public class SkeletonSquadLeashGoal extends Goal {

	private final Skeleton mob;
	private final SkeletonSquadMember squad;

	public SkeletonSquadLeashGoal(Skeleton mob, SkeletonSquadMember squad) {
		this.mob = mob;
		this.squad = squad;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return anchorTooFar();
	}

	@Override
	public boolean canContinueToUse() {
		return anchorTooFar();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		Skeleton anchor = SkeletonCaptain.anchor(mob, squad);
		if (anchor == null) {
			return;
		}
		mob.getLookControl().setLookAt(anchor, 30.0f, 30.0f);
		if (mob.tickCount % 10 == 0 || mob.getNavigation().isDone()) {
			mob.getNavigation().moveTo(anchor, SkeletonCaptain.GROUP_SPEED);
		}
	}

	private boolean anchorTooFar() {
		if (squad.evolvingthreats$getSquadRole() == SkeletonCaptain.ROLE_NONE) {
			return false;
		}
		Skeleton anchor = SkeletonCaptain.anchor(mob, squad);
		if (anchor == null || anchor == mob) {
			return false;
		}
		double limit = mob.getTarget() == null
				? SkeletonCaptain.IDLE_FOLLOW_BLOCKS
				: SkeletonCaptain.COMBAT_LEASH_BLOCKS;
		return mob.distanceToSqr(anchor) > limit * limit;
	}
}
