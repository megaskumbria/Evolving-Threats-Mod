package com.example.skeleton;

import com.example.entity.TacticianSkeletonEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Обычный скелет и странник иногда получают лук с Откидыванием V или VI.
 * В ванили максимум II, у капитана отдельно IV. Этот лук бьёт ещё дальше.
 * Такой лук игроку не падает: ни с шансом, ни с Добычей.
 * Откидывание I и II с обычных чар по-прежнему может выпасть.
 */
public final class SkeletonPunchBow {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/**
	 * Включить выдачу лука. false — новые скелеты V и VI не получают.
	 * Уже выданный лук всё равно не падает: проверка при смерти от этой галки не зависит.
	 */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что у скелета или странника с луком будет Откидывание V или VI.
	 * 0.12 = 12%. Бросается после обычных чар. Капитан, его помощники и «700 IQ» этот шанс не бросают.
	 * 1.0 = такой лук у каждого, у кого лук остался. 0 = не выдавать.
	 */
	public static final float CHANCE = 0.12f;

	/** Откидывание V. Сильнее капитанского IV: игрок улетает ещё дальше. */
	public static final int PUNCH_LEVEL_V = 5;

	/** Откидывание VI. Реже, чем V, и бьёт ещё сильнее. */
	public static final int PUNCH_LEVEL_VI = 6;

	/**
	 * Среди луков с сильным откидыванием: насколько часто V и насколько часто VI.
	 * 3 и 1 = из таких луков 75% получают V, 25% получают VI.
	 * Вес 0 у VI = всегда только V.
	 */
	public static final int LEVEL_V_WEIGHT = 3;

	/** См. LEVEL_V_WEIGHT. */
	public static final int LEVEL_VI_WEIGHT = 1;

	/**
	 * Лук с Откидыванием этого уровня и выше никогда не падает.
	 * 5 = не падают V и VI. Обычные I–II (максимум ванили) падают как раньше.
	 * Поставь 1, если не должен падать вообще никакой лук с Откидыванием.
	 */
	public static final int MIN_LEVEL_THAT_NEVER_DROPS = 5;

	// ========================================================================

	private static final EquipmentSlot[] HANDS = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND };

	private SkeletonPunchBow() {
	}

	/**
	 * Команда. Скелет появляется в указанной точке уже с этим луком, шанс не бросается.
	 * level — {@link #PUNCH_LEVEL_V} или {@link #PUNCH_LEVEL_VI}.
	 * false — скелета не удалось создать.
	 */
	public static boolean spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt, int punchLevel) {
		AbstractSkeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			return false;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		skeleton.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(skeleton.blockPosition()),
				EntitySpawnReason.COMMAND,
				null);
		if (skeleton instanceof TacticianSkeletonEntity tactician && tactician.evolvingthreats$isTactician()) {
			tactician.evolvingthreats$setTactician(false);
			tactician.evolvingthreats$setBackItem(ItemStack.EMPTY);
			tactician.evolvingthreats$setMarkedPlayer(null);
			tactician.evolvingthreats$setMeleeUntil(0L);
		}
		giveBow(skeleton, punchLevel);
		if (lookAt != null) {
			skeleton.setTarget(lookAt);
		}
		level.addFreshEntity(skeleton);
		return true;
	}

	/** Кладёт лук с заданным Откидыванием в основную руку. Такой лук не падает. */
	private static void giveBow(AbstractSkeleton skeleton, int punchLevel) {
		int level = punchLevel >= PUNCH_LEVEL_VI ? PUNCH_LEVEL_VI : PUNCH_LEVEL_V;
		ItemStack bow = new ItemStack(Items.BOW);
		var holder = skeleton.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PUNCH);
		EnchantmentHelper.updateEnchantments(bow, mutable -> mutable.set(holder, level));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
		skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		skeleton.reassessWeaponGoal();
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
	}

	/**
	 * Конец набора скелета. Если в руке лук и шанс выпал — дописывает Откидывание V или VI.
	 * Остальные чары лука (Сила, Горящая стрела) остаются.
	 */
	public static void tryApply(AbstractSkeleton skeleton) {
		if (!ENABLED || CHANCE <= 0.0f) {
			return;
		}
		EquipmentSlot slot = bowSlot(skeleton);
		if (slot == null || skeleton.getRandom().nextFloat() >= CHANCE) {
			return;
		}
		int level = rollLevel(skeleton.getRandom());
		if (level <= 0) {
			return;
		}
		ItemStack bow = skeleton.getItemBySlot(slot);
		var holder = skeleton.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PUNCH);
		EnchantmentHelper.updateEnchantments(bow, mutable -> mutable.set(holder, level));
		skeleton.setItemSlot(slot, bow);
	}

	/**
	 * Перед тем как моб уронит вещи. Лук мог переехать из руки в руку во время боя,
	 * поэтому смотрим обе руки в момент смерти, а не слот при спавне.
	 */
	public static void preventDrop(Mob mob) {
		var registry = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		var punch = registry.getOrThrow(Enchantments.PUNCH);
		int min = Math.max(1, MIN_LEVEL_THAT_NEVER_DROPS);
		for (EquipmentSlot slot : HANDS) {
			ItemStack stack = mob.getItemBySlot(slot);
			if (!stack.is(Items.BOW)) {
				continue;
			}
			if (EnchantmentHelper.getItemEnchantmentLevel(punch, stack) >= min) {
				mob.setDropChance(slot, 0.0f);
			}
		}
	}

	private static int rollLevel(RandomSource random) {
		int v = Math.max(0, LEVEL_V_WEIGHT);
		int vi = Math.max(0, LEVEL_VI_WEIGHT);
		int total = v + vi;
		if (total <= 0) {
			return PUNCH_LEVEL_V;
		}
		if (random.nextInt(total) < v) {
			return PUNCH_LEVEL_V;
		}
		return PUNCH_LEVEL_VI;
	}

	private static @Nullable EquipmentSlot bowSlot(AbstractSkeleton skeleton) {
		if (skeleton.getMainHandItem().is(Items.BOW)) {
			return EquipmentSlot.MAINHAND;
		}
		if (skeleton.getOffhandItem().is(Items.BOW)) {
			return EquipmentSlot.OFFHAND;
		}
		return null;
	}
}
