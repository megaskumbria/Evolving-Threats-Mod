package com.example.blaze;

import com.example.entity.AlchemistBlazeEntity;
import net.minecraft.server.level.ServerLevel;
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
 * Редкий блейз. В залпе из трёх шаров первый (когда перезарядка прошла) — алхимический.
 * С виду это обычный огненный шар: тот же огонь, урон и поджог.
 * Дополнительно, если огненная стойкость длиннее порога, она укорачивается до этого порога.
 * Уровень зелья остаётся. Между особыми выстрелами блейз стреляет такими же обычными шарами.
 */
public final class BlazeAlchemist {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — алхимики не появляются, и шар не срезает стойкость. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный блейз при появлении окажется алхимиком. 0.04 = 4%.
	 * Спаунер крепости тоже бросает этот шанс. Команда спавна шанс не использует.
	 */
	public static final float SPAWN_CHANCE = 0.04f;

	/**
	 * Сколько секунд огненной стойкости остаётся после попадания.
	 * 20 = с 8 минут станет 20 секунд. Если стойкости уже меньше или ровно столько — не трогает
	 * и заново не обновляет. Бесконечная стойкость (команда) тоже срезается до этого числа.
	 */
	public static final int FIRE_RESISTANCE_LEFT_SECONDS = 20;

	/**
	 * Пауза между шарами, которые срезают стойкость, секунды.
	 * 20 = следующий такой шар не раньше чем через 20 секунд.
	 * Залп блейза сам по себе примерно раз в 9 секунд, так что срез выходит около каждого третьего залпа.
	 * Меньше 1 — стойкость срежут и соседние шары того же залпа (между ними 0.3 сек).
	 * 0 = каждый шар этого блейза срезает стойкость.
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

	/** Конец серверного тика блейза. Убавляет паузу до следующего шара со срезом стойкости. */
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
	 * Вызвать, когда блейз только что создал маленький огненный шар.
	 * true — этот шар ещё и срежет стойкость, пауза начинается заново.
	 * Вид шара не меняется: это тот же огненный заряд, что у обычного блейза.
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
	 * Попадание алхимического шара. Урон и поджог делает сам огненный шар.
	 * Здесь только срез стойкости. Щит спереди срез ловит.
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

	/** Вызвать из finalizeSpawn. Командный спавн ставит флаг заранее. */
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

	/** Команда: алхимик перед игроком. null — механика выключена. */
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
		target.forceAddEffect(new MobEffectInstance(
				MobEffects.FIRE_RESISTANCE,
				leftTicks,
				current.getAmplifier(),
				current.isAmbient(),
				current.isVisible(),
				current.showIcon()), source);
	}

	/** Щит смотрит на шар — попадание не срезает стойкость. */
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
