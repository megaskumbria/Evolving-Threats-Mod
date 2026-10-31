package com.example.skeleton;

import com.example.RandomWeapons;
import com.example.entity.SniperSkeletonEntity;
import com.example.mixin.RangedBowAttackGoalAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Скелет-снайпер. Стреляет с дальней дистанции, лук из рук не убирается.
 * Во второй руке обычное оружие моба. Броня и чары — как у обычного скелета.
 * Быстрый лук может выпасть отдельно: тогда он натягивает чаще, но дальность остаётся снайперской.
 */
public final class SniperSkeleton {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — снайперы не появляются, команда тоже не спавнит. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный скелет или странник будет снайпером.
	 * 0.002 = 0,2%, один из пятисот.
	 * ФИКС: с первого дня, от шкалы угрозы и луны не растёт.
	 * 0 = только команда.
	 */
	public static final float SPAWN_CHANCE = 0.002f;

	/**
	 * С какого расстояния стреляет, в блоках. У обычного скелета 15, у дальнего лука 30.
	 * 128 = восемь чанков. Если в мире мобы засыпают ближе, он не выстрелит, пока не подойдёшь.
	 */
	public static final float ATTACK_RANGE_BLOCKS = 128.0f;

	/**
	 * Как далеко замечает игрока, в блоках. Должно быть больше дальности лука,
	 * иначе он не видит цель, до которой мог бы дострелить. 140 = чуть дальше 128.
	 */
	public static final double FOLLOW_RANGE = 140.0;

	/**
	 * Скорость стрелы. У обычного скелета 1.6, у дальнего лука 2.4.
	 * 10 = долетает до 128 блоков примерно за секунду и не падает в землю по пути.
	 * Если уменьшить, подними {@link #ARROW_ARC}, иначе стрела ляжет раньше игрока.
	 */
	public static final float ARROW_SPEED = 10.0f;

	/**
	 * Насколько задирать прицел. У обычного скелета 0.2, и это число умножается на расстояние.
	 * 0.04 подогнано под скорость 10 и выстрел до 128 блоков: попадает в тело и вблизи, и издалека.
	 * Выше — на дальней дистанции стрела пройдёт над головой. Ниже — упадёт перед игроком.
	 */
	public static final double ARROW_ARC = 0.04;

	/**
	 * Разброс стрелы. У скелета на сложной сложности около 2: на 128 блоках это промах в несколько блоков.
	 * 0.15 = попадает в тело на дальней дистанции. 0 = всегда в точку прицела.
	 * 2 = как сложная сложность, на этой дальности почти всегда мимо.
	 */
	public static final float INACCURACY = 0.15f;

	// ========================================================================

	private SniperSkeleton() {
	}

	public static boolean isSniper(AbstractSkeleton skeleton) {
		return ENABLED
				&& skeleton instanceof SniperSkeletonEntity data
				&& data.evolvingthreats$isSniper();
	}

	/**
	 * true — этот скелет снайпер. Дальше ему не выдаём два меча, тотем и вредную стрелу:
	 * лук должен остаться, вторая рука занята обычным оружием.
	 */
	public static boolean tryRoll(AbstractSkeleton skeleton) {
		if (!ENABLED || !(skeleton instanceof SniperSkeletonEntity data)) {
			return false;
		}
		if (!(skeleton.level() instanceof ServerLevel)) {
			return false;
		}
		// Команда ставит метку до появления. Шанс тогда не бросаем.
		if (data.evolvingthreats$isSniper()) {
			return true;
		}
		if (SPAWN_CHANCE <= 0.0f) {
			return false;
		}
		if (skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setSniper(true);
		return true;
	}

	/** Лук в основную руку, случайное оружие во вторую. Броню и чары вешает общий код скелета. */
	public static void equip(AbstractSkeleton skeleton) {
		if (!isSniper(skeleton)) {
			return;
		}
		if (!skeleton.getMainHandItem().is(Items.BOW)) {
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
		RandomWeapons.equipDualWieldArcher(skeleton);
	}

	/**
	 * Дальность взгляда и выстрела. Звать после быстрого лука: тот ставит свои 30 блоков,
	 * здесь они снова становятся снайперскими. Паузу быстрого лука не трогаем.
	 */
	public static void apply(AbstractSkeleton skeleton, RangedBowAttackGoal<?> bowGoal) {
		if (!isSniper(skeleton)) {
			return;
		}
		var follow = skeleton.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(FOLLOW_RANGE);
		}
		float range = Math.max(1.0f, ATTACK_RANGE_BLOCKS);
		((RangedBowAttackGoalAccessor) bowGoal).evolvingthreats$setAttackRadiusSqr(range * range);
	}

	/**
	 * Команда: снайпер перед игроком, без случайного шанса.
	 * Броня, чары и оружие во второй руке вешаются тем же кодом, что при обычном спавне.
	 */
	public static void spawn(ServerLevel level, Vec3 pos, LivingEntity lookAt) {
		if (!ENABLED) {
			return;
		}
		AbstractSkeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			return;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (skeleton instanceof SniperSkeletonEntity data) {
			data.evolvingthreats$setSniper(true);
		}
		skeleton.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(skeleton.blockPosition()),
				EntitySpawnReason.COMMAND,
				null);
		if (lookAt != null) {
			skeleton.setTarget(lookAt);
		}
		level.addFreshEntity(skeleton);
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
