package com.example.entity.ai;

import com.example.entity.SkeletonSquadMember;
import com.example.skeleton.SkeletonCaptain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.Skeleton;

import java.util.EnumSet;
import java.util.UUID;

/**
 The captain himself goes to the fixed player, even if he has not yet seen him at point-blank range.
 Assistants do not lead this goal: they run after the captain with a leash.
 */
public class CaptainHuntGoal extends Goal {

	private final Skeleton captain;
	private final SkeletonSquadMember squad;

	public CaptainHuntGoal(Skeleton captain, SkeletonSquadMember squad) {
		this.captain = captain;
		this.squad = squad;
		this.setFlags(EnumSet.of(Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		return squad.evolvingthreats$getSquadRole() == SkeletonCaptain.ROLE_CAPTAIN && resolve() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = resolve();
		if (target != null && captain.getTarget() != target) {
			captain.setTarget(target);
		}
	}

	private LivingEntity resolve() {
		UUID id = squad.evolvingthreats$getHuntTargetId();
		if (id == null) {
			return SkeletonCaptain.tryLockNearestPlayer(captain, squad);
		}
		if (!(captain.level() instanceof ServerLevel level)) {
			return null;
		}

		ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
		if (player == null || !player.isAlive() || player.isSpectator()) {
			return null;
		}
		if (!SkeletonCaptain.HUNT_CREATIVE && player.isCreative()) {
			return null;
		}
		if (player.level() != captain.level()) {
			squad.evolvingthreats$setHuntTargetId(null);
			return null;
		}

		double abandon = SkeletonCaptain.HUNT_ABANDON_DISTANCE;
		if (captain.distanceToSqr(player) > abandon * abandon) {
			squad.evolvingthreats$setHuntTargetId(null);
			captain.setTarget(null);
			return null;
		}
		return player;
	}
}
