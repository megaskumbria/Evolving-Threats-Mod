package com.example.creeper;

import com.example.entity.DynamiteCreeperEntity;
import com.example.entity.FastCreeperEntity;
import com.example.entity.FeintCreeperEntity;
import com.example.entity.MagnetCreeperEntity;
import com.example.entity.SapperCreeperEntity;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Rare creeper that pulls the player once it has noticed them.
 * At first the tug is weaker than walking, and an invisible leash stops you at the edge of its sight.
 * Over a few seconds the leash shrinks and the tug grows, until sprinting cannot hold
 * and you are dragged up to the creeper. Then it hisses and explodes like a normal one.
 * Electric sparks while the pull is on — that is how you spot it. No custom name.
 * Never a sapper, dynamite thrower, feint, or super-fast: those already chase their own way.
 * Can still be charged (bigger blast) or a shield-breaker.
 * Chance is FIXED from day 40 ({@link ThreatTable#MAGNET_CREEPER_DAY}). None before that.
 * Threat and the moon do not change the chance.
 */
public final class MagnetCreeper {

	// ===================== SETTINGS (change these numbers) =====================

	/** Enable the mechanic. false — no magnet creepers, command spawn disabled too. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a normal creeper is a magnet. 0.01 = 1%.
	 * FIXED from day 40 ({@link ThreatTable#MAGNET_CREEPER_DAY}): none before that day,
	 * then this chance exactly. Does not scale with threat or moon.
	 * Rolled only if it is not already a sapper, thrower, feint, or super-fast.
	 * Charged and shield-breaker are separate and can stack with this.
	 * 0 = command only. 1 = every remaining creeper after day 40.
	 * Command ignores the day and this chance.
	 */
	public static final float SPAWN_CHANCE = 0.01f;

	/**
	 * Seconds from the moment it notices you until the pull is at full strength
	 * and the leash has shrunk to {@link #ARRIVE_BLOCKS}.
	 * 8 = about eight seconds where you can still hang at the edge of its sight, then you are reeled in.
	 * Higher = longer you can hold the edge. 0 = full pull on the first tick.
	 * Losing the target for {@link #LOSE_GRACE_TICKS} starts this timer over.
	 */
	public static final float RAMP_SECONDS = 8.0f;

	/**
	 * How close the leash shrinks, in blocks. 2 = just outside the creeper, still inside the blast.
	 * The leash STARTS just inside this creeper's sight (follow range: 32 with the sight mod, 16 if that is off).
	 * You cannot be farther than the current leash. It moves from that edge down to this number.
	 * Lower than 1 shoves you into the body. Higher than sight does nothing.
	 */
	public static final double ARRIVE_BLOCKS = 2.0;

	/**
	 * Blocks inside the sight edge where the leash starts.
	 * 1 = you can run to about 31 when sight is 32, not the exact rim.
	 * On the exact rim the creeper forgets you and the pull would start over.
	 * 0 = leash sits on the rim. Do not set this larger than the sight range.
	 */
	public static final double EDGE_MARGIN = 1.0;

	/**
	 * Inward tug at the start, in blocks per tick (20 ticks = 1 second).
	 * Walking is about 0.22, sprinting about 0.28.
	 * 0.10 = weaker than walking, so at first you can run out to the edge of sight and no farther.
	 * 0 = no tug until the leash itself shrinks. Above 0.28 = even sprint loses immediately.
	 */
	public static final double START_PULL_PER_TICK = 0.10;

	/**
	 * Inward tug at the end of {@link #RAMP_SECONDS}, in blocks per tick.
	 * 0.60 = stronger than sprint (0.28) and a speed potion. You are dragged to the creeper.
	 * Raise it if speed effects or ice still let you hold ground. Lower it for a slower reel.
	 */
	public static final double END_PULL_PER_TICK = 0.60;

	/**
	 * Fastest correction in one tick when you are already outside the leash, in blocks.
	 * 1.5 = faster than sprint and about an elytra glide, so a burst cannot open a gap.
	 * Only used past the leash. On the edge, the tug stays the small overshoot, not this number.
	 * Higher = a harsher yank after a pearl or a hit. 0 = the leash never corrects (do not use).
	 */
	public static final double MAX_PULL_PER_TICK = 1.5;

	/**
	 * How much of the tug applies up and down. 0.0 = slide on the ground only.
	 * 1.0 = pull straight at the creeper, including off a ledge or up toward one above you.
	 * 0.5 = a creeper below drags you off a ledge, but you are not lifted like a magnet crane.
	 */
	public static final double VERTICAL_PULL = 0.5;

	/**
	 * Closer than this, in blocks, the tug stops so you are not shoved through the creeper.
	 * 1.5 = still inside a normal blast. Once the ramp is finished, running back out is cancelled.
	 * 0 = keep tugging even inside the body (you bounce).
	 */
	public static final double STOP_BLOCKS = 1.5;

	/**
	 * If you are this many blocks outside the leash (ender pearl, elytra dive), snap back onto it.
	 * 6 = a normal sprint never reaches this; a pearl does.
	 * Snap is skipped if the landing spot is inside a block — then the tug reels you along the wall.
	 * 0 = snap whenever you cross the leash (harsher, more jitter).
	 */
	public static final double SNAP_BLOCKS = 6.0;

	/**
	 * Ticks without a player target before the ramp resets. 20 = 1 second.
	 * A brief corner does not forgive the pull. Longer than this, the next meeting starts weak again.
	 * 0 = reset the instant it loses you.
	 */
	public static final int LOSE_GRACE_TICKS = 20;

	/**
	 * Electric sparks on you and on the creeper while it is pulling.
	 * true = you can see which creeper is the magnet. false = looks vanilla until you feel the tug.
	 */
	public static final boolean SHOW_PARTICLES = true;

	/**
	 * How often to spawn sparks, in ticks. 2 = a thin stream. 1 = denser. 0 = no sparks,
	 * same as {@link #SHOW_PARTICLES} false.
	 */
	public static final int PARTICLE_EVERY_TICKS = 2;

	// ========================================================================

	private MagnetCreeper() {
	}

	public static boolean isMagnet(Creeper creeper) {
		return ENABLED
				&& creeper instanceof MagnetCreeperEntity data
				&& data.evolvingthreats$isMagnetCreeper();
	}

	/** End of creeper spawn. Command spawn skips the roll: the flag is set beforehand. */
	public static void rollOnSpawn(Creeper creeper, EntitySpawnReason reason) {
		if (!ENABLED || !(creeper instanceof MagnetCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$isMagnetCreeper()) {
			return;
		}
		// Those variants already move on their own. A magnet would fight their chase.
		if (creeper instanceof SapperCreeperEntity sapper && sapper.evolvingthreats$isSapperCreeper()) {
			return;
		}
		if (creeper instanceof DynamiteCreeperEntity dynamite && dynamite.evolvingthreats$isDynamiteCreeper()) {
			return;
		}
		if (creeper instanceof FeintCreeperEntity feint && feint.evolvingthreats$isFeintCreeper()) {
			return;
		}
		if (creeper instanceof FastCreeperEntity fast && fast.evolvingthreats$isFastCreeper()) {
			return;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return;
		}
		// None before day 40. From that day the chance stays SPAWN_CHANCE; the moon does not change it.
		if (!ThreatScale.reached(creeper.level(), ThreatTable.MAGNET_CREEPER_DAY)) {
			return;
		}
		if (SPAWN_CHANCE <= 0.0f || creeper.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		data.evolvingthreats$setMagnetCreeper(true);
	}

	/**
	 * Every server tick. No player target — the ramp waits, then resets.
	 * Player target — tug toward the creeper and keep them inside the shrinking leash.
	 * Creative and spectator are not pulled.
	 */
	public static void tick(Creeper creeper) {
		if (!isMagnet(creeper) || creeper.level().isClientSide()) {
			return;
		}
		if (!(creeper instanceof MagnetCreeperEntity data) || !creeper.isAlive()) {
			return;
		}
		Player player = targetPlayer(creeper);
		if (player == null) {
			int lost = data.evolvingthreats$getMagnetLostTicks() + 1;
			data.evolvingthreats$setMagnetLostTicks(lost);
			if (lost > Math.max(0, LOSE_GRACE_TICKS)) {
				data.evolvingthreats$setMagnetPullTicks(0);
			}
			return;
		}
		data.evolvingthreats$setMagnetLostTicks(0);
		int ramp = rampTicks();
		int pulled = data.evolvingthreats$getMagnetPullTicks();
		double progress = ramp <= 0 ? 1.0 : Math.min(1.0, pulled / (double) ramp);
		if (ramp <= 0) {
			data.evolvingthreats$setMagnetPullTicks(0);
		} else if (pulled < ramp) {
			data.evolvingthreats$setMagnetPullTicks(pulled + 1);
		}
		pull(creeper, player, progress);
		spawnParticles(creeper, player);
	}

	/** Command: magnet in front of the player. It targets you immediately. null if disabled. */
	public static @Nullable Creeper spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper == null) {
			throw new IllegalStateException("Failed to create magnet creeper");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		creeper.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (creeper instanceof MagnetCreeperEntity data) {
			data.evolvingthreats$setMagnetCreeper(true);
		}
		creeper.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(creeper.blockPosition()),
				EntitySpawnReason.COMMAND,
				null);
		if (lookAt != null) {
			creeper.setTarget(lookAt);
		}
		level.addFreshEntity(creeper);
		return creeper;
	}

	private static void pull(Creeper creeper, Player player, double progress) {
		Entity body = mountOrSelf(player);
		Vec3 toward = center(creeper).subtract(center(body));
		double dist = toward.length();
		if (dist < 1.0E-4) {
			return;
		}
		Vec3 dir = toward.scale(1.0 / dist);
		double sight = sightBlocks(creeper);
		double arrive = Math.min(sight, Math.max(0.5, ARRIVE_BLOCKS));
		double edge = Math.max(arrive, sight - Math.max(0.0, EDGE_MARGIN));
		double leash = Mth.lerp(progress, edge, arrive);
		double excess = dist - leash;
		double tug = Math.max(0.0, Mth.lerp(progress, START_PULL_PER_TICK, END_PULL_PER_TICK));

		boolean snapped = false;
		if (!player.isPassenger() && SNAP_BLOCKS > 0.0 && excess >= SNAP_BLOCKS) {
			snapped = trySnap(player, dir, excess);
			if (snapped) {
				excess = 0.0;
			}
		}

		double step;
		boolean cancelOutward;
		if (dist <= Math.max(0.0, STOP_BLOCKS)) {
			// Already in its face: do not push through the body.
			// After the ramp, also eat the speed that would carry you back out.
			step = 0.0;
			cancelOutward = progress >= 1.0;
		} else if (excess > 0.0) {
			step = Math.min(maxPull(), Math.max(tug, excess));
			cancelOutward = true;
		} else {
			step = Math.min(maxPull(), tug);
			cancelOutward = snapped;
		}

		// Set speed toward the creeper to `step`. Adding every tick would fling you on ice.
		// Sprint still applies afterwards, so a weak step can be outrun up to the leash.
		Vec3 motion = body.getDeltaMovement();
		double along = motion.dot(dir);
		double vertical = Mth.clamp(VERTICAL_PULL, 0.0, 1.0);
		if (along < step && (cancelOutward || step > 0.0)) {
			Vec3 boost = dir.scale(step - along);
			Vec3 add = new Vec3(boost.x, boost.y * vertical, boost.z);
			motion = motion.add(add);
			if (add.y > 0.0) {
				body.resetFallDistance();
			}
		}
		body.setDeltaMovement(motion);
		body.syncVelocity = true;
	}

	/**
	 * Place the player on the leash. false = the spot is blocked or outside the world,
	 * and the ordinary tug has to reel them instead.
	 */
	private static boolean trySnap(Player player, Vec3 dir, double excess) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 feet = player.position().add(dir.scale(excess));
		BlockPos pos = BlockPos.containing(feet);
		if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		AABB box = player.getDimensions(player.getPose()).makeBoundingBox(feet);
		if (!level.noBlockCollision(player, box)) {
			return false;
		}
		return player.teleportTo(
				level,
				feet.x,
				feet.y,
				feet.z,
				Set.of(),
				player.getYRot(),
				player.getXRot(),
				false);
	}

	private static void spawnParticles(Creeper creeper, Player player) {
		if (!SHOW_PARTICLES || PARTICLE_EVERY_TICKS <= 0 || !creeper.isAlive()) {
			return;
		}
		if (creeper.tickCount % PARTICLE_EVERY_TICKS != 0) {
			return;
		}
		if (!(creeper.level() instanceof ServerLevel level)) {
			return;
		}
		level.sendParticles(
				ParticleTypes.ELECTRIC_SPARK,
				player.getX(),
				player.getY() + player.getBbHeight() * 0.6,
				player.getZ(),
				5,
				0.25,
				0.35,
				0.25,
				0.02);
		level.sendParticles(
				ParticleTypes.ELECTRIC_SPARK,
				creeper.getX(),
				creeper.getY() + creeper.getBbHeight() * 0.6,
				creeper.getZ(),
				4,
				0.2,
				0.25,
				0.2,
				0.01);
	}

	private static Player targetPlayer(Creeper creeper) {
		if (!(creeper.getTarget() instanceof Player player)) {
			return null;
		}
		if (!player.isAlive() || player.isSpectator() || player.isCreative() || player.level() != creeper.level()) {
			return null;
		}
		return player;
	}

	/** A horse or boat moves; the rider does not. Drag the mount. */
	private static Entity mountOrSelf(Player player) {
		Entity body = player;
		for (int guard = 0; guard < 8 && body.getVehicle() != null; guard++) {
			body = body.getVehicle();
		}
		return body;
	}

	private static Vec3 center(Entity entity) {
		return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
	}

	/** Follow range is how far this creeper notices and remembers you. Sight mod raises a normal creeper to 32. */
	private static double sightBlocks(Creeper creeper) {
		var follow = creeper.getAttribute(Attributes.FOLLOW_RANGE);
		double sight = follow == null ? 16.0 : follow.getBaseValue();
		return Math.max(1.0, sight);
	}

	private static double maxPull() {
		return Math.max(0.0, MAX_PULL_PER_TICK);
	}

	private static int rampTicks() {
		return Math.max(0, Math.round(RAMP_SECONDS * 20.0f));
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
