package com.example.shulker;

import com.example.advancement.ModAdvancements;
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
 * Hitting a closed shulker shell sometimes removes half the durability
 * from the weapon or tool used. Bows and crossbows count too.
 * Unbreaking does not reduce this loss.
 */
public final class ShulkerShellDurability {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — closed shells no longer damage gear. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) per hit on a closed shell.
	 * 0.05 = 5%. Fresh roll each hit; no global counter across shulkers.
	 * Open shulkers never roll. Bare fist hits too but fists have no durability.
	 * 0 = never. 1 = every closed-shell hit.
	 */
	public static final float HIT_CHANCE = 0.05f;

	/**
	 * Fraction of max durability to remove. 0.5 = half the bar.
	 * If less durability remains, the item breaks.
	 * 0 = no loss. 1 = item breaks immediately.
	 */
	public static final float DURABILITY_FRACTION = 0.5f;

	// ========================================================================

	private ShulkerShellDurability() {
	}

	/**
	 * Shulker is already closed. Called at hit time even if the shell blocked damage.
	 * Arrows on a closed shulker deal no damage, but the bow can still take durability loss.
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

	/** Player hit directly or via their projectile. Other mobs and explosions are excluded. */
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
	 * Stack that actually lives in the player's inventory.
	 * Arrows only store a bow copy; durability cannot be applied to that copy.
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
		if (player instanceof ServerPlayer serverPlayer) {
			ModAdvancements.onToolGroaned(serverPlayer);
		}
	}

	/** Same crack as a full break. Plays even if the item is still held. */
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
