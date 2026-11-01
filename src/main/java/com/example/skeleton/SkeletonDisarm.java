package com.example.skeleton;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/**
 * Стрела скелета иногда выбивает вещи из рук игрока.
 * Летит вперёд, как выброс кнопкой Q, и пару секунд её нельзя подобрать.
 * Броня и остальной инвентарь не трогаются.
 */
public final class SkeletonDisarm {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — стрела ничего из рук не выбивает. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что попавшая стрела выбьет руки.
	 * 0.05 = 5%, примерно каждая двадцатая стрела, которая сняла здоровье.
	 * 0.02 почти не заметно за бой. 0.15 выбивает слишком часто.
	 * 0 = никогда. 1 = каждая такая стрела.
	 * Один бросок на попадание: либо ничего, либо обе занятые руки сразу.
	 */
	public static final float DISARM_CHANCE = 0.05f;

	// ========================================================================

	private SkeletonDisarm() {
	}

	/**
	 * Стрела уже сняла здоровье, либо игрок в креативе и её удар туда не доходит.
	 * Щит, который отбил выстрел, и мигание после удара сюда не попадают.
	 * Стрелы игрока и других мобов не считаются: стрелок должен быть скелетом, странником или болотным.
	 */
	public static void onArrowHit(AbstractArrow arrow, Player player) {
		if (!ENABLED || DISARM_CHANCE <= 0.0f || !player.isAlive()) {
			return;
		}
		if (!(arrow.level() instanceof ServerLevel) || !(arrow.getOwner() instanceof AbstractSkeleton)) {
			return;
		}
		if (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
			return;
		}
		if (arrow.getRandom().nextFloat() >= DISARM_CHANCE) {
			return;
		}
		throwHand(player, InteractionHand.MAIN_HAND);
		throwHand(player, InteractionHand.OFF_HAND);
	}

	/** Вся стопка из руки летит вперёд. Пустая рука ничего не роняет. */
	private static void throwHand(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			return;
		}
		ItemStack thrown = held.copy();
		player.setItemInHand(hand, ItemStack.EMPTY);
		ItemEntity entity = player.createItemStackToDrop(thrown, false, true);
		if (entity != null) {
			player.level().addFreshEntity(entity);
		}
	}
}
