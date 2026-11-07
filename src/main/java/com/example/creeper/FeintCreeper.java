package com.example.creeper;

import com.example.entity.DynamiteCreeperEntity;
import com.example.entity.FastCreeperEntity;
import com.example.entity.FeintCreeperEntity;
import com.example.entity.SapperCreeperEntity;
import com.example.mixin.CreeperFuseAccessor;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Looks like a normal creeper, but speed potion particles always swirl around it.
 * That is how you spot it early: after the burst there is no time to react.
 * Particles are visual only — no real potion effect and no extra speed from them.
 * While the player is far away, speed and fuse match vanilla.
 * At about 6–7 blocks the disguise drops: it runs like the super-fast creeper and hisses for half a second.
 * No custom name. Never sapper, dynamite thrower, or super-fast at spawn.
 * Can still be charged or a shield-breaker.
 * Chance is FIXED from day 40 ({@link ThreatTable#FEINT_CREEPER_DAY}). None before that.
 * Threat and the moon do not change the chance.
 */
public final class FeintCreeper {

	// ===================== SETTINGS (change these numbers) =====================

	/** Enable the mechanic. false — no feint creepers, command spawn disabled too. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a normal creeper is a feint. 0.003 = 0.3%.
	 * FIXED from day 40 ({@link ThreatTable#FEINT_CREEPER_DAY}): none before that day,
	 * then this chance exactly. Does not scale with threat or moon.
	 * Rarer than super-fast (that one is 3%, also from day 40). 0 = command only.
	 * 1 = every remaining creeper after day 40.
	 * Command ignores the day and this chance.
	 */
	public static final float SPAWN_CHANCE = 0.003f;

	/**
	 * Inner edge of the reveal zone, in blocks.
	 * Each feint picks one distance between this and TRIGGER_MAX once.
	 * 6 = burst almost in melee. Below 3 is pointless: vanilla already starts hissing around 3 blocks.
	 */
	public static final float TRIGGER_MIN = 6.0f;

	/**
	 * Outer edge of the reveal zone, in blocks. 7 = like the range where vanilla still keeps hissing.
	 * 6 and 7 together: “somewhere around 6–7 blocks”. Equal values = always the same distance.
	 */
	public static final float TRIGGER_MAX = 7.0f;

	/**
	 * Speed after reveal. Vanilla creeper is 0.25 — sprint outruns that.
	 * 0.50 = same as super-fast: catches sprint and sprint-jumps on flat ground.
	 * Change only here. Super-fast (FastCreeper.CHASE_SPEED) does not use this value.
	 */
	public static final double CHASE_SPEED = 0.50;

	/**
	 * Speed while disguised. 0.25 = normal creeper.
	 * While the player is outside the reveal zone, feint looks the same as vanilla.
	 */
	public static final double CALM_SPEED = 0.25;

	/**
	 * Hiss duration after reveal, in seconds. 0.5 = half a second, like super-fast.
	 * Explosion power unchanged. Half a second is barely enough to run.
	 * 0 or less counts as one tick in the game.
	 */
	public static final float FUSE_SECONDS = 0.5f;

	/**
	 * Hiss length while pretending to be normal. 1.5 = vanilla creeper.
	 * Flint and steel from far away, before reveal, uses this long fuse.
	 */
	public static final float CALM_FUSE_SECONDS = 1.5f;

	/**
	 * true — after the first reveal it never disguises again: fast with short fuse for life,
	 * even if the player runs away. Stepping back cannot make it slow again.
	 * false — fast only while the player is inside the 6–7 block zone. Run away and hiss stops —
	 * slow again; next meeting is another feint act.
	 */
	public static final boolean KEEP_BURST = true;

	/**
	 * Speed potion particles around the feint. true — visible all the time, even while still slow.
	 * Spot it before the burst. false — indistinguishable from a normal creeper.
	 * Not a real potion: speed stays {@link #CALM_SPEED} until reveal.
	 * Spawn potion buffs (MobPotionBuffs) can still speed up the burst separately.
	 */
	public static final boolean SPEED_PARTICLES = true;

	/**
	 * How often to spawn one particle, in ticks. A mob with real speed potion does this about every 4 ticks —
	 * hard to see from far away. 2 = slightly denser, feint visible early.
	 * 1 = every tick, thicker cloud. 0 or less — no particles, same as SPEED_PARTICLES = false.
	 */
	public static final int SPEED_PARTICLE_EVERY_TICKS = 2;

	// ========================================================================

	/** Particle color/type — same as drank speed potion. Computed once. */
	private static ParticleOptions speedParticle;

	private FeintCreeper() {
	}

	public static boolean isFeint(Creeper creeper) {
		return ENABLED
				&& creeper instanceof FeintCreeperEntity data
				&& data.evolvingthreats$isFeintCreeper();
	}

	/** End of creeper spawn. Command spawn skips the roll: flag is set beforehand. */
	public static void rollOnSpawn(Creeper creeper, EntitySpawnReason reason) {
		if (!ENABLED || !(creeper instanceof FeintCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$isFeintCreeper()) {
			ensureDistance(creeper);
			return;
		}
		// Sapper and thrower chase their own way. Super-fast is fast immediately, no disguise.
		if (creeper instanceof SapperCreeperEntity sapper && sapper.evolvingthreats$isSapperCreeper()) {
			return;
		}
		if (creeper instanceof DynamiteCreeperEntity dynamite && dynamite.evolvingthreats$isDynamiteCreeper()) {
			return;
		}
		if (creeper instanceof FastCreeperEntity fast && fast.evolvingthreats$isFastCreeper()) {
			return;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return;
		}
		// None before day 40. From that day the chance stays SPAWN_CHANCE; the moon does not change it.
		if (!ThreatScale.reached(creeper.level(), ThreatTable.FEINT_CREEPER_DAY)) {
			return;
		}
		if (SPAWN_CHANCE <= 0.0f || creeper.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		data.evolvingthreats$setFeintCreeper(true);
		ensureDistance(creeper);
	}

	/**
	 * Every server tick. Far away — normal speed and fuse.
	 * Player enters the zone — burst and half-second fuse.
	 * Hiss still starts up close (~3 blocks) like vanilla:
	 * at 6–7 blocks the blast does not reach yet, so only the sprint happens there.
	 */
	public static void tick(Creeper creeper) {
		if (!isFeint(creeper) || creeper.level().isClientSide()) {
			return;
		}
		if (!(creeper instanceof FeintCreeperEntity data)) {
			return;
		}
		ensureDistance(creeper);
		boolean close = playerInTrigger(creeper, data.evolvingthreats$getTriggerDistance());
		if (close) {
			data.evolvingthreats$setFeintRevealed(true);
		}
		if (shouldBurst(creeper, data, close)) {
			applyBurst(creeper);
		} else {
			applyCalm(creeper);
		}
		spawnSpeedParticles(creeper);
	}

	/** Command: feint in front of the player. Far from the zone it walks like normal. null if disabled. */
	public static @Nullable Creeper spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper == null) {
			throw new IllegalStateException("Failed to create feint creeper");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		creeper.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (creeper instanceof FeintCreeperEntity data) {
			data.evolvingthreats$setFeintCreeper(true);
			data.evolvingthreats$setTriggerDistance(rollDistance(creeper));
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

	private static boolean shouldBurst(Creeper creeper, FeintCreeperEntity data, boolean close) {
		if (KEEP_BURST) {
			return data.evolvingthreats$isFeintRevealed();
		}
		// Without “remember burst”: short fuse while hissing,
		// otherwise stepping back mid-hiss would stretch fuse back to 1.5s.
		return close || (isHissing(creeper) && isShortFuse(creeper));
	}

	private static void applyBurst(Creeper creeper) {
		setSpeed(creeper, CHASE_SPEED);
		setFuse(creeper, fuseTicks(FUSE_SECONDS));
	}

	private static void applyCalm(Creeper creeper) {
		setSpeed(creeper, CALM_SPEED);
		setFuse(creeper, fuseTicks(CALM_FUSE_SECONDS));
	}

	/**
	 * Same blue swirls as speed potion. No effect on the creeper,
	 * so calm speed 0.25 is unchanged by particles.
	 * Visible to about 32 blocks — the game does not render these farther.
	 */
	private static void spawnSpeedParticles(Creeper creeper) {
		if (!SPEED_PARTICLES || SPEED_PARTICLE_EVERY_TICKS <= 0 || !creeper.isAlive()) {
			return;
		}
		if (creeper.tickCount % SPEED_PARTICLE_EVERY_TICKS != 0) {
			return;
		}
		if (!(creeper.level() instanceof ServerLevel level)) {
			return;
		}
		// count = 0: one particle at this spot, velocity 1,1,1 — like potion on a mob.
		level.sendParticles(
				speedParticle(),
				creeper.getRandomX(0.5),
				creeper.getRandomY(),
				creeper.getRandomZ(0.5),
				0,
				1.0,
				1.0,
				1.0,
				1.0,
				1.0,
				1.0);
	}

	private static ParticleOptions speedParticle() {
		if (speedParticle == null) {
			// ambient = false: bright particles like drank potion, not beacon dim ones.
			MobEffectInstance effect = new MobEffectInstance(MobEffects.SPEED, 1, 0, false, true, false);
			speedParticle = MobEffects.SPEED.value().createParticleOptions(effect);
		}
		return speedParticle;
	}

	private static boolean playerInTrigger(Creeper creeper, double blocks) {
		LivingEntity target = creeper.getTarget();
		if (!(target instanceof Player player) || player.isSpectator() || player.isDeadOrDying()) {
			return false;
		}
		double reach = Math.max(0.0, blocks);
		return creeper.distanceToSqr(player) <= reach * reach;
	}

	private static void ensureDistance(Creeper creeper) {
		if (!(creeper instanceof FeintCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$getTriggerDistance() <= 0.0) {
			data.evolvingthreats$setTriggerDistance(rollDistance(creeper));
		}
	}

	private static double rollDistance(Creeper creeper) {
		float min = Math.min(TRIGGER_MIN, TRIGGER_MAX);
		float max = Math.max(TRIGGER_MIN, TRIGGER_MAX);
		if (max <= min) {
			return min;
		}
		return Mth.randomBetween(creeper.getRandom(), min, max);
	}

	private static void setSpeed(Creeper creeper, double speed) {
		var attribute = creeper.getAttribute(Attributes.MOVEMENT_SPEED);
		if (attribute != null && attribute.getBaseValue() != speed) {
			attribute.setBaseValue(Math.max(0.0, speed));
		}
	}

	/**
	 * Fuse length. If already hissing, remaining fraction is kept:
	 * changing max fuse does not explode same tick or reset hiss to zero.
	 */
	private static void setFuse(Creeper creeper, int ticks) {
		if (!(creeper instanceof CreeperFuseAccessor fuse)) {
			return;
		}
		int currentMax = fuse.evolvingthreats$getMaxSwell();
		if (currentMax == ticks) {
			return;
		}
		int swell = fuse.evolvingthreats$getSwell();
		if (swell > 0 && currentMax > 0) {
			int scaled = Math.round(swell * (ticks / (float) currentMax));
			if (swell < currentMax) {
				scaled = Math.min(scaled, Math.max(0, ticks - 1));
			}
			fuse.evolvingthreats$setSwell(Math.max(0, scaled));
		}
		fuse.evolvingthreats$setMaxSwell(Math.max(1, ticks));
	}

	private static boolean isHissing(Creeper creeper) {
		return creeper instanceof CreeperFuseAccessor fuse && fuse.evolvingthreats$getSwell() > 0;
	}

	private static boolean isShortFuse(Creeper creeper) {
		return creeper instanceof CreeperFuseAccessor fuse
				&& fuse.evolvingthreats$getMaxSwell() <= fuseTicks(FUSE_SECONDS);
	}

	/** Fuse ticks. 20 ticks = 1 second. 0.5 seconds = 10 ticks. */
	private static int fuseTicks(float seconds) {
		return Math.max(1, Math.round(seconds * 20.0f));
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
