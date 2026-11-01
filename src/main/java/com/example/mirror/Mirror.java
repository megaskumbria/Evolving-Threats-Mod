package com.example.mirror;

import com.example.MobNames;
import com.example.entity.MirrorEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 A rare Mirror mob. The game does not add it: it is a regular mob from the list below.
 The player's point-blank shot returns to him: the same number that went into the mob before his armor.
 Next, this number is cut by the player's armor. Arrow, explosion, fire and thorns do not return.
 */
public final class Mirror {

	// ===================== SETTINGS (change these numbers) =====================

	/*False - new Mirror does not appear, the team does not spawn. The living remain as they are. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0-1.0) when a suitable mob appears. 0.003 = 0.3%.
	 FIXED: From day one, the scale and moon do not change.
	 * Dropped separately for each species from the list. The team doesn't give up that chance.
	 */
	public static final float SPAWN_CHANCE = 0.003f;

	/** Name overhead. In English, like the other mobs. */
	public static final String NAME = "Mirror";

	// ========================================================================

	/*So that the return of the blow does not cause a second return in the same blow. */
	private static boolean reflecting;

	private Mirror() {
	}

	public static boolean isMirror(Mob mob) {
		return mob instanceof MirrorEntity data && data.evolvingthreats$isMirror();
	}

	/**
	 The appearance of ifrite, witch, cryper, piglin, enderman and scales.
	 They do not have their appearance, the chance is thrown in the general code of the mob.
	 Zombies, skeleton, spider and brute are not included: otherwise the chance would be thrown twice.
	 */
	public static void tryRollSharedSpawn(Mob mob, EntitySpawnReason reason) {
		if (!isSharedSpawnType(mob)) {
			return;
		}
		tryRoll(mob, reason);
	}

	/**
	 * Once upon appearance. If the mob already has its own name (Bastion, Basilisk and others),
	 This opportunity is not thrown: the name is not erased.
	 */
	public static void tryRoll(Mob mob, EntitySpawnReason reason) {
		if (!ENABLED || !(mob instanceof MirrorEntity data) || !(mob.level() instanceof ServerLevel)) {
			return;
		}
		if (data.evolvingthreats$isMirror()) {
			apply(mob);
			return;
		}
		if (!isListedType(mob) || mob.getCustomName() != null) {
			return;
		}
		if (SPAWN_CHANCE <= 0.0f || mob.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		data.evolvingthreats$setMirror(true);
		apply(mob);
	}

	/**
	 The strike has already reached the Mirror. Return the player the same amount of damage that the game counted before the Mob armor.
	 The sharpness, strength and critical impact are already there: they were considered by the player’s attack.
	 */
	public static void onHurt(LivingEntity mob, ServerLevel level, DamageSource source, float damage, boolean landed) {
		if (!landed || reflecting || damage <= 0.0f || !(mob instanceof Mob mirror) || !isMirror(mirror)) {
			return;
		}
		if (!(source.getEntity() instanceof Player player) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		// The player is point-blank. Arrow, trident and explosion come as a separate entity.
		if (source.getDirectEntity() != player) {
			return;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE)
				|| source.is(DamageTypeTags.IS_EXPLOSION)
				|| source.is(DamageTypeTags.IS_FIRE)
				|| source.is(DamageTypes.THORNS)) {
			return;
		}
		reflecting = true;
		try {
			// A blow like this mob: the death message will have the name Mirror.
			// The number is the same, the armor and the player's defense cut it themselves.
			player.hurtServer(level, level.damageSources().mobAttack(mirror), damage);
		} finally {
			reflecting = false;
		}
	}

	/**
	 * Team. kind: zombie, skeleton, wither, blaze, spider, piglin, brute, enderman, silverfish, creeper, witch.
	 * The empty line is zombies. False is turned off or not.
	 */
	public static boolean spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt, String kind) {
		if (!ENABLED) {
			return false;
		}
		EntityType<? extends Mob> type = typeFor(kind);
		if (type == null) {
			return false;
		}
		Mob mob = type.create(level, EntitySpawnReason.COMMAND);
		if (mob == null) {
			return false;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		mob.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (mob instanceof MirrorEntity data) {
			data.evolvingthreats$setMirror(true);
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

	/*After download: the name is hidden again until the mob is hovered. Health doesn't matter. */
	public static void refresh(Mob mob) {
		if (!isMirror(mob)) {
			return;
		}
		MobNames.showOnlyWhenLookedAt(mob);
	}

	private static void apply(Mob mob) {
		mob.setCustomName(Component.literal(NAME).withStyle(Style.EMPTY.withItalic(false)));
		MobNames.showOnlyWhenLookedAt(mob);
	}

	private static boolean isSharedSpawnType(Mob mob) {
		EntityType<?> type = mob.getType();
		return type == EntityTypes.BLAZE
				|| type == EntityTypes.WITCH
				|| type == EntityTypes.CREEPER
				|| type == EntityTypes.PIGLIN
				|| type == EntityTypes.ENDERMAN
				|| type == EntityTypes.SILVERFISH;
	}

	private static boolean isListedType(Mob mob) {
		EntityType<?> type = mob.getType();
		return type == EntityTypes.ZOMBIE
				|| type == EntityTypes.SKELETON
				|| type == EntityTypes.WITHER_SKELETON
				|| type == EntityTypes.BLAZE
				|| type == EntityTypes.SPIDER
				|| type == EntityTypes.PIGLIN
				|| type == EntityTypes.PIGLIN_BRUTE
				|| type == EntityTypes.ENDERMAN
				|| type == EntityTypes.SILVERFISH
				|| type == EntityTypes.CREEPER
				|| type == EntityTypes.WITCH;
	}

	private static @Nullable EntityType<? extends Mob> typeFor(String kind) {
		return switch (kind == null ? "" : kind) {
			case "", "zombie" -> EntityTypes.ZOMBIE;
			case "skeleton" -> EntityTypes.SKELETON;
			case "wither" -> EntityTypes.WITHER_SKELETON;
			case "blaze" -> EntityTypes.BLAZE;
			case "spider" -> EntityTypes.SPIDER;
			case "piglin" -> EntityTypes.PIGLIN;
			case "brute" -> EntityTypes.PIGLIN_BRUTE;
			case "enderman" -> EntityTypes.ENDERMAN;
			case "silverfish" -> EntityTypes.SILVERFISH;
			case "creeper" -> EntityTypes.CREEPER;
			case "witch" -> EntityTypes.WITCH;
			default -> null;
		};
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
	}
}
