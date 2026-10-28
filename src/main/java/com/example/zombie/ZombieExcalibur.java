package com.example.zombie;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieExcaliburEntity;
import com.example.entity.ZombieOverseerEntity;
import net.minecraft.util.Mth;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Редкий именной зомби Excalibur.
 * В руке деревянный, каменный, медный, железный или алмазный меч с Остротой 20.
 * Меч не выпадает. Вместо него падает бирка с этим именем и больше опыта, чем у обычного зомби.
 *
 * <p>Острота 20 — это обычный удар, только сильнее. Отдельного «сокрушительного» приёма нет.
 * Игра добавляет 1 урон за первый уровень и ещё 0.5 за каждый следующий: 20 = 10.5 сверх меча.
 * У голого игрока на обычной сложности это примерно 8–10 сердец (алмазный почти добивает).
 * На сложной любой такой меч убивает голого с одного удара. В броне удар слабее,
 * полного алмазного сета одним ударом не снимает.
 */
public final class ZombieExcalibur {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — такие зомби не появляются, команда тоже не спавнит. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный зомби окажется Excalibur. 0.01 = 1%.
	 * ФИКС: с первого дня, от шкалы угрозы и луны не зависит.
	 * Это стартовое число, его легко подкрутить. 0.002 = 0.2%, как у надсмотрщика. 0 = только команда.
	 * Утопленник, кадавр, зомби-житель, детёныш, надсмотрщик и рейд этот шанс не бросают.
	 * Команда шанс не использует.
	 */
	public static final float SPAWN_CHANCE = 0.01f;

	/**
	 * Уровень Остроты на мече. 20. У стола зачарований потолок V (5), здесь нарочно выше.
	 * Урон чар: 1 за I уровень и +0.5 за каждый следующий. 20 даёт 10.5 урона сверх меча.
	 * 5 = обычная Острота V (плюс 3 урона). 1 = Острота I.
	 */
	public static final int SHARPNESS_LEVEL = 20;

	/**
	 * Опыт за убийство. 20 — в четыре раза больше обычного зомби (у того 5).
	 * Падает, только если зомби убил игрок: так устроена сама игра.
	 * 0 = опыта нет. Это число вместо обычного, а не сверху.
	 */
	public static final int XP_REWARD = 20;

	/**
	 * Сколько бирок падает всегда. 1 = одна бирка с именем Excalibur.
	 * 0 = бирку не ронять. Меч от этого числа не зависит: он не падает никогда.
	 */
	public static final int NAME_TAG_COUNT = 1;

	/** Имя над головой и текст на бирке. */
	public static final String DISPLAY_NAME = "Excalibur";

	// ========================================================================

	/** Дерево, камень, медь, железо, алмаз. Шансы равные, золота и незерита нет. */
	private static final Item[] SWORDS = {
			Items.WOODEN_SWORD,
			Items.STONE_SWORD,
			Items.COPPER_SWORD,
			Items.IRON_SWORD,
			Items.DIAMOND_SWORD
	};

	private ZombieExcalibur() {
	}

	public static boolean isExcalibur(Zombie zombie) {
		return ENABLED
				&& zombie instanceof ZombieExcaliburEntity data
				&& data.evolvingthreats$isExcalibur();
	}

	/**
	 * Конец появления зомби. true = это Excalibur: случайный меч, щит, тотем,
	 * броня мода и голова ему не выдаются. Зелья при появлении уже могли повеситься.
	 */
	public static boolean tryClaim(Zombie zombie, EntitySpawnReason reason) {
		if (!canBeExcalibur(zombie)) {
			return false;
		}
		ZombieExcaliburEntity data = (ZombieExcaliburEntity) zombie;
		if (data.evolvingthreats$isExcalibur()) {
			equip(zombie);
			return true;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		if (zombie.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setExcalibur(true);
		equip(zombie);
		return true;
	}

	/** Сколько опыта дать. -1 = это не Excalibur, пусть решает игра. */
	public static int experienceReward(Zombie zombie) {
		if (!isExcalibur(zombie)) {
			return -1;
		}
		return Math.max(0, XP_REWARD);
	}

	/** Бирка с именем. Меч сюда не входит. */
	public static void dropBonus(Zombie zombie, ServerLevel level) {
		if (!isExcalibur(zombie) || NAME_TAG_COUNT <= 0) {
			return;
		}
		ItemStack tag = new ItemStack(Items.NAME_TAG, NAME_TAG_COUNT);
		tag.set(DataComponents.CUSTOM_NAME, name());
		zombie.spawnAtLocation(level, tag);
	}

	/** Команда: Excalibur перед игроком. null — механика выключена. */
	public static @Nullable Zombie spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (zombie == null) {
			throw new IllegalStateException("Failed to create Excalibur");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		zombie.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (zombie instanceof ZombieExcaliburEntity data) {
			data.evolvingthreats$setExcalibur(true);
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

	private static boolean canBeExcalibur(Zombie zombie) {
		if (!ENABLED || !(zombie instanceof ZombieExcaliburEntity)) {
			return false;
		}
		if (zombie.getType() != EntityTypes.ZOMBIE || zombie.isBaby()) {
			return false;
		}
		if (zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		return !(zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant());
	}

	private static void equip(Zombie zombie) {
		zombie.setCustomName(name());
		zombie.setCustomNameVisible(true);
		// Имя уже не даёт ему исчезнуть. Чужую вещь не подбирает, иначе меч сменится.
		zombie.setCanPickUpLoot(false);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, sword(zombie));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		// 0 = меч не падает, даже с Добычей.
		zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		zombie.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
	}

	private static ItemStack sword(Zombie zombie) {
		Item item = SWORDS[zombie.getRandom().nextInt(SWORDS.length)];
		ItemStack stack = new ItemStack(item);
		if (!(zombie.level() instanceof ServerLevel level) || SHARPNESS_LEVEL <= 0) {
			return stack;
		}
		Holder<Enchantment> sharp = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.SHARPNESS);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(sharp, SHARPNESS_LEVEL));
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
