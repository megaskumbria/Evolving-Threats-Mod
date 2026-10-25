package com.example.illager;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Часть поборников появляется с алмазным топором вместо железного.
 * Особняк, патруль, рейд, яйцо и команда спавна — один и тот же бросок.
 * Чары, которые игра или мод уже повесили на железный топор, переходят на алмазный.
 */
public final class VindicatorDiamondAxe {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — у всех поборников снова железный топор. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что топор в руке станет алмазным. 0.25 = 25%, примерно каждый четвёртый.
	 * Бросается один раз, когда топор только выдали: и в особняке, и в патруле, и в рейде.
	 * 0 = алмазных нет. 1 = алмазный у каждого поборника.
	 */
	public static final float CHANCE = 0.25f;

	// ========================================================================

	private VindicatorDiamondAxe() {
	}

	/**
	 * Вызвать, когда железный топор уже лежит в руке.
	 * В рейде это после чар рейда. Вне рейда — до чар, их вешает игра следом.
	 */
	public static void tryReplace(Vindicator vindicator) {
		if (!ENABLED || CHANCE <= 0.0f) {
			return;
		}
		ItemStack hand = vindicator.getMainHandItem();
		if (!hand.is(Items.IRON_AXE)) {
			return;
		}
		if (vindicator.getRandom().nextFloat() >= CHANCE) {
			return;
		}
		vindicator.setItemSlot(EquipmentSlot.MAINHAND, hand.transmuteCopy(Items.DIAMOND_AXE));
	}
}
