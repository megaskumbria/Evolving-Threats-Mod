package com.example;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Тотем бессмертия во второй руке у зомби и скелета.
 * Игра сама спасает любого, кто держит тотем: здоровье падает до половины сердца,
 * тотем сгорает, моб получает регенерацию, поглощение и огнестойкость.
 * В бездну и от команды убийства тотем не спасает — так же, как у игрока.
 * Вторая рука после тотема занята: щит, вредная стрела и второй меч туда не ставятся.
 */
public final class MobTotem {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — тотемы не выдаются. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что зомби или скелет появится с тотемом во второй руке на 100-й день (конец шкалы угрозы). 0.04 = 4%.
	 * В начале игры шанс намного меньше и растёт со шкалой.
	 * Реже щита (25%): тотем один раз отменяет смерть. 0 = тотемов не будет. 1 = у всех, у кого свободна вторая рука.
	 */
	public static final float SPAWN_CHANCE = 0.04f;

	/**
	 * Шанс, что неиспользованный тотем выпадет при смерти. 0.085 ≈ 8.5%, как щит.
	 * Если тотем уже сработал, он сгорает и упасть не может. 1.0 = падает всегда, 0 = не падает.
	 */
	public static final float DROP_CHANCE = 0.085f;

	// ========================================================================

	private MobTotem() {
	}

	/**
	 * Вызвать до щита и вредной стрелы.
	 *
	 * @return true — вторая рука занята тотемом, другое туда ставить не нужно
	 */
	public static boolean tryEquip(Mob mob) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f) {
			return false;
		}
		if (!mob.getOffhandItem().isEmpty()) {
			return false;
		}
		RandomSource random = mob.getRandom();
		// РАСТЁТ: SPAWN_CHANCE — шанс на 100-й день, в начале игры он меньше (ThreatTable.TOTEM_START_RATIO).
		if (random.nextFloat() >= ThreatScale.growingChance(mob.level(), SPAWN_CHANCE, ThreatTable.TOTEM_START_RATIO)) {
			return false;
		}
		mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		mob.setDropChance(EquipmentSlot.OFFHAND, DROP_CHANCE);
		return true;
	}
}
