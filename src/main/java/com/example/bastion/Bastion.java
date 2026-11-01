package com.example.bastion;

import com.example.MobNames;
import com.example.entity.BastionEntity;
import com.example.siege.SiegeNight;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A rare common zombie or skeleton named Bastion.
 * The game does not add it: it is the same mob, only very tenacious and it is not moved by the blow.
 * Armor, enchantments, weapons and (in zombies) breaking blocks remain as usual.
 */
public final class Bastion {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — new Bastions do not appear, the team will not spam either. Already alive remain as they are. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that an ordinary zombie or skeleton will become a Bastion when it appears.
	 * 0.002 = 0.2%, like the supervisor and the captain. Throws separately for zombies and for the skeleton.
	 * The team skip this roll. Spauner throws. Summoned backup and /summon — no.
	 * The number is taken the same as that of other rare mobs from the 60th day: enter your percentage here and enter it.
	 */
	public static final float SPAWN_CHANCE = 0.002f;

	/** Name overhead. In English, like the rest of the name mobs. */
	public static final String NAME = "Bastion";

	/**
	 * Health. 1000 = 500 hearts. An ordinary zombie and skeleton accidentally have 20–40,
	 * this number is put after that throw and is always the same.
	 */
	public static final float MAX_HEALTH = 1000.0f;

	/**
	 * Experience for killing. 200 = two hundred. Always so much, armor and weapons from above do not add.
	 * The total exp multiplier (now ×2) does not increase this number.
	 * 0 = no experience. A normal zombie without this gives about 10.
	 */
	public static final int XP_REWARD = 200;

	/**
	 * How far the blow moves. 1.0 = does not move at all.
	 * 0.0 = as normal mob. Above 1, the game is still considered a complete rejection.
	 * The reclining arrow and explosion do not push either: the game considers them to be the same shift.
	 */
	public static final double KNOCKBACK_RESISTANCE = 1.0;

	// ========================================================================

	private Bastion() {
	}

	public static boolean isBastion(Mob mob) {
		return mob instanceof BastionEntity data && data.evolvingthreats$isBastion();
	}

	/** How much experience to give. -1 = this is not a Bastion, let the game decide. */
	public static int experienceReward(Mob mob) {
		if (!isBastion(mob)) {
			return -1;
		}
		return Math.max(0, XP_REWARD);
	}

	/**
	 * Throw a chance. true = it's a Bastion, he doesn't need a small skeleton.
	 * Health and no shift puts {@link #apply}, after the rest of the pack.
	 */
	public static boolean tryRoll(Mob mob, EntitySpawnReason reason) {
		if (isBastion(mob)) {
			return true;
		}
		if (!canRoll(mob, reason)) {
			return false;
		}
		if (SPAWN_CHANCE <= 0.0f || mob.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		if (mob instanceof BastionEntity data) {
			data.evolvingthreats$setBastion(true);
		}
		return true;
	}

	/**
	 * Team. Already appears as a Bastion, the chance does not rush.
	 * skeleton = false — zombie, true — skeleton.
	 * false — disabled or mob could not be created.
	 */
	public static boolean spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt, boolean skeleton) {
		if (!ENABLED) {
			return false;
		}
		Mob mob = skeleton
				? EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND)
				: EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (mob == null) {
			return false;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		mob.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (mob instanceof BastionEntity data) {
			data.evolvingthreats$setBastion(true);
		}
		mob.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(mob.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		if (lookAt != null) {
			mob.setTarget(lookAt);
		}
		level.addFreshEntity(mob);
		return true;
	}

	/** Health, name, blow does not move. The potion of speed, if it was already hanging, is removed. */
	public static void apply(Mob mob) {
		if (!isBastion(mob)) {
			return;
		}
		mob.setCustomName(Component.literal(NAME).withStyle(Style.EMPTY.withItalic(false)));
		MobNames.showOnlyWhenLookedAt(mob);
		var maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(MAX_HEALTH);
			mob.setHealth(MAX_HEALTH);
		}
		var knockback = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null) {
			knockback.setBaseValue(KNOCKBACK_RESISTANCE);
		}
		mob.removeEffect(MobEffects.SPEED);
	}

	/**
	 * After downloading the world. Health does not affect: how much was removed, so much was removed.
	 * Again prohibits the shift and hides the name until the mob is hovered over.
	 */
	public static void refresh(Mob mob) {
		if (!isBastion(mob)) {
			return;
		}
		MobNames.showOnlyWhenLookedAt(mob);
		var knockback = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null && knockback.getBaseValue() < KNOCKBACK_RESISTANCE) {
			knockback.setBaseValue(KNOCKBACK_RESISTANCE);
		}
		mob.removeEffect(MobEffects.SPEED);
	}

	private static boolean canRoll(Mob mob, EntitySpawnReason reason) {
		if (!ENABLED || !(mob instanceof BastionEntity) || !(mob.level() instanceof ServerLevel)) {
			return false;
		}
		if (!(mob instanceof Zombie) && !(mob instanceof Skeleton)) {
			return false;
		}
		if (mob.getType() != EntityTypes.ZOMBIE && mob.getType() != EntityTypes.SKELETON) {
			return false;
		}
		// The cub is faster than the adult. The bastion walks with the normal step of an adult.
		if (mob.isBaby()) {
			return false;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		// On the night of the siege, nominal rare mobs do not appear.
		if (SiegeNight.isWindowOpen(mob.level())) {
			return false;
		}
		// From what day do the Bastions appear at all. The odds remain unchanged from day to day.
		return ThreatScale.reached(mob.level(), ThreatTable.BASTION_DAY);
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
	}
}
