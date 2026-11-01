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
 * Голый зомби иногда забирает вещь из руки игрока себе.
 * Пока вещь у него, он не исчезает сам и не подбирает другую.
 * При смерти вещь падает на землю.
 */
public final class ZombieTheft {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — удар ничего не забирает. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что удар, который уже снял здоровье, заберёт вещь.
	 * 0.04 = 4%, примерно каждый двадцать пятый такой удар.
	 * 0.02 = вдвое реже. 0 = никогда. 1 = каждый удар.
	 * Зомби с предметом в основной руке не ворует, даже если шанс выпал.
	 */
	public static final float STEAL_CHANCE = 0.04f;

	/**
	 * Шанс (0.0–1.0), что украденная вещь выпадет при смерти.
	 * 1 = всегда, и прочность меча не портится.
	 * Меньше 1 — не каждый раз, и игра может сбить прочность. 0 = вещь пропадает вместе с зомби.
	 */
	public static final float DROP_CHANCE = 1.0f;

	// ========================================================================

	private ZombieTheft() {
	}

	/**
	 * Удар уже снял здоровье.
	 * Щит и мигание сюда не попадают.
	 * Утопленник и зомби-пиглин не воруют. Кадавр и зомби-житель — воруют, если рука пустая.
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

	/** Сначала меч в основной руке. Если она пустая — щит или тотем во второй. */
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
