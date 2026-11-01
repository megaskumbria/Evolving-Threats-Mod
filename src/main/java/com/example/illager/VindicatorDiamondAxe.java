package com.example.illager;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 Some of the champions appear with a diamond axe instead of an iron one.
 Mansion, patrol, raid, egg and team spawn - the same throw.
 The charms that a game or a mod has already hung on an iron axe pass to a diamond one.
 */
public final class VindicatorDiamondAxe {

	// ===================== SETTINGS (change these numbers) =====================

	/*False – all the champions again have an iron axe. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0-1.0) that the axe in the hand will become diamond. 0.25 = 25%, about one in four.
	 It is thrown once when the axe was only issued: in the mansion, in the patrol, and in the raid.
	 * 0 = no diamonds. 1 = diamond for each champion.
	 */
	public static final float CHANCE = 0.25f;

	// ========================================================================

	private VindicatorDiamondAxe() {
	}

	/**
	 Call when the iron axe is already in your hand.
	 In the raid it's after the spell of the raid. Outside the raid - to the charm, they hang the game next.
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
