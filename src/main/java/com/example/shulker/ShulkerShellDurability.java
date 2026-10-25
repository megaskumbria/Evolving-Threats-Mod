package com.example.shulker;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

/**
 * Удар по закрытому панцирю шалкера иногда снимает половину прочности
 * с оружия или инструмента, которым ударили. Лук и арбалет тоже.
 * Чара «Прочность» это списание не уменьшает.
 */
public final class ShulkerShellDurability {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — панцирь больше не портит оружие. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0) на каждый удар по закрытому панцирю.
	 * 0.05 = 5%. Бросок заново на каждый удар, общий счётчик на шалкеров не ведётся.
	 * Открытый шалкер этот шанс не бросает. Удар кулаком тоже: у кулака нет прочности.
	 * 0 = никогда. 1 = каждый удар по закрытому панцирю.
	 */
	public static final float HIT_CHANCE = 0.05f;

	/**
	 * Какую долю максимальной прочности снять. 0.5 = половина шкалы.
	 * Если осталось меньше, вещь ломается.
	 * 0 = не списывать. 1 = вещь ломается сразу.
	 */
	public static final float DURABILITY_FRACTION = 0.5f;

	// ========================================================================

	private ShulkerShellDurability() {
	}

	/**
	 * Шалкер уже закрыт. Вызывается в момент удара, даже если панцирь урон не пропустил.
	 * Стрела по закрытому шалкеру как раз не наносит урон, но лук всё равно может пострадать.
	 */
	public static void onClosedHit(DamageSource source) {
		if (!ENABLED || HIT_CHANCE <= 0.0f || DURABILITY_FRACTION <= 0.0f) {
			return;
		}
		Player player = playerAttacker(source);
		if (player == null || player.level().isClientSide() || player.hasInfiniteMaterials()) {
			return;
		}
		ItemStack weapon = liveWeapon(player, source);
		if (weapon.isEmpty()) {
			return;
		}
		if (player.getRandom().nextFloat() >= HIT_CHANCE) {
			return;
		}
		shred(player, weapon);
	}

	/** Игрок ударил сам или его снаряд. Чужой моб и взрыв сюда не входят. */
	private static Player playerAttacker(DamageSource source) {
		Entity direct = source.getDirectEntity();
		if (direct instanceof Player player) {
			return player;
		}
		if (direct instanceof Projectile && source.getEntity() instanceof Player player) {
			return player;
		}
		return null;
	}

	/**
	 * Предмет, который реально лежит у игрока.
	 * У стрелы игра хранит только копию лука, по ней прочность не спишешь.
	 */
	private static ItemStack liveWeapon(Player player, DamageSource source) {
		if (source.getDirectEntity() instanceof Player) {
			ItemStack hand = player.getMainHandItem();
			return hand.isDamageableItem() ? hand : ItemStack.EMPTY;
		}
		ItemStack fired = source.getWeaponItem();
		if (fired == null || !fired.isDamageableItem()) {
			return ItemStack.EMPTY;
		}
		if (sameTool(player.getMainHandItem(), fired)) {
			return player.getMainHandItem();
		}
		if (sameTool(player.getOffhandItem(), fired)) {
			return player.getOffhandItem();
		}
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (sameTool(stack, fired)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static boolean sameTool(ItemStack stack, ItemStack fired) {
		return stack.isDamageableItem() && ItemStack.isSameItem(stack, fired);
	}

	private static void shred(Player player, ItemStack weapon) {
		int max = weapon.getMaxDamage();
		if (max <= 0) {
			return;
		}
		int loss = Math.max(1, (int) (max * DURABILITY_FRACTION));
		int next = weapon.getDamageValue() + loss;
		if (player instanceof ServerPlayer serverPlayer) {
			CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(serverPlayer, weapon, Math.min(next, max));
		}
		if (next >= max) {
			ItemStack broken = weapon.copy();
			EquipmentSlot hand = handSlot(player, weapon);
			if (hand != null) {
				player.setItemSlot(hand, ItemStack.EMPTY);
				player.onEquippedItemBroken(broken, hand);
			} else {
				weapon.shrink(1);
				playBreak(player);
			}
		} else {
			weapon.setDamageValue(next);
			EquipmentSlot hand = handSlot(player, weapon);
			if (hand != null) {
				player.setItemSlot(hand, weapon);
			}
			playBreak(player);
		}
	}

	/** Тот же треск, что при полной поломке. Слышно, даже если вещь ещё в руке. */
	private static void playBreak(Player player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	private static EquipmentSlot handSlot(Player player, ItemStack weapon) {
		if (player.getMainHandItem() == weapon) {
			return EquipmentSlot.MAINHAND;
		}
		if (player.getOffhandItem() == weapon) {
			return EquipmentSlot.OFFHAND;
		}
		return null;
	}
}
