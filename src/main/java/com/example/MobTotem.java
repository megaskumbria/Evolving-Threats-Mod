package com.example;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 The totem of immortality in the second hand of a zombie and a skeleton.
 The game itself saves anyone who holds a totem: health drops to half the heart.
 * The totem burns, the mob receives regeneration, absorption and fire resistance.
 In the abyss and from the team of murder totem does not spawn - just like the player.
 The second hand after the totem is occupied: the shield, the harmful arrow and the second sword are not placed there.
 */
public final class MobTotem {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - totems are not issued. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0-1.0) that a zombie or skeleton will appear with a totem in the second hand on day 100 (end of the threat scale). 0.04 = 4%.
	 At the beginning of the game, the chance is much smaller and increases with the scale.
	 Less than the shield (25%): the totem cancels death once. 0 = no totems. 1 = anyone with a free second hand.
	 */
	public static final float SPAWN_CHANCE = 0.04f;

	/**
	 The chance that an unused totem will fall on death. 0.085 to 8.5% as a shield.
	 If the totem has already worked, it burns and cannot fall. 1.0 = always falls, 0 = never falls.
	 */
	public static final float DROP_CHANCE = 0.085f;

	// ========================================================================

	private MobTotem() {
	}

	/**
	 Call to the shield and the harmful arrow.
	 *
	 * @return true - the second hand is occupied by the totem, you do not need to put the other one there
	 */
	public static boolean tryEquip(Mob mob) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f) {
			return false;
		}
		if (!mob.getOffhandItem().isEmpty()) {
			return false;
		}
		RandomSource random = mob.getRandom();
		// GROWING: SPAWN CHANCE - the chance on the 100th day, at the beginning of the game it is less (ThreatTable.TOTEM START RATIO).
		if (random.nextFloat() >= ThreatScale.growingChance(mob.level(), SPAWN_CHANCE, ThreatTable.TOTEM_START_RATIO)) {
			return false;
		}
		mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		mob.setDropChance(EquipmentSlot.OFFHAND, DROP_CHANCE);
		return true;
	}
}
