package com.example.entity.ai;

import com.example.entity.ZombieOverseerEntity;
import com.example.overseer.ZombieOverseer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 Periodically calls on ordinary zombies if the target is near.
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
		int spawned = 0;

		for (int i = 0; i < toSpawn; i++) {
			if (spawnServant(level, target)) {
				spawned++;
			}
		}

		// There's no room near the wall - a short pause to try again when it comes out into the hallway.
		int cooldown = spawned > 0 ? ZombieOverseer.SUMMON_COOLDOWN_TICKS : ZombieOverseer.SUMMON_FAIL_COOLDOWN_TICKS;
		overseer.evolvingthreats$setSummonCooldownTicks(cooldown);
		if (spawned == 0) {
			return;
		}

		level.playSound(
				null,
				zombie.blockPosition(),
				SoundEvents.EVOKER_PREPARE_SUMMON,
				SoundSource.HOSTILE,
				0.6f,
				0.5f
		);
	}

	/*True - the zombie stood on the free floor, not inside the block. */
	private boolean spawnServant(ServerLevel level, LivingEntity target) {
		Vec3 pos = findOpenSpot(level);
		if (pos == null) {
			return false;
		}
		Zombie servant = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (servant == null) {
			return false;
		}

		servant.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
		DifficultyInstance difficulty = level.getCurrentDifficultyAt(servant.blockPosition());
		servant.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, null);
		servant.setTarget(target);
		level.addFreshEntity(servant);

		overseer.evolvingthreats$setActiveServantCount(overseer.evolvingthreats$getActiveServantCount() + 1);
		return true;
	}

	/**
	 The point near where the zombie is on the block, and the body and head in the air.
	 Otherwise he appears in the wall and suffocates.
	 */
	private Vec3 findOpenSpot(ServerLevel level) {
		var random = level.getRandom();
		double min = ZombieOverseer.SUMMON_RADIUS_MIN;
		double max = Math.max(min, ZombieOverseer.SUMMON_RADIUS_MAX);
		int baseY = zombie.blockPosition().getY();
		for (int attempt = 0; attempt < ZombieOverseer.SUMMON_SPOT_TRIES; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = min + random.nextDouble() * (max - min);
			double x = zombie.getX() + Math.cos(angle) * radius;
			double z = zombie.getZ() + Math.sin(angle) * radius;
			for (int dy = ZombieOverseer.SUMMON_VERTICAL_STEPS; dy >= -ZombieOverseer.SUMMON_VERTICAL_STEPS; dy--) {
				int y = baseY + dy;
				if (canStand(level, x, y, z)) {
					return new Vec3(x, y, z);
				}
			}
		}
		return null;
	}

	/*The floor under the feet is hard, the body itself in two blocks does not cross the blocks and not in the lava. */
	private boolean canStand(ServerLevel level, double x, int y, double z) {
		BlockPos feet = BlockPos.containing(x, y, z);
		if (!level.getWorldBorder().isWithinBounds(feet)) {
			return false;
		}
		BlockPos ground = feet.below();
		if (!hasCollision(level, ground)) {
			return false;
		}
		if (hasCollision(level, feet) || hasCollision(level, feet.above())) {
			return false;
		}
		if (level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(feet.above()).is(FluidTags.LAVA)) {
			return false;
		}
		AABB body = EntityTypes.ZOMBIE.getDimensions().makeBoundingBox(x, y, z);
		return level.noCollision(body);
	}

	private static boolean hasCollision(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.getCollisionShape(level, pos).isEmpty();
	}
}
