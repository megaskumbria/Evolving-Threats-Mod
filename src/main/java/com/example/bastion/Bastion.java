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
 * Редкий обычный зомби или скелет по имени Bastion.
 * Игра его не добавляет: это тот же моб, только очень живучий и его не сдвигает удар.
 * Броня, чары, оружие и (у зомби) ломание блоков остаются как у обычного.
 */
public final class Bastion {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — новые Бастионы не появляются, команда тоже не спавнит. Уже живые остаются как есть. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный зомби или скелет при появлении станет Бастионом.
	 * 0.002 = 0.2%, как у надсмотрщика и капитана. Бросается отдельно для зомби и для скелета.
	 * Команда этот шанс не бросает. Спаунер бросает. Призванная подмога и /summon — нет.
	 * Число взято таким же, как у других редких мобов с 60-го дня: свой процент сюда и впиши.
	 */
	public static final float SPAWN_CHANCE = 0.002f;

	/** Имя над головой. По-английски, как у остальных именных мобов. */
	public static final String NAME = "Bastion";

	/**
	 * Здоровье. 1000 = 500 сердец. У обычного зомби и скелета случайно 20–40,
	 * это число ставится после того броска и всегда одно и то же.
	 */
	public static final float MAX_HEALTH = 1000.0f;

	/**
	 * Насколько удар сдвигает с места. 1.0 = не сдвигает вообще.
	 * 0.0 = как обычный моб. Выше 1 игра всё равно считает полным отказом.
	 * Стрела с Откидыванием и взрыв тоже не толкают: игра считает их тем же сдвигом.
	 */
	public static final double KNOCKBACK_RESISTANCE = 1.0;

	// ========================================================================

	private Bastion() {
	}

	public static boolean isBastion(Mob mob) {
		return mob instanceof BastionEntity data && data.evolvingthreats$isBastion();
	}

	/**
	 * Бросить шанс. true = это Бастион, маленький скелет ему не нужен.
	 * Здоровье и отказ от сдвига ставит {@link #apply}, уже после остального набора.
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
	 * Команда. Появляется уже Бастионом, шанс не бросается.
	 * skeleton = false — зомби, true — скелет.
	 * false — выключен или моба не удалось создать.
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

	/** Здоровье, имя, удар не сдвигает. Зелье скорости, если уже висело, снимается. */
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
	 * После загрузки мира. Здоровье не трогает: сколько сняли, столько и снято.
	 * Снова запрещает сдвиг и прячет имя, пока на моба не наведутся.
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
		// Детёныш быстрее взрослого. Бастион ходит обычным шагом взрослого.
		if (mob.isBaby()) {
			return false;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		// В ночь осады именные редкие мобы не появляются.
		if (SiegeNight.isWindowOpen(mob.level())) {
			return false;
		}
		// С какого дня Бастионы вообще появляются. Шанс ниже от дня не меняется.
		return ThreatScale.reached(mob.level(), ThreatTable.BASTION_DAY);
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
	}
}
