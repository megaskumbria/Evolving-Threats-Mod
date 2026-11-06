package com.example.creeper;

import com.example.advancement.ModAdvancements;
import com.example.entity.DynamiteCreeperEntity;
import com.example.entity.SapperCreeperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 A rare cryper that blows up what a player is standing on.
 The force of the explosion and hiss like a regular cryper (1.5 seconds).
 If there is no way to the player and he is not visible (wall, closed house) - comes as close as possible,
 * waits {@link #WALL DELAY MIN SECONDS}-{@link #WALL DELAY MAX SECONDS} and explodes against the wall.
 In a clean field, the hiss goes out if you move away, like a regular cryper.
 * The player remembers {@link #MEMORY SECONDS} seconds, even if he is out of sight.
 */
public final class SapperCreeper {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False: Bombers don’t show up, the team doesn’t make a bomber either. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0-1.0) at spawning of a regular cryper to become a bomber. 0.05 = 5%.
	 The dynamite thrower will not fall on this cryper. A loaded cryper could be a bomber.
	 * The Spawn team does not take the chance.
	 */
	public static final float SPAWN_CHANCE = 0.05f;

	/**
	 Horizontally closer to this (blocks) to the column under the player - immediately undermining.
	 2.2 Close to the tower or cage on which the player is standing.
	 */
	public static final double DETONATE_HORIZONTAL = 2.2;

	/**
	 A player above the cryper by at least as many blocks is a tower/staircase/floor.
	 • Destruction at the base, even if there is no way up.
	 */
	public static final int ELEVATED_BLOCKS = 2;

	/**
	 How close (blocks, cells) the path must come to the goal in order to count “you can get there.”
	 * 0 = you need to stand exactly in the player's cage: in a clean field it is often "no way", and after the impact the cryper exploded.
	 2 = suffice to approach closely, as in battle.
	 */
	public static final int PATH_REACH_BLOCKS = 2;

	/*How long to wait at the wall before the explosion, seconds (random number between them). */
	public static final int WALL_DELAY_MIN_SECONDS = 8;
	public static final int WALL_DELAY_MAX_SECONDS = 10;

	/**
	 * Consider that the cryper hit the wall and is worth it if {@link #STUCK CHECK TICKS} tics
	 It has moved by fewer than so many blocks.
	 */
	public static final double STUCK_MOVE_BLOCKS = 0.6;

	/** The checkout window is stuck, ticks. 20 = 1 second. */
	public static final int STUCK_CHECK_TICKS = 20;

	/**
	 How many seconds does the player remember after he stopped seeing him (run into the house and closed).
	 During this time manages to reach the wall, wait 8-10 seconds and explode.
	 */
	public static final int MEMORY_SECONDS = 90;

	/*Next to that (blocks) from the remembered player, the bomber surrenders. */
	public static final double CHASE_MAX_DISTANCE = 40.0;

	/*How far the player can see. */
	public static final double FOLLOW_RANGE = 32.0;

	/*Speed of running to support (1.0 = usual step of the cryper). */
	public static final double MOVE_SPEED = 1.1;

	/*How often to re-check "is there a way", ticks. 10 = 0.5 sec. */
	public static final int PATH_CHECK_TICKS = 10;

	// ========================================================================

	private SapperCreeper() {
	}

	public static boolean isSapper(Creeper creeper) {
		return ENABLED
				&& creeper instanceof SapperCreeperEntity data
				&& data.evolvingthreats$isSapperCreeper();
	}

	/** Call from finalizeSpawn. The team spawn puts the flag in advance. */
	public static void rollOnSpawn(Creeper creeper, EntitySpawnReason reason) {
		if (!ENABLED || !(creeper instanceof SapperCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$isSapperCreeper()) {
			return;
		}
		if (creeper instanceof DynamiteCreeperEntity dynamite && dynamite.evolvingthreats$isDynamiteCreeper()) {
			return;
		}
		if (reason == EntitySpawnReason.COMMAND) {
			return;
		}
		if (creeper.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		apply(creeper);
	}

	/*Team: The bomber in front of the player. */
	public static Creeper spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper == null) {
			throw new IllegalStateException("Failed to create sapper creeper");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		creeper.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (creeper instanceof SapperCreeperEntity data) {
			data.evolvingthreats$setSapperCreeper(true);
		}
		creeper.finalizeSpawn(level, level.getCurrentDifficultyAt(creeper.blockPosition()), EntitySpawnReason.COMMAND, null);
		apply(creeper);
		if (lookAt != null) {
			creeper.setTarget(lookAt);
		}
		level.addFreshEntity(creeper);
		return creeper;
	}

	private static void apply(Creeper creeper) {
		if (creeper instanceof SapperCreeperEntity data) {
			data.evolvingthreats$setSapperCreeper(true);
		}
		var follow = creeper.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(FOLLOW_RANGE);
		}
	}

	/*The cage under the feet of the player is what needs to be blown up. */
	public static BlockPos supportUnder(LivingEntity target) {
		return target.blockPosition().below();
	}

	public static boolean canReach(Creeper creeper, LivingEntity target) {
		Path path = creeper.getNavigation().createPath(target, PATH_REACH_BLOCKS);
		return path != null && path.canReach();
	}

	public static boolean canReach(Creeper creeper, BlockPos pos) {
		Path path = creeper.getNavigation().createPath(pos, PATH_REACH_BLOCKS);
		return path != null && path.canReach();
	}

	/*There is no wall between the cryper and the player: kick and retreat in a clean field, not a house. */
	public static boolean canSee(Creeper creeper, LivingEntity target) {
		return creeper.hasLineOfSight(target);
	}

	public static double horizontalDistance(Creeper creeper, LivingEntity target) {
		double dx = target.getX() - creeper.getX();
		double dz = target.getZ() - creeper.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	public static boolean isElevated(Creeper creeper, LivingEntity target) {
		return target.getY() >= creeper.getY() + ELEVATED_BLOCKS;
	}

	/** Accidental pause at the wall, ticks. */
	public static int randomWallDelayTicks(Creeper creeper) {
		int min = WALL_DELAY_MIN_SECONDS * 20;
		int max = WALL_DELAY_MAX_SECONDS * 20;
		return max <= min ? min : creeper.getRandom().nextIntBetweenInclusive(min, max);
	}

	/*Fire the fuse: the usual hiss of 1.5 seconds and an explosion of normal force. A second call does nothing. */
	public static void detonate(Creeper creeper) {
		if (!creeper.isIgnited()) {
			creeper.ignite();
			if (creeper.getTarget() instanceof ServerPlayer player) {
				ModAdvancements.onCoverBlast(player);
			}
		}
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
