package com.example.creeper;

import com.example.entity.DynamiteCreeperEntity;
import com.example.entity.SapperCreeperEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 The usual-looking cryper, which does not run up to explode, but throws lit dynamite and retreats.
 It looks like a vanilla cryper - the player only recognizes it when TNT arrives.
 */
public final class DynamiteCreeper {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - all crypers are ordinary, the team spawn does not throw either. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0-1.0) that a common cryper will be a thrower when it appears in the world.
	 * 0.04 = 4%. The Spawn team doesn't take that chance - it always creates a thrower.
	 */
	public static final float SPAWN_CHANCE = 0.04f;

	/**
	 Closer than that (blocks) the cryper is backing away from the player and does not throw dynamite.
	 8 = holds a distance of about 8 blocks.
	 */
	public static final double RETREAT_DISTANCE = 8.0;

	/**
	 * Beyond that (blocks) the cryper comes closer to throw.
	 * Between {@link #RETREAT DISTANCE} and this number he circles sideways and throws.
	 */
	public static final double THROW_MAX_DISTANCE = 16.0;

	/**
	 At what distance (blocks) the cryper tries to keep while throwing dynamite.
	 * Less {@link #RETREAT DISTANCE} he's backing up, more {@link #THROW MAX DISTANCE} - fits.
	 */
	public static final double HOLD_DISTANCE = 12.0;

	/**
	 How many blocks away from the player moves the point to which he goes.
	 * Bigger, bigger circle. 6 = visibly running, but remains in the throw zone.
	 */
	public static final double STRAFE_SIDE_BLOCKS = 6.0;

	/*Speed of running sideways (1.0 = usual step of the cryper). */
	public static final double STRAFE_SPEED = 1.1;

	/*How often does the spin side change, seconds (random number between them). */
	public static final int STRAFE_SWITCH_MIN_SECONDS = 2;
	public static final int STRAFE_SWITCH_MAX_SECONDS = 4;

	/**
	 * Beyond that (blocks) the thrower throws the chase.
	 Slightly more vanilla range of sight of the cryper so that he had time to come out on the throw.
	 */
	public static final double CHASE_MAX_DISTANCE = 28.0;

	/*How far (blocks) the cryper "sees" the player. A normal cryper has about 16. */
	public static final double FOLLOW_RANGE = 32.0;

	/*Pause between throws, seconds. 8 = one dynamite every 8 seconds. */
	public static final int THROW_COOLDOWN_SECONDS = 8;

	/*Dynamite fuse: minimum and maximum, seconds (random number between them). 2 and 3 = explosion in 2-3 seconds. */
	public static final int FUSE_MIN_SECONDS = 2;
	public static final int FUSE_MAX_SECONDS = 3;

	/*Retreat rate (1.0 = usual cryper step, 1.2 = slightly faster escapes). */
	public static final double RETREAT_SPEED = 1.2;

	/*The speed at which it fits the throw distance. */
	public static final double APPROACH_SPEED = 1.0;

	/**
	 Horizontal velocity of dynamite, blocks per tick (20 ticks = 1 sec).
	 0.70 = reaches the player in 12 blocks in about a second and falls to his feet, not over his head.
	 * Bigger, faster and more flattering. Less is above the arc.
	 */
	public static final double THROW_BLOCKS_PER_TICK = 0.70;

	/**
	 How many tics before the explosion dynamite should already be at the feet.
	 8 = 0.4 seconds lies on the ground next to the player, then explodes.
	 Less - the explosion is still in flight. More is longer, the player will have time to leave.
	 */
	public static final int LAND_BEFORE_EXPLODE_TICKS = 8;

	/**
	 Random shift of the drop point, blocks. 0.4 = almost the player, but not the perfect sniper.
	 * More - more often misses the legs.
	 */
	public static final double AIM_SPREAD_BLOCKS = 0.4;

	/*Gravity of vanilla TNT per tick. Don’t change unless the game itself has changed. */
	private static final double TNT_GRAVITY = 0.04;

	/*Deceleration of TNT in the air for tick (vanilla). Don’t change unless the game itself has changed. */
	private static final double TNT_AIR_DRAG = 0.98;

	// ========================================================================

	private DynamiteCreeper() {
	}

	public static boolean isDynamite(Creeper creeper) {
		return ENABLED
				&& creeper instanceof DynamiteCreeperEntity data
				&& data.evolvingthreats$isDynamiteCreeper();
	}

	/** Call from finalizeSpawn. The team spawn puts the flag in advance and does not get here again. */
	public static void rollOnSpawn(Creeper creeper, ServerLevelAccessor level, EntitySpawnReason reason) {
		if (!ENABLED || !(creeper instanceof DynamiteCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$isDynamiteCreeper()) {
			return;
		}
		if (creeper instanceof SapperCreeperEntity sapper && sapper.evolvingthreats$isSapperCreeper()) {
			return;
		}
		// Charged cryper dynamite doesn't throw. He could be a bomber or a shield breaker.
		if (creeper.isPowered()) {
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

	/*Team: Cryper-thrower in front of the player (or at the source point of the team). */
	public static Creeper spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper == null) {
			throw new IllegalStateException("Failed to create dynamite creeper");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		creeper.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (creeper instanceof DynamiteCreeperEntity data) {
			data.evolvingthreats$setDynamiteCreeper(true);
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
		if (creeper instanceof DynamiteCreeperEntity data) {
			data.evolvingthreats$setDynamiteCreeper(true);
		}
		var follow = creeper.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(FOLLOW_RANGE);
		}
	}

	public static int cooldownTicks() {
		return THROW_COOLDOWN_SECONDS * 20;
	}

	/*Toss lighted dynamite at the target. Dynamite, a common vanilla TNT, falls to your feet. */
	public static void throwTnt(Creeper creeper, LivingEntity target) {
		if (!(creeper.level() instanceof ServerLevel level)) {
			return;
		}
		double spawnX = creeper.getX();
		double spawnY = creeper.getY() + creeper.getBbHeight() * 0.8;
		double spawnZ = creeper.getZ();

		PrimedTnt tnt = new PrimedTnt(level, spawnX, spawnY, spawnZ, creeper);
		int fuseMin = FUSE_MIN_SECONDS * 20;
		int fuseMax = FUSE_MAX_SECONDS * 20;
		int fuse = fuseMax <= fuseMin ? fuseMin : creeper.getRandom().nextIntBetweenInclusive(fuseMin, fuseMax);
		tnt.setFuse(fuse);

		double spread = (creeper.getRandom().nextDouble() - 0.5) * AIM_SPREAD_BLOCKS;
		double spreadZ = (creeper.getRandom().nextDouble() - 0.5) * AIM_SPREAD_BLOCKS;
		// A little preemptive if the player runs. A worthwhile player - with almost no shift.
		Vec3 motion = target.getDeltaMovement();
		double landX = target.getX() + motion.x * 8.0 + spread;
		double landZ = target.getZ() + motion.z * 8.0 + spreadZ;
		double landY = target.getY();

		double dx = landX - spawnX;
		double dy = landY - spawnY;
		double dz = landZ - spawnZ;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		int travelTicks = (int) Math.ceil(horizontal / THROW_BLOCKS_PER_TICK);
		int latestLanding = Math.max(6, fuse - LAND_BEFORE_EXPLODE_TICKS);
		travelTicks = Mth.clamp(travelTicks, 6, latestLanding);

		double dragSum = horizontalDragSum(travelTicks);
		double vx = dragSum < 1.0E-4 ? 0.0 : dx / dragSum;
		double vz = dragSum < 1.0E-4 ? 0.0 : dz / dragSum;
		double vy = verticalSpeedToLand(dy, travelTicks);
		tnt.setDeltaMovement(vx, vy, vz);

		level.addFreshEntity(tnt);
		level.playSound(null, creeper.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0f, 1.0f);
	}

	/*How many blocks horizontally will TNT fly for {@code ticks} if the initial speed is 1? */
	private static double horizontalDragSum(int ticks) {
		double sum = 0.0;
		double drag = 1.0;
		for (int i = 0; i < ticks; i++) {
			sum += drag;
			drag *= TNT_AIR_DRAG;
		}
		return sum;
	}

	/**
	 * Initial speed up to {@code ticks} tics TNT was at the desired height.
	 Each tick game first adds gravity, then moves, then slows down (like vanilla TNT).
	 */
	private static double verticalSpeedToLand(double dy, int ticks) {
		double atZero = simulateVertical(0.0, ticks);
		double atOne = simulateVertical(1.0, ticks);
		double perSpeed = atOne - atZero;
		if (Math.abs(perSpeed) < 1.0E-4) {
			return 0.2;
		}
		return (dy - atZero) / perSpeed;
	}

	private static double simulateVertical(double initialSpeed, int ticks) {
		double y = 0.0;
		double vy = initialSpeed;
		for (int i = 0; i < ticks; i++) {
			vy -= TNT_GRAVITY;
			y += vy;
			vy *= TNT_AIR_DRAG;
		}
		return y;
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
