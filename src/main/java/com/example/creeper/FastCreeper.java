package com.example.creeper;

import com.example.entity.DynamiteCreeperEntity;
import com.example.entity.FastCreeperEntity;
import com.example.entity.FeintCreeperEntity;
import com.example.entity.SapperCreeperEntity;
import com.example.mixin.CreeperFuseAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 A rare cryper from which you can not escape.
 Runs much faster than the sprint: in a straight line catches up with the player and jumps in the sprint.
 Hisses for half a second and explodes. A normal cryper has a hissing of 1.5 seconds.
 There is no such thing as a bomber, a dynamite thrower and a deception: they have their own way of getting to the player.
 */
public final class FastCreeper {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - such crypers do not appear, the team does not spawn either. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0–1.0) that a regular cryper will be super fast. 0.03 = 3%.
	 FIXED: From day one, the threat scale and the moon do not depend.
	 * That's the starting number. The bomber and shield breaker have 5%, the loaded 1%.
	 * 0 = commands only. 1 = every cryper is like this.
	 The team skip this roll.
	 */
	public static final float SPAWN_CHANCE = 0.03f;

	/**
	 Speed of chase. The usual cryper 0.25 - the player's sprint away from him.
	 0.50 = twice as fast as usual: the sprint and jumps in the sprint do not overtake it, in a straight line it catches up.
	 * 0.35 - you can still come off on a flat field. 0.70 - barely dodge.
	 */
	public static final double CHASE_SPEED = 0.50;

	/**
	 How far (blocks) the player remembers and keeps running.
	 The usual cryper is about 16: he ran out of this zone - he throws a chase.
	 48 = to escape "a corner of sight" will not work, he still catches up.
	 * 16 = normal. 0 = loses the target almost immediately.
	 */
	public static final double FOLLOW_RANGE = 48.0;

	/**
	 How many seconds does it hiss before it explodes? 0.5 = half a second.
	 * A normal cryper has 1.5. Same force of explosion.
	 The game counts 0 and less as one moment, otherwise the explosion will occur in the same tick as the hiss.
	 */
	public static final float FUSE_SECONDS = 0.5f;

	// ========================================================================

	private FastCreeper() {
	}

	public static boolean isFast(Creeper creeper) {
		return ENABLED
				&& creeper instanceof FastCreeperEntity data
				&& data.evolvingthreats$isFastCreeper();
	}

	/*End of the creeper. Team spawn does not throw a chance: the flag is put in advance. */
	public static void rollOnSpawn(Creeper creeper, EntitySpawnReason reason) {
		if (!ENABLED || !(creeper instanceof FastCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$isFastCreeper()) {
			apply(creeper);
			return;
		}
		// The bomber and the thrower catch up in their own way. The deception is quick only up close. We can't substitute for speed.
		if (creeper instanceof SapperCreeperEntity sapper && sapper.evolvingthreats$isSapperCreeper()) {
			return;
		}
		if (creeper instanceof DynamiteCreeperEntity dynamite && dynamite.evolvingthreats$isDynamiteCreeper()) {
			return;
		}
		if (creeper instanceof FeintCreeperEntity feint && feint.evolvingthreats$isFeintCreeper()) {
			return;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return;
		}
		if (SPAWN_CHANCE <= 0.0f || creeper.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		apply(creeper);
	}

	/*Speed, chase range and short hissing. Call both when it appears and when the cryper is loaded from the world. */
	public static void apply(Creeper creeper) {
		if (!(creeper instanceof FastCreeperEntity data)) {
			return;
		}
		data.evolvingthreats$setFastCreeper(true);
		var speed = creeper.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(Math.max(0.0, CHASE_SPEED));
		}
		var follow = creeper.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(Math.max(1.0, FOLLOW_RANGE));
		}
		if (creeper instanceof CreeperFuseAccessor fuse) {
			fuse.evolvingthreats$setMaxSwell(fuseTicks());
		}
	}

	/*Tics of hissing. 20 ticks = 1 second. 0.5 seconds = 10 ticks. */
	public static int fuseTicks() {
		return Math.max(1, Math.round(FUSE_SECONDS * 20.0f));
	}

	/*Team: A quick cryper in front of the player. null - the mechanics are off. */
	public static @Nullable Creeper spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper == null) {
			throw new IllegalStateException("Failed to create fast creeper");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		creeper.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (creeper instanceof FastCreeperEntity data) {
			data.evolvingthreats$setFastCreeper(true);
		}
		creeper.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(creeper.blockPosition()),
				EntitySpawnReason.COMMAND,
				null);
		apply(creeper);
		if (lookAt != null) {
			creeper.setTarget(lookAt);
		}
		level.addFreshEntity(creeper);
		return creeper;
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
