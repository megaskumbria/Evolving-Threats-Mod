package com.example.piglin;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 Rare impact of brutal piglin removes half the strength from one thing in armor
 * and hangs dizziness so that the player notices that the armor is damaged.
 The charm of “Durability” this blow does not weaken: the number itself is written off, and not “attempted wear”.
 */
public final class PiglinBruteArmorHit {

	// ===================== SETTINGS (change these numbers) =====================

	/*False – the brute strike again is only normal. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0-1.0) that the impact, which has already taken off health, will spoil the armor.
	 * 0.05 = 5%, approximately every twentieth such impact.
	 * Throw again for each stroke. The shield that repulsed the impact and the blinking after the impact do not count.
	 * 0 = brute does not spoil the armor. 1 = every stroke that passes.
	 */
	public static final float HIT_CHANCE = 0.05f;

	/**
	 What percentage of maximum strength to remove from one thing? 0.5 = half the scale.
	 The nonseritic breastplate (592) loses 296, even with the spell of "Strength".
	 If less than this fraction remains, the thing breaks down and disappears.
	 * 0 = strength not to be touched. 1 = the selected item breaks down immediately.
	 */
	public static final float DURABILITY_FRACTION = 0.5f;

	/**
	 How many seconds does dizziness last after such a blow? 10 = ten seconds.
	 The screen floats like spoiled food. Milk takes effect immediately.
	 * 0 = no dizziness, strength is still written off.
	 */
	public static final int NAUSEA_SECONDS = 10;

	// ========================================================================

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
	};

	private PiglinBruteArmorHit() {
	}

	/**
	 The blow has already taken off.
	 Ordinary piglins and zombie piglins do not get here. A player in creative mode, too.
	 */
	public static void onHit(PiglinBrute brute, Player player) {
		if (!ENABLED || HIT_CHANCE <= 0.0f || DURABILITY_FRACTION <= 0.0f) {
			return;
		}
		if (player.level().isClientSide() || player.hasInfiniteMaterials()) {
			return;
		}
		if (brute.getRandom().nextFloat() >= HIT_CHANCE) {
			return;
		}
		EquipmentSlot slot = pickSlot(player, brute.getRandom());
		if (slot == null) {
			return;
		}
		shred(player, slot);
		if (NAUSEA_SECONDS > 0) {
			player.addEffect(new MobEffectInstance(
					MobEffects.NAUSEA,
					NAUSEA_SECONDS * 20,
					0,
					false,
					true,
					true), brute);
		}
	}

	/*One random piece of armor worn, which generally has strength. */
	private static EquipmentSlot pickSlot(Player player, RandomSource random) {
		List<EquipmentSlot> worn = new ArrayList<>(ARMOR_SLOTS.length);
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isDamageableItem() && stack.is(armorTag(slot))) {
				worn.add(slot);
			}
		}
		if (worn.isEmpty()) {
			return null;
		}
		return worn.get(random.nextInt(worn.size()));
	}

	private static void shred(Player player, EquipmentSlot slot) {
		ItemStack armor = player.getItemBySlot(slot);
		int max = armor.getMaxDamage();
		if (max <= 0) {
			return;
		}
		int loss = Math.max(1, (int) (max * DURABILITY_FRACTION));
		int next = armor.getDamageValue() + loss;
		if (player instanceof ServerPlayer serverPlayer) {
			CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(serverPlayer, armor, Math.min(next, max));
		}
		if (next >= max) {
			ItemStack broken = armor.copy();
			player.setItemSlot(slot, ItemStack.EMPTY);
			player.onEquippedItemBroken(broken, slot);
		} else {
			armor.setDamageValue(next);
			player.setItemSlot(slot, armor);
			playBreak(player);
		}
	}

	/** Same crack as when it was completely broken. The player can hear it even if it is still on it. */
	private static void playBreak(Player player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	/*Pumpkin and elites in these slots are not considered armor. */
	private static TagKey<Item> armorTag(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> ItemTags.HEAD_ARMOR;
			case CHEST -> ItemTags.CHEST_ARMOR;
			case LEGS -> ItemTags.LEG_ARMOR;
			case FEET -> ItemTags.FOOT_ARMOR;
			default -> ItemTags.HEAD_ARMOR;
		};
	}
}
