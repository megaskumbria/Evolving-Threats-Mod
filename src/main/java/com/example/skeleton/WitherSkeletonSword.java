package com.example.skeleton;

import com.example.MobEnchantments;
import com.example.entity.SpawnerHeadCarrierEntity;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Скелет-иссушитель в аду иногда получает незеритовый меч.
 * Каменный меч остаётся у остальных. На оба меча вешаются чары, и они растут со шкалой угрозы.
 * Незеритовый меч не выпадает. Опыт за любого иссушителя свой, общий множитель его не удваивает.
 */
public final class WitherSkeletonSword {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — в аду снова только каменный меч, без чар мода. Опыт ниже остаётся. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0) незеритового меча на 100-й день. 0.15 = 15%.
	 * GROWING: в начале это 10% от числа (ThreatTable.DANGEROUS_START_RATIO), около 1,5%.
	 * Луна чуть двигает шанс. 0 = незерита нет, каменный меч и чары остаются.
	 * Только ад. Осада в обычном мире и носитель спавнера этот бросок не делают.
	 */
	public static final float NETHERITE_CHANCE = 0.15f;

	/**
	 * Шанс (0.0–1.0), что меч получит чары, на 100-й день. 0.70 = 70%, как у меча обычного скелета.
	 * В начале около 1% (ThreatTable.WEAPON_ENCHANT_CHANCE_START). Число чар и их сила тоже растут.
	 * Список тот же: острота, отбрасывание, заговор огня, добыча и остальные с меча.
	 * 0 = чар мода нет. Каменный меч при этом всё ещё может выпасть, как в обычной игре.
	 */
	public static final float ENCHANT_CHANCE = 0.70f;

	/**
	 * Опыт, если иссушителя убил игрок. 25.
	 * Раньше игра давала 5, мод удваивал до 10 и добавлял немного за меч.
	 * Это число вместо старого. Общий множитель его второй раз не удваивает.
	 * -1 = снова как у обычного моба, с множителем. 0 = опыта нет.
	 * Ад, осада и носитель спавнера получают одно и то же.
	 */
	public static final int XP_REWARD = 25;

	// ========================================================================

	private WitherSkeletonSword() {
	}

	/** Конец появления. Меч и чары — только в аду и не у носителя спавнера. */
	public static void tryApply(WitherSkeleton skeleton) {
		if (!ENABLED || skeleton.level().dimension() != Level.NETHER) {
			return;
		}
		if (skeleton instanceof SpawnerHeadCarrierEntity carrier && carrier.evolvingthreats$isSpawnerHeadCarrier()) {
			return;
		}
		if (skeleton.getRandom().nextFloat() < ThreatScale.dangerousChance(skeleton.level(), NETHERITE_CHANCE)) {
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
			// 0 = не выпадает никогда, даже с Добычей. Каменный меч это число не трогает.
			skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		}
		MobEnchantments.rollMainHandGrowing(skeleton, ENCHANT_CHANCE);
	}

	/** Сколько опыта дать. -1 = это не иссушитель или опыт снова обычный. */
	public static int experienceReward(Mob mob) {
		if (!(mob instanceof WitherSkeleton) || XP_REWARD < 0) {
			return -1;
		}
		return XP_REWARD;
	}
}
