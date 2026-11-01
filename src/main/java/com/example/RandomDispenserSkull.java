package com.example;

import com.example.threat.ThreatScale;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Скелет с раздатчиком на голове — редкий вариант с залпом стрел по таймеру.
 * Совместим с бронёй, dual-wield и т.д.; раздатчик занимает слот HEAD (перекрывает шлем).
 */
public final class RandomDispenserSkull {

	// ===================== SETTINGS (меняй эти числа) =====================

	/**
	 * Шанс (0.0–1.0), что у скелета на голове будет раздатчик, на 100-й день
	 * (GROWING: в начале игры в 10 раз меньше, см. ThreatTable.DANGEROUS_START_RATIO). 0.02 = 2%.
	 */
	public static final float SKELETON_DISPENSER_HEAD_CHANCE = 0.02f;

	/** Минимальная пауза между залпами, секунды. */
	public static final int BARRAGE_COOLDOWN_MIN_SECONDS = 15;

	/** Максимальная пауза между залпами, секунды. */
	public static final int BARRAGE_COOLDOWN_MAX_SECONDS = 20;

	/** Сколько стрел выпускается за один залп. */
	public static final int BARRAGE_ARROW_COUNT = 14;

	/** Каждые N тиков (20 тиков = 1 сек) выпускается очередная стрела в залпе. 2 = ~7 стр/сек. */
	public static final int BARRAGE_SHOOT_INTERVAL_TICKS = 2;

	/** Длительность фазы залпа (тиков). Если стрелы кончились раньше — фаза всё равно может идти до конца. */
	public static final int BARRAGE_PHASE_TICKS = 40;

	/** Скорость стрел (чем больше — тем быстрее летят). */
	public static final float BARRAGE_ARROW_VELOCITY = 1.35f;

	/**
	 * Разброс (как у ванильного лука): чем больше — тем менее точно.
	 * 12–18 — «дофига стрел, но не снайпер».
	 */
	public static final float BARRAGE_INACCURACY = 14.0f;

	/** Шанс дропа раздатчика с головы при смерти. */
	public static final float DISPENSER_HEAD_DROP_CHANCE = 0.085f;

	// ========================================================================

	private RandomDispenserSkull() {
	}

	public static boolean isDispenserSkull(Mob mob) {
		return mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.DISPENSER);
	}

	public static void tryEquipDispenserHead(Mob mob) {
		if (mob.getRandom().nextFloat() >= ThreatScale.dangerousChance(mob.level(), SKELETON_DISPENSER_HEAD_CHANCE)) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DISPENSER));
		mob.setDropChance(EquipmentSlot.HEAD, DISPENSER_HEAD_DROP_CHANCE);
	}

	public static int randomCooldownTicks(RandomSource random) {
		int minTicks = BARRAGE_COOLDOWN_MIN_SECONDS * 20;
		int maxTicks = BARRAGE_COOLDOWN_MAX_SECONDS * 20;
		if (maxTicks <= minTicks) {
			return minTicks;
		}
		return random.nextIntBetweenInclusive(minTicks, maxTicks);
	}
}
