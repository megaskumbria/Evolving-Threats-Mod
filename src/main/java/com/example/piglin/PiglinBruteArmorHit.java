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
 * Редкий удар брутального пиглина снимает половину прочности с одной вещи в броне
 * и вешает головокружение, чтобы игрок заметил, что броня пострадала.
 * Чара «Прочность» этот удар не ослабляет: списывается само число, а не «попытка износа».
 */
public final class PiglinBruteArmorHit {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — удар брута снова только обычный. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что удар, который уже снял здоровье, испортит броню.
	 * 0.05 = 5%, примерно каждый двадцатый такой удар.
	 * Бросок заново на каждый удар. Щит, который отбил удар, и мигание после удара не считаются.
	 * 0 = брут броню не портит. 1 = каждый прошедший удар.
	 */
	public static final float HIT_CHANCE = 0.05f;

	/**
	 * Какую долю максимальной прочности снять с одной вещи. 0.5 = половина шкалы.
	 * Незеритовый нагрудник (592) теряет 296, даже с чарой «Прочность».
	 * Если осталось меньше этой доли, вещь ломается и исчезает.
	 * 0 = прочность не трогать. 1 = выбранная вещь ломается сразу.
	 */
	public static final float DURABILITY_FRACTION = 0.5f;

	/**
	 * Сколько секунд длится головокружение после такого удара. 10 = десять секунд.
	 * Экран плывёт, как от испорченной еды. Молоко снимает эффект сразу.
	 * 0 = головокружения нет, прочность всё равно списывается.
	 */
	public static final int NAUSEA_SECONDS = 10;

	// ========================================================================

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
	};

	private PiglinBruteArmorHit() {
	}

	/**
	 * Удар уже снял здоровье.
	 * Обычный пиглин и зомби-пиглин сюда не попадают. Игрок в творческом режиме тоже.
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

	/** Одна случайная надетая вещь брони, у которой вообще бывает прочность. */
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

	/** Тот же треск, что при полной поломке. Игрок слышит его, даже если вещь ещё на нём. */
	private static void playBreak(Player player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	/** Тыква и элитры в этих слотах бронёй не считаются. */
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
