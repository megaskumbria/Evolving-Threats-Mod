package com.example.zombie;

import com.example.MobEnchantments;
import com.example.MobNames;
import com.example.RandomArmor;
import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieExcaliburEntity;
import com.example.entity.ZombieMjolnirEntity;
import com.example.entity.ZombieOverseerEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Редкий именной зомби Mjölnir.
 * В руке булава (молот) с Отдачей 20. У стола зачарований потолок II.
 * Молот почти никогда не выпадает. Бирка с именем падает всегда, опыта больше, чем у обычного зомби.
 *
 * <p>Отдача 20 — обычный удар, только отброс гораздо сильнее. Каждый уровень добавляет 1 к силе.
 * Отдача II — это 2, Отдача 20 — это 20, в десять раз сильнее: игрока может унести далеко.
 */
public final class ZombieMjolnir {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — такие зомби не появляются, команда тоже не спавнит. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный зомби окажется Mjölnir. 0.002 = 0.2%, один из пятисот.
	 * ФИКС: с первого дня, от шкалы угрозы и луны не зависит.
	 * У Excalibur 0.01 = 1%, здесь нарочно реже. 0 = только команда.
	 * Утопленник, кадавр, зомби-житель, детёныш, надсмотрщик, рейд и Excalibur этот шанс не бросают.
	 * Команда шанс не использует.
	 */
	public static final float SPAWN_CHANCE = 0.002f;

	/**
	 * Уровень Отдачи на молоте. 20. У стола зачарований потолок II (2).
	 * Каждый уровень добавляет 1 к силе отброса. 2 = обычная Отдача II. 20 = в десять раз сильнее.
	 * 1 = Отдача I. 0 = молот без отдачи, только удар самой булавы.
	 */
	public static final int KNOCKBACK_LEVEL = 20;

	/**
	 * Шанс (0.0–1.0), что при смерти упадёт молот вместе с Отдачей. 0.005 = 0.5%, один из двухсот.
	 * Это стартовое «очень редко»: у капитана скелетов меч падает в 15%, здесь нарочно реже.
	 * 0 = молот не падает никогда. 1 = падает всегда. Добыча на этот бросок не влияет.
	 */
	public static final float HAMMER_DROP_CHANCE = 0.005f;

	/**
	 * Опыт за убийство. 20 — в четыре раза больше обычного зомби (у того 5), как у Excalibur.
	 * Падает, только если зомби убил игрок: так устроена сама игра.
	 * 0 = опыта нет. Это число вместо обычного, а не сверху.
	 */
	public static final int XP_REWARD = 20;

	/**
	 * Сколько бирок падает всегда. 1 = одна бирка с именем Mjölnir.
	 * 0 = бирку не ронять. Молот от этого числа не зависит.
	 */
	public static final int NAME_TAG_COUNT = 1;

	/** Имя над головой и текст на бирке. */
	public static final String DISPLAY_NAME = "Mjölnir";

	// ========================================================================

	private ZombieMjolnir() {
	}

	public static boolean isMjolnir(Zombie zombie) {
		return ENABLED
				&& zombie instanceof ZombieMjolnirEntity data
				&& data.evolvingthreats$isMjolnir();
	}

	/**
	 * Конец появления зомби. true = это Mjölnir: случайное оружие, щит, тотем
	 * и голова ему не выдаются. Броня — как у обычного зомби.
	 * Зелья при появлении уже могли повеситься.
	 */
	public static boolean tryClaim(Zombie zombie, EntitySpawnReason reason) {
		if (!canBeMjolnir(zombie)) {
			return false;
		}
		ZombieMjolnirEntity data = (ZombieMjolnirEntity) zombie;
		if (data.evolvingthreats$isMjolnir()) {
			equip(zombie);
			return true;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		if (zombie.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setMjolnir(true);
		equip(zombie);
		return true;
	}

	/** Сколько опыта дать. -1 = это не Mjölnir, пусть решает игра. */
	public static int experienceReward(Zombie zombie) {
		if (!isMjolnir(zombie)) {
			return -1;
		}
		return Math.max(0, XP_REWARD);
	}

	/** Бирка всегда. Молот — отдельным редким броском, с теми же чарами. */
	public static void dropBonus(Zombie zombie, ServerLevel level) {
		if (!isMjolnir(zombie)) {
			return;
		}
		if (NAME_TAG_COUNT > 0) {
			ItemStack tag = new ItemStack(Items.NAME_TAG, NAME_TAG_COUNT);
			tag.set(DataComponents.CUSTOM_NAME, name());
			zombie.spawnAtLocation(level, tag);
		}
		if (HAMMER_DROP_CHANCE <= 0.0f || zombie.getRandom().nextFloat() >= HAMMER_DROP_CHANCE) {
			return;
		}
		ItemStack hammer = zombie.getMainHandItem();
		if (!hammer.isEmpty()) {
			zombie.spawnAtLocation(level, hammer.copy());
		}
	}

	/** Команда: Mjölnir перед игроком. null — механика выключена. */
	public static @Nullable Zombie spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (zombie == null) {
			throw new IllegalStateException("Failed to create Mjolnir");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		zombie.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (zombie instanceof ZombieMjolnirEntity data) {
			data.evolvingthreats$setMjolnir(true);
		}
		zombie.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(zombie.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		if (lookAt != null) {
			zombie.setTarget(lookAt);
		}
		level.addFreshEntity(zombie);
		return zombie;
	}

	private static boolean canBeMjolnir(Zombie zombie) {
		if (!ENABLED || !(zombie instanceof ZombieMjolnirEntity)) {
			return false;
		}
		if (zombie.getType() != EntityTypes.ZOMBIE || zombie.isBaby()) {
			return false;
		}
		if (zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		if (zombie instanceof ZombieExcaliburEntity excalibur && excalibur.evolvingthreats$isExcalibur()) {
			return false;
		}
		return !(zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant());
	}

	private static void equip(Zombie zombie) {
		zombie.setCustomName(name());
		MobNames.showOnlyWhenLookedAt(zombie);
		// Имя уже не даёт ему исчезнуть. Чужую вещь не подбирает, иначе молот сменится.
		zombie.setCanPickUpLoot(false);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, hammer(zombie));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		// 0 = игра сама молот не роняет, даже с Добычей. Редкий бросок делает dropBonus.
		zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		zombie.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		// Та же броня, что у обычного зомби. Руки этот бросок не трогает.
		RandomArmor.rollForZombie(zombie);
		MobEnchantments.rollArmorForZombie(zombie);
	}

	private static ItemStack hammer(Zombie zombie) {
		ItemStack stack = new ItemStack(Items.MACE);
		if (!(zombie.level() instanceof ServerLevel level) || KNOCKBACK_LEVEL <= 0) {
			return stack;
		}
		Holder<Enchantment> knockback = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.KNOCKBACK);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(knockback, KNOCKBACK_LEVEL));
		return stack;
	}

	private static Component name() {
		return Component.literal(DISPLAY_NAME).withStyle(Style.EMPTY.withItalic(false));
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
