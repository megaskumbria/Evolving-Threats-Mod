package com.example.zombie;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Unarmed zombies sometimes steal an item from the player's hand.
 * While they hold it they do not despawn and do not pick up anything else.
 * The item drops on the ground when the zombie dies.
 */
public final class ZombieTheft {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — hits never steal. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a hit that already dealt damage steals an item.
	 * 0.04 = 4%, roughly every twenty-fifth such hit.
	 * 0.02 = half as often. 0 = never. 1 = every hit.
	 * Zombies with something in the main hand never steal, even if the roll succeeds.
	 */
	public static final float STEAL_CHANCE = 0.04f;

	/**
	 * Chance (0.0–1.0) that the stolen item drops on death.
	 * 1 = always, and sword durability is not reduced on drop.
	 * Below 1 — not every time, and the game may reduce durability. 0 = item vanishes with the zombie.
	 */
	public static final float DROP_CHANCE = 1.0f;

	// ========================================================================

	private ZombieTheft() {
	}

	/**
	 * Hit already dealt damage.
	 * Shield blocks and iframes do not reach here.
	 * Drowned and zombified piglins never steal. Husks and zombie villagers steal if the hand is empty.
	 */
	public static void onHit(Zombie zombie, Player player) {
		if (!ENABLED || STEAL_CHANCE <= 0.0f || !player.isAlive()) {
			return;
		}
		if (zombie instanceof Drowned || zombie instanceof ZombifiedPiglin) {
			return;
		}
		if (!zombie.getMainHandItem().isEmpty()) {
			return;
		}
		InteractionHand hand = handToSteal(player);
		if (hand == null) {
			return;
		}
		if (zombie.getRandom().nextFloat() >= STEAL_CHANCE) {
			return;
		}
		ItemStack stolen = player.getItemInHand(hand).copy();
		player.setItemInHand(hand, ItemStack.EMPTY);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, stolen);
		float dropChance = Math.clamp(DROP_CHANCE, 0.0f, 1.0f);
		zombie.setDropChance(EquipmentSlot.MAINHAND, dropChance);
		if (dropChance >= 1.0f) {
			zombie.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		}
		zombie.setPersistenceRequired();
		zombie.setCanPickUpLoot(false);
		zombie.level().playSound(null, zombie.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 1.0f, 0.6f);
	}

	/** Main hand first (weapon). If empty — shield or totem in offhand. */
	private static InteractionHand handToSteal(Player player) {
		if (!player.getMainHandItem().isEmpty()) {
			return InteractionHand.MAIN_HAND;
		}
		if (!player.getOffhandItem().isEmpty()) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}
}
