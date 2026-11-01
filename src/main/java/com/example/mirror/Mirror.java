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
 * Редкий моб Mirror. Игра его не добавляет: это обычный моб из списка ниже.
 * Удар игрока в упор возвращается ему же: то же число, что ушло в моба до его брони.
 * Дальше это число режет уже броня игрока. Стрела, взрыв, огонь и шипы не возвращаются.
 */
public final class Mirror {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — новые Mirror не появляются, команда тоже не спавнит. Уже живые остаются как есть. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0) при появлении подходящего моба. 0.003 = 0,3%.
	 * FIXED: с первого дня, от шкалы и луны не меняется.
	 * Бросается отдельно у каждого вида из списка. Команда этот шанс не бросает.
	 */
	public static final float SPAWN_CHANCE = 0.003f;

	/** Имя над головой. По-английски, как у остальных именных мобов. */
	public static final String NAME = "Mirror";

	// ========================================================================

	/** Чтобы возврат удара не вызвал второй возврат в том же ударе. */
	private static boolean reflecting;

	private Mirror() {
	}

	public static boolean isMirror(Mob mob) {
		return mob instanceof MirrorEntity data && data.evolvingthreats$isMirror();
	}

	/**
	 * Появление ифрита, ведьмы, крипера, пиглина, эндермена и чешуйницы.
	 * У них нет своего появления, шанс бросается в общем коде моба.
	 * Зомби, скелет, паук и брут сюда не входят: иначе шанс бросился бы дважды.
	 */
	public static void tryRollSharedSpawn(Mob mob, EntitySpawnReason reason) {
		if (!isSharedSpawnType(mob)) {
			return;
		}
		tryRoll(mob, reason);
	}

	/**
	 * Один раз при появлении. Если у моба уже есть своё имя (Бастион, Василиск и остальные),
	 * этот шанс не бросается: имя не затираем.
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
	 * Удар уже дошёл до Mirror. Вернуть игроку то же число урона, что игра насчитала до брони моба.
	 * Острота, сила и критический удар в этом числе уже есть: их посчитала атака игрока.
	 */
	public static void onHurt(LivingEntity mob, ServerLevel level, DamageSource source, float damage, boolean landed) {
		if (!landed || reflecting || damage <= 0.0f || !(mob instanceof Mob mirror) || !isMirror(mirror)) {
			return;
		}
		if (!(source.getEntity() instanceof Player player) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		// В упор бьёт сам игрок. Стрела, трезубец и взрыв приходят отдельной сущностью.
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
			// Удар как от этого моба: в сообщении о смерти будет имя Mirror.
			// Число то же, броня и Защита игрока режут его сами.
			player.hurtServer(level, level.damageSources().mobAttack(mirror), damage);
		} finally {
			reflecting = false;
		}
	}

	/**
	 * Команда. kind: zombie, skeleton, wither, blaze, spider, piglin, brute, enderman, silverfish, creeper, witch.
	 * Пустая строка — зомби. false — выключен или такого вида нет.
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

	/** После загрузки: имя снова прячется, пока на моба не наведутся. Здоровье не трогает. */
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
