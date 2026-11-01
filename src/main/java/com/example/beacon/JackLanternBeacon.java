package com.example.beacon;

import com.example.threat.ThreatScale;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieOverseerEntity;
import com.example.light.LightSeeking;
import com.example.spawnerhead.SpawnerHeadMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * A rare zombie or skeleton with {@link Items#JACK_O_LANTERN} on its head is a beacon.
 * Zombie: does not look for/break the light; every few tics accelerates the zombies around to the player he sees.
 * Skeleton: One fire arrow per cooldown (target {@link com.example.entity.ai.JackLanternFireShotGoal}).
 */
public final class JackLanternBeacon {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. false — the pumpkin-lantern does not appear on the head. */
	public static final boolean ENABLED = true;

	/**
	 * The chance (0.0–1.0) for a spawn to become a carrier on the 100th day (GROWING: at the beginning of the game, 10 times less,
	 * see ThreatTable.DANGEROUS_START_RATIO). 0.01 = 1%. 0.05 = again every twentieth.
	 * Throw after the spavner (and the distributor at the skeleton): one head.
	 */
	public static final float CARRIER_SPAWN_CHANCE = 0.01f;

	/** Chance of a jack o'lantern drop from the head at death. */
	public static final float JACK_HEAD_DROP_CHANCE = 0.085f;

	// --- Zombie Beacon ---

	/**
	 * Radius (blocks) around the bearer: the zombies inside receive acceleration if the beacon sees the player.
	 * 20 ≈ as "agro" around the beacon torch.
	 */
	public static final double BEACON_RADIUS = 20.0;

	/** Every N ticks (20 = 1 sec), the beacon “pulses” the effect across the flock. */
	public static final int BEACON_PULSE_INTERVAL_TICKS = 20;

	/**
	 * Speed effect to allies: 0 = Speed I, 1 = Speed II.
	 * Duration must be ≥ pulse interval, otherwise the effect will flash.
	 */
	public static final int ALLY_SPEED_AMPLIFIER = 0;

	/** How many ticks does Speed hold for an ally after a pulse (40 = 2 sec). */
	public static final int ALLY_SPEED_DURATION_TICKS = 40;

	/**
	 * If the zombie in the radius does not have a goal, give the goal to the same player that the beacon sees.
	 * false — acceleration only, without a new agro.
	 */
	public static final boolean SHARE_TARGET_WITH_ALLIES = true;

	// --- Skeleton beacon (fire arrow) ---

	/** Minimum pause between fire shots (sec). */
	public static final int FIRE_SHOT_COOLDOWN_MIN_SECONDS = 8;

	/** Maximum pause between fire shots (sec). */
	public static final int FIRE_SHOT_COOLDOWN_MAX_SECONDS = 14;

	/** The speed of the fire arrow. */
	public static final float FIRE_ARROW_VELOCITY = 1.25f;

	/** Spread (less than that of the distributor — one accurate "hot" shot). */
	public static final float FIRE_ARROW_INACCURACY = 6.0f;

	/** Seconds of arrow burning after hitting. */
	public static final int FIRE_ARROW_FIRE_SECONDS = 5;

	/** Minimum distance to the target for the shot (blocks). */
	public static final double FIRE_SHOT_MIN_DISTANCE = 4.0;

	/** The maximum distance to the target for the shot (blocks). */
	public static final double FIRE_SHOT_MAX_DISTANCE = 22.0;

	// ========================================================================

	private JackLanternBeacon() {
	}

	public static boolean hasJackOnHead(Mob mob) {
		return mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.JACK_O_LANTERN);
	}

	/** Zombie carrier with active beacon role (for AI purposes). */
	public static boolean isBeaconZombie(Zombie zombie) {
		return ENABLED && hasJackOnHead(zombie) && isEligibleSpecial(zombie);
	}

	/** The beacon is not chasing torches — see {@link LightSeeking#isEligible}. */
	public static boolean skipsLightSeeking(Zombie zombie) {
		return isBeaconZombie(zombie);
	}

	public static boolean tryEquipOnSpawn(Mob mob) {
		if (!ENABLED || !isEligibleType(mob) || !isEligibleSpecial(mob)) {
			return false;
		}
		if (SpawnerHeadMob.hasSpawnerOnHead(mob)) {
			return false;
		}
		if (mob.getRandom().nextFloat() >= ThreatScale.dangerousChance(mob.level(), CARRIER_SPAWN_CHANCE)) {
			return false;
		}
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.JACK_O_LANTERN));
		mob.setDropChance(EquipmentSlot.HEAD, JACK_HEAD_DROP_CHANCE);
		return true;
	}

	public static int randomFireShotCooldownTicks(net.minecraft.util.RandomSource random) {
		int min = FIRE_SHOT_COOLDOWN_MIN_SECONDS * 20;
		int max = FIRE_SHOT_COOLDOWN_MAX_SECONDS * 20;
		if (max <= min) {
			return min;
		}
		return random.nextIntBetweenInclusive(min, max);
	}

	/** One “wave” of pack acceleration is called from {@link JackBeaconAuraGoal}. */
	public static void pulseAura(Zombie beacon, LivingEntity focus) {
		if (!ENABLED || !(beacon.level() instanceof ServerLevel level)) {
			return;
		}
		if (!(focus instanceof Player)) {
			return;
		}
		AABB area = beacon.getBoundingBox().inflate(BEACON_RADIUS);
		for (Zombie ally : level.getEntitiesOfClass(Zombie.class, area, z -> canReceiveBeaconBuff(z, beacon))) {
			ally.addEffect(new MobEffectInstance(
					MobEffects.SPEED,
					ALLY_SPEED_DURATION_TICKS,
					ALLY_SPEED_AMPLIFIER,
					false,
					false,
					true));
			if (SHARE_TARGET_WITH_ALLIES && ally.getTarget() == null) {
				ally.setTarget(focus);
			}
		}
	}

	private static boolean canReceiveBeaconBuff(Zombie ally, Zombie beacon) {
		if (ally == beacon || !ally.isAlive()) {
			return false;
		}
		if (ally instanceof Drowned || ally instanceof ZombifiedPiglin) {
			return false;
		}
		if (ally instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		if (ally instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return false;
		}
		return true;
	}

	private static boolean isEligibleType(Mob mob) {
		if (mob instanceof Drowned || mob instanceof ZombifiedPiglin) {
			return false;
		}
		if (mob instanceof Zombie) {
			return true;
		}
		return mob instanceof Skeleton || mob instanceof Stray;
	}

	private static boolean isEligibleSpecial(Mob mob) {
		if (mob instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		return !(mob instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant());
	}
}
