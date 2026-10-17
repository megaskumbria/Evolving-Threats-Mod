package com.example.entity.ai;

import com.example.entity.RaidParticipantEntity;
import com.example.raid.RaidAnchor;
import com.example.raid.RaidRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Пока якорь жив и GPS активен — держит цель на игроке рейда (как охота надсмотрщика).
 */
public class RaidHuntGoal extends Goal {

	private final Mob mob;
	private final RaidParticipantEntity raid;

	public RaidHuntGoal(Mob mob, RaidParticipantEntity raid) {
		this.mob = mob;
		this.raid = raid;
		this.setFlags(EnumSet.of(Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		if (!raid.evolvingthreats$isRaidParticipant() || !raid.evolvingthreats$isRaidGpsActive()) {
			return false;
		}
		UUID raidId = raid.evolvingthreats$getRaidId();
		if (raidId == null) {
			return false;
		}
		if (!(mob.level() instanceof ServerLevel serverLevel)) {
			return false;
		}
		if (!raid.evolvingthreats$isRaidAnchor() && !RaidRegistry.isAnchorAlive(serverLevel, raidId)) {
			return false;
		}
		return resolveHuntTarget() != null;
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
		if (mob.getTarget() != target) {
			mob.setTarget(target);
		}
	}

	private LivingEntity resolveHuntTarget() {
		UUID id = raid.evolvingthreats$getRaidHuntTargetId();
		if (id == null) {
			return null;
		}
		if (!(mob.level() instanceof ServerLevel serverLevel)) {
			return null;
		}

		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(id);
		if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) {
			return null;
		}
		if (player.level() != mob.level()) {
			return null;
		}

		double maxSqr = RaidAnchor.HUNT_MAX_DISTANCE * RaidAnchor.HUNT_MAX_DISTANCE;
		if (mob.distanceToSqr(player) > maxSqr) {
			mob.setTarget(null);
			return null;
		}

		return player;
	}
}
