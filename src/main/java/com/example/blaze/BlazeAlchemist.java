package com.example.blaze;

import com.example.advancement.ModAdvancements;
import com.example.entity.AlchemistBlazeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 Rare blaze. In a volley of three balls, the first (when the recharge has passed) is alchemical.
 In appearance it is an ordinary fireball: the same fire, damage and arson.
 In addition, if the fire resistance is longer than the threshold, it is shortened to this threshold.
 * Potion levels remain. Between special shots, the Blaze shoots the same ordinary balls.
 */
public final class BlazeAlchemist {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - alchemists do not appear, and the ball does not cut the resistance. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0–1.0) that a common blaze will appear as an alchemist. 0.04 = 4%.
	 Spawner of the fortress also throws this chance. Spawn team doesn't take chances.
	 */
	public static final float SPAWN_CHANCE = 0.04f;

	/**
	 How many seconds of fire resistance remain after hitting.
	 * 20 = 8 minutes is 20 seconds. If there is less resistance or just as much resistance, do not touch it.
	 * and doesn't update again. Infinite perseverance (team) is also cut down to this number.
	 */
	public static final int FIRE_RESISTANCE_LEFT_SECONDS = 20;

	/**
	 A pause between balls that cut resistance, seconds.
	 20 = the next such ball not earlier than 20 seconds.
	 A volley of the Blaze itself is about once every 9 seconds, so that the cut comes out about every third volley.
	 Less than 1 - the resistance will be cut and neighboring balls of the same volley (between them 0.3 seconds).
	 * 0 = each ball of this blaze cuts the resistance.
	 */
	public static final int SHOT_COOLDOWN_SECONDS = 20;

	// ========================================================================

	private BlazeAlchemist() {
	}

	public static boolean isAlchemist(Blaze blaze) {
		return ENABLED
				&& blaze instanceof AlchemistBlazeEntity data
				&& data.evolvingthreats$isAlchemist();
	}

	/*End of the Blaze server tick. Reduces the pause to the next ball with a cut of resistance. */
	public static void tickCooldown(Blaze blaze) {
		if (!(blaze instanceof AlchemistBlazeEntity data) || !data.evolvingthreats$isAlchemist()) {
			return;
		}
		int cooldown = data.evolvingthreats$getAlchemistCooldown();
		if (cooldown > 0) {
			data.evolvingthreats$setAlchemistCooldown(cooldown - 1);
		}
	}

	/**
	 Call when the Blaze has just created a small fireball.
	 * True - this ball will also cut resistance, the pause begins again.
	 The appearance of the ball does not change: it is the same fire charge as the usual Blaze.
	 */
	public static boolean claimShot(Blaze blaze) {
		if (!isAlchemist(blaze) || !(blaze instanceof AlchemistBlazeEntity data)) {
			return false;
		}
		if (data.evolvingthreats$getAlchemistCooldown() > 0) {
			return false;
		}
		data.evolvingthreats$setAlchemistCooldown(Math.max(0, SHOT_COOLDOWN_SECONDS) * 20);
		return true;
	}

	/**
	 * Alchemical ball hit. The fireball itself does the damage and arson.
	 * There's just a bit of resistance. Shield in front catches a cut.
	 */
	public static void onHitEntity(SmallFireball ball, Entity target) {
		if (!(ball.level() instanceof ServerLevel level)) {
			return;
		}
		if (!(target instanceof LivingEntity living)) {
			return;
		}
		if (blockedByShield(living, ball)) {
			level.playSound(
					null,
					living.getX(),
					living.getY(),
					living.getZ(),
					SoundEvents.SHIELD_BLOCK,
					SoundSource.PLAYERS,
					1.0f,
					0.9f);
			return;
		}
		shortenFireResistance(living, ball.getOwner());
	}

	/** Call from finalizeSpawn. The team spawn puts the flag in advance. */
	public static void rollOnSpawn(Blaze blaze, EntitySpawnReason reason) {
		if (!ENABLED || !(blaze instanceof AlchemistBlazeEntity data)) {
			return;
		}
		if (data.evolvingthreats$isAlchemist()) {
			return;
		}
		if (reason == EntitySpawnReason.COMMAND) {
			return;
		}
		if (blaze.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		data.evolvingthreats$setAlchemist(true);
	}

	/*Team: Alchemist in front of player. null - the mechanics are off. */
	public static @Nullable Blaze spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Blaze blaze = EntityTypes.BLAZE.create(level, EntitySpawnReason.COMMAND);
		if (blaze == null) {
			throw new IllegalStateException("Failed to create blaze alchemist");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		blaze.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (blaze instanceof AlchemistBlazeEntity data) {
			data.evolvingthreats$setAlchemist(true);
		}
		blaze.finalizeSpawn(level, level.getCurrentDifficultyAt(blaze.blockPosition()), EntitySpawnReason.COMMAND, null);
		if (blaze instanceof AlchemistBlazeEntity data) {
			data.evolvingthreats$setAlchemist(true);
			data.evolvingthreats$setAlchemistCooldown(0);
		}
		if (lookAt != null) {
			blaze.setTarget(lookAt);
		}
		level.addFreshEntity(blaze);
		return blaze;
	}

	private static void shortenFireResistance(LivingEntity target, @Nullable Entity source) {
		MobEffectInstance current = target.getEffect(MobEffects.FIRE_RESISTANCE);
		if (current == null) {
			return;
		}
		int leftTicks = Math.max(1, FIRE_RESISTANCE_LEFT_SECONDS) * 20;
		if (!current.isInfiniteDuration() && current.getDuration() <= leftTicks) {
			return;
		}
		if (target instanceof ServerPlayer player) {
			ModAdvancements.onFireCut(player);
		}
		target.forceAddEffect(new MobEffectInstance(
				MobEffects.FIRE_RESISTANCE,
				leftTicks,
				current.getAmplifier(),
				current.isAmbient(),
				current.isVisible(),
				current.showIcon()), source);
	}

	/*The shield looks at the ball - the hit does not cut the resistance. */
	private static boolean blockedByShield(LivingEntity target, SmallFireball ball) {
		if (!target.isBlocking()) {
			return false;
		}
		Vec3 toBall = ball.position().subtract(target.getEyePosition());
		if (toBall.lengthSqr() < 1.0E-4) {
			return false;
		}
		return target.getViewVector(1.0f).dot(toBall.normalize()) > 0.0;
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
