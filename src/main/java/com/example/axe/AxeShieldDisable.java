package com.example.axe;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Удар зомби, скелета или брутального пиглина топором сбивает щит,
 * как удар поборника: щит на несколько секунд нельзя поднять.
 * Деревянный топор щит не сбивает.
 *
 * <p>У поборника и у брута с золотым топором в основной руке игра это уже делает сама.
 * Здесь то же самое для топора во второй руке и запрет для деревянного.
 * От дней не зависит.
 */
public final class AxeShieldDisable {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — щит снова сбивает только то, что умеет сама игра. Деревянный топор в основной руке тоже. */
	public static final boolean ENABLED = true;

	/**
	 * На сколько секунд щит нельзя поднять после удара топором.
	 * 5 = как у поборника и у топора в обычной игре. 2 = короче. 0 = щит не сбивается.
	 */
	public static final float COOLDOWN_SECONDS = 5.0f;

	// ========================================================================

	/** Камень, медь, золото, железо, алмаз, незерит. Дерева нет. */
	private static final Item[] AXES = {
			Items.STONE_AXE,
			Items.COPPER_AXE,
			Items.GOLDEN_AXE,
			Items.IRON_AXE,
			Items.DIAMOND_AXE,
			Items.NETHERITE_AXE
	};

	private AxeShieldDisable() {
	}

	/**
	 * Сколько секунд отдать игре, когда удар попал в щит.
	 * -1 = этот моб не наш, пусть решает игра.
	 * 0 = щит не сбивать (деревянный топор).
	 */
	public static float seconds(LivingEntity attacker) {
		if (!ENABLED || COOLDOWN_SECONDS < 0.0f || !applies(attacker)) {
			return -1.0f;
		}
		if (holds(attacker, false)) {
			return COOLDOWN_SECONDS;
		}
		if (holds(attacker, true)) {
			return 0.0f;
		}
		return -1.0f;
	}

	/** Зомби (и его виды), скелет (и его виды), брутальный пиглин. Поборник сюда не входит. */
	private static boolean applies(LivingEntity attacker) {
		return attacker instanceof Zombie
				|| attacker instanceof AbstractSkeleton
				|| attacker instanceof PiglinBrute;
	}

	/** {@code wooden} — ищем деревянный топор. Иначе любой другой из списка. Смотрим обе руки. */
	private static boolean holds(LivingEntity attacker, boolean wooden) {
		return isAxe(attacker.getItemBySlot(EquipmentSlot.MAINHAND), wooden)
				|| isAxe(attacker.getItemBySlot(EquipmentSlot.OFFHAND), wooden);
	}

	private static boolean isAxe(ItemStack stack, boolean wooden) {
		if (stack.isEmpty()) {
			return false;
		}
		if (wooden) {
			return stack.is(Items.WOODEN_AXE);
		}
		for (Item axe : AXES) {
			if (stack.is(axe)) {
				return true;
			}
		}
		return false;
	}
}
