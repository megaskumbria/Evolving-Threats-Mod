package com.example.skeleton;

import com.example.entity.SkeletonHorsemanMount;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Skeleton on a skeleton horse.
 * Vanilla trap horses only appear in thunderstorms and are very rare.
 * Here a normal skeleton on the surface at night sometimes spawns already mounted.
 * The rider is the same skeleton as on foot: shield, potions, arrows, armor, and weapons already applied.
 * Thunder trap horses can still spawn. The game no longer re-equips those riders:
 * it does not put an iron helmet over ours or strip bow/helmet enchants.
 */
public final class SkeletonHorseman {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * Enables both night horseman spawn and blocking thunder traps from re-equipping the rider.
	 * false — horsemen only from thunderstorms again, and traps dress riders vanilla-style.
	 */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a normal surface-night skeleton spawns on a horse.
	 * 0.05 = 5%. Rolled after armor and weapons, Overworld only, open sky above.
	 * Caves, spawners, Nether, and thunder traps skip this roll.
	 */
	public static final float SPAWN_CHANCE = 0.05f;

	/**
	 * Beyond this distance (blocks) horse and rider despawn like a normal skeleton.
	 * 128 — same as monsters. 0 — never despawn until killed.
	 * Skeleton horses alone do not despawn, so without this the world fills with horsemen.
	 */
	public static final double DESPAWN_DISTANCE_BLOCKS = 128.0;

	// ========================================================================

	private SkeletonHorseman() {
	}

	/**
	 * End of skeleton spawn. Horse is created here and the skeleton mounts it.
	 * If the roll fails — skeleton stays on foot.
	 */
	public static void tryMount(AbstractSkeleton skeleton, ServerLevelAccessor level, EntitySpawnReason reason) {
		if (!canMountNaturally(skeleton, level, reason)) {
			return;
		}
		if (skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		mount((ServerLevel) level, skeleton);
	}

	/** Command: already equipped skeleton on a horse. null — mechanic disabled. */
	public static @Nullable Skeleton spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			throw new IllegalStateException("Failed to create skeleton horseman");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		skeleton.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(skeleton.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		mount(level, skeleton);
		if (lookAt != null) {
			skeleton.setTarget(lookAt);
		}
		level.addFreshEntity(skeleton);
		return skeleton;
	}

	private static boolean canMountNaturally(AbstractSkeleton skeleton, ServerLevelAccessor level, EntitySpawnReason reason) {
		if (!ENABLED || !(skeleton instanceof Skeleton)) {
			return false;
		}
		if (reason != EntitySpawnReason.NATURAL || skeleton.isPassenger()) {
			return false;
		}
		if (!(level instanceof ServerLevel server) || server.dimension() != Level.OVERWORLD) {
			return false;
		}
		return server.canSeeSky(skeleton.blockPosition());
	}

	/**
	 * Mod horses should not live forever. Far from players, both despawn.
	 * If the rider died, the horse despawns too — not a pet.
	 * If a player mounted in time, the horse stays.
	 */
	public static void tickMount(SkeletonHorse horse) {
		if (!ENABLED) {
			return;
		}
		if (!(horse instanceof SkeletonHorsemanMount mount) || !mount.evolvingthreats$isHorseman()) {
			return;
		}
		if (!(horse.level() instanceof ServerLevel level) || horse.isRemoved()) {
			return;
		}
		boolean skeletonRiding = false;
		for (Entity passenger : horse.getPassengers()) {
			if (passenger instanceof Player) {
				return;
			}
			if (passenger instanceof AbstractSkeleton) {
				skeletonRiding = true;
			}
		}
		if (!skeletonRiding) {
			horse.discard();
			return;
		}
		if (DESPAWN_DISTANCE_BLOCKS <= 0.0 || level.getNearestPlayer(horse, DESPAWN_DISTANCE_BLOCKS) != null) {
			return;
		}
		for (Entity passenger : List.copyOf(horse.getPassengers())) {
			passenger.discard();
		}
		horse.discard();
	}

	private static void mount(ServerLevel level, AbstractSkeleton skeleton) {
		SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.JOCKEY);
		if (horse == null) {
			return;
		}
		horse.snapTo(skeleton.getX(), skeleton.getY(), skeleton.getZ(), skeleton.getYRot(), 0.0f);
		horse.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(horse.blockPosition()),
				EntitySpawnReason.JOCKEY,
				(SpawnGroupData) null);
		horse.setTamed(true);
		horse.setAge(0);
		horse.setTrap(false);
		if (horse instanceof SkeletonHorsemanMount mount) {
			mount.evolvingthreats$setHorseman(true);
		}
		skeleton.startRiding(horse);
		level.addFreshEntity(horse);
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
