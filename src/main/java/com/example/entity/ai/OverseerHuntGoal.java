package com.example.entity.ai;

import com.example.entity.ZombieOverseerEntity;
import com.example.overseer.ZombieOverseer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Держит цель на «закреплённом» игроке, пока тот в радиусе охоты.
 */
public class OverseerHuntGoal extends Goal {

	private final Zombie zombie;
	private final ZombieOverseerEntity overseer;

	public OverseerHuntGoal(Zombie zombie, ZombieOverseerEntity overseer) {
		this.zombie = zombie;
		this.overseer = overseer;
		this.setFlags(EnumSet.of(Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		return overseer.evolvingthreats$isOverseer() && resolveHuntTarget() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void tick() {
		LivingEntity target = resolveHuntTarget();
		if (target == null) {
			return;
		}
		if (zombie.getTarget() != target) {
			zombie.setTarget(target);
		}
	}

	private LivingEntity resolveHuntTarget() {
		UUID id = overseer.evolvingthreats$getHuntTargetId();
		if (id == null) {
			return tryAcquireNearestPlayer();
		}
		if (!(zombie.level() instanceof ServerLevel serverLevel)) {
			return null;
		}

		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(id);
		if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) {
			return null;
		}
		if (player.level() != zombie.level()) {
			overseer.evolvingthreats$setHuntTargetId(null);
			return null;
		}

		double distSqr = zombie.distanceToSqr(player);
		if (distSqr > ZombieOverseer.HUNT_ABANDON_DISTANCE * ZombieOverseer.HUNT_ABANDON_DISTANCE) {
			overseer.evolvingthreats$setHuntTargetId(null);
			zombie.setTarget(null);
			return null;
		}

		return player;
	}

	private LivingEntity tryAcquireNearestPlayer() {
		if (!(zombie.level() instanceof ServerLevel level)) {
			return null;
		}
		var player = ZombieOverseer.findNearestPlayer(level, zombie, ZombieOverseer.HUNT_ABANDON_DISTANCE);
		if (player != null) {
			overseer.evolvingthreats$setHuntTargetId(player.getUUID());
			overseer.evolvingthreats$setHuntStartedGameTime(level.getGameTime());
		}
		return player;
	}
}
