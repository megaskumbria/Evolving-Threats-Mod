package com.example.entity.ai;

import com.example.entity.ZombieOverseerEntity;
import com.example.overseer.ZombieOverseer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Периодически призывает обычных зомби, если цель рядом.
 */
public class OverseerSummonGoal extends Goal {

	private final Zombie zombie;
	private final ZombieOverseerEntity overseer;

	public OverseerSummonGoal(Zombie zombie, ZombieOverseerEntity overseer) {
		this.zombie = zombie;
		this.overseer = overseer;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		if (overseer.evolvingthreats$getSummonCooldownTicks() > 0) {
			return false;
		}
		if (overseer.evolvingthreats$getActiveServantCount() >= ZombieOverseer.MAX_SERVANTS_ALIVE) {
			return false;
		}
		LivingEntity target = zombie.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		double maxDistSqr = ZombieOverseer.SUMMON_PLAYER_MAX_DISTANCE * ZombieOverseer.SUMMON_PLAYER_MAX_DISTANCE;
		return zombie.distanceToSqr(target) <= maxDistSqr;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		if (!(zombie.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity target = zombie.getTarget();
		if (target == null) {
			return;
		}

		int room = ZombieOverseer.MAX_SERVANTS_ALIVE - overseer.evolvingthreats$getActiveServantCount();
		int toSpawn = Math.min(ZombieOverseer.SUMMON_ZOMBIES_PER_CAST, room);

		for (int i = 0; i < toSpawn; i++) {
			spawnServant(level, target);
		}

		level.playSound(
				null,
				zombie.blockPosition(),
				SoundEvents.EVOKER_PREPARE_SUMMON,
				SoundSource.HOSTILE,
				0.6f,
				0.5f
		);

		overseer.evolvingthreats$setSummonCooldownTicks(ZombieOverseer.SUMMON_COOLDOWN_TICKS);
	}

	private void spawnServant(ServerLevel level, LivingEntity target) {
		Zombie servant = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (servant == null) {
			return;
		}

		var random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2;
		double radius = 2.0 + random.nextDouble() * 2.0;
		double x = zombie.getX() + Math.cos(angle) * radius;
		double z = zombie.getZ() + Math.sin(angle) * radius;
		servant.snapTo(x, zombie.getY(), z, random.nextFloat() * 360.0f, 0.0f);

		DifficultyInstance difficulty = level.getCurrentDifficultyAt(servant.blockPosition());
		servant.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, null);
		servant.setTarget(target);
		level.addFreshEntity(servant);

		overseer.evolvingthreats$setActiveServantCount(overseer.evolvingthreats$getActiveServantCount() + 1);
	}
}
