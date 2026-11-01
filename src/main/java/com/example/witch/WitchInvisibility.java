package com.example.witch;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;
import java.util.Optional;

/**
 * Ведьма в бою может отпить невидимость.
 * Глоток такой же, как у её обычных зелий: бутылка в руке, она замедляется, потом эффект.
 * Пока пьёт, зелья не кидает. После глотка её не видно, но броски и звуки остаются.
 * Вода без подводного дыхания, огонь без огнестойкости и лечение не перебиваются.
 */
public final class WitchInvisibility {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — ведьма больше не пьёт невидимость. Уже выпитое до конца срока само спадёт. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0) выпить, когда подошла проверка. 0.10 = 10%.
	 * Было 0.35: ведьма пила почти в каждом бою. Проверка не каждый миг: см. {@link #CHECK_SECONDS}.
	 * 0 = не пьёт. 1 = пьёт на каждой проверке.
	 */
	public static final float DRINK_CHANCE = 0.10f;

	/**
	 * Как часто в бою бросать шанс, в секундах. 15 = раз в пятнадцать секунд, пока есть цель.
	 * Было 6: вместе с высоким шансом невидимость висела почти весь бой.
	 * Без цели и пока невидимость уже висит, секунды не идут.
	 * Первая проверка — сразу, как ведьма кого-то выбрала.
	 * 0 игра считает как 1, иначе шанс бросался бы каждый миг.
	 */
	public static final int CHECK_SECONDS = 15;

	/**
	 * Сколько секунд ведьму не видно после глотка. 20 = двадцать секунд.
	 * Обычное зелье невидимости длится 3 минуты (180). Здесь короче, чтобы бой не превращался
	 * в поиск невидимки на всю ночь. Поставь 180, если нужна полная бутылка.
	 * 0 = не пьёт.
	 */
	public static final int INVISIBILITY_SECONDS = 20;

	/**
	 * Частицы вокруг ведьмы, пока она невидима.
	 * false — её не выдаёт облачко. Брошенную бутылку и звуки всё равно слышно и видно.
	 * true — частицы как у обычного зелья, ведьму проще заметить.
	 */
	public static final boolean SHOW_PARTICLES = false;

	// ========================================================================

	/**
	 * То же замедление, что игра вешает на время любого глотка ведьмы.
	 * Идентификатор общий с игрой: когда глоток кончается, игра сама его снимает.
	 */
	private static final Identifier DRINKING_SLOW_ID = Identifier.withDefaultNamespace("drinking");
	private static final AttributeModifier DRINKING_SLOW = new AttributeModifier(
			DRINKING_SLOW_ID, -0.25, AttributeModifier.Operation.ADD_VALUE);

	/** Отрицательная пауза значит: шанс уже выпал, ждём свободный глоток. */
	private static final int QUEUED = -1;

	private WitchInvisibility() {
	}

	/**
	 * Начало тика ведьмы, до того как она сама выберет воду, огонь, лечение или скорость.
	 * {@code cooldownTicks} — сколько тиков до следующей проверки. Его же вернуть в поле ведьмы.
	 */
	public static Drink tick(Witch witch, int cooldownTicks) {
		if (!ENABLED || DRINK_CHANCE <= 0.0f || INVISIBILITY_SECONDS <= 0
				|| witch.level().isClientSide() || !witch.isAlive()) {
			return new Drink(Math.max(0, cooldownTicks), 0);
		}
		if (witch.hasEffect(MobEffects.INVISIBILITY)) {
			// Пока эффект висит, отсчёт замирает на полной паузе. После спада проверка снова через CHECK_SECONDS.
			return new Drink(Math.max(1, CHECK_SECONDS) * 20, 0);
		}
		if (witch.getTarget() == null) {
			return new Drink(Math.max(0, cooldownTicks), 0);
		}
		if (cooldownTicks < 0) {
			if (isUrgentDrink(witch)) {
				return new Drink(QUEUED, 0);
			}
			return startDrink(witch);
		}
		if (cooldownTicks > 0) {
			return new Drink(cooldownTicks - 1, 0);
		}
		int interval = Math.max(1, CHECK_SECONDS) * 20;
		if (witch.getRandom().nextFloat() >= DRINK_CHANCE) {
			return new Drink(interval, 0);
		}
		if (isUrgentDrink(witch)) {
			return new Drink(QUEUED, 0);
		}
		return startDrink(witch);
	}

	private static Drink startDrink(Witch witch) {
		boolean fresh = !witch.isDrinkingPotion();
		ItemStack stack = potionStack();
		witch.setItemSlot(EquipmentSlot.MAINHAND, stack);
		witch.setUsingItem(true);
		int useTicks = 0;
		if (fresh) {
			if (!witch.isSilent()) {
				witch.level().playSound(
						null,
						witch.getX(),
						witch.getY(),
						witch.getZ(),
						SoundEvents.WITCH_DRINK,
						witch.getSoundSource(),
						1.0f,
						0.8f + witch.getRandom().nextFloat() * 0.4f);
			}
			AttributeInstance speed = witch.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(DRINKING_SLOW_ID);
				speed.addTransientModifier(DRINKING_SLOW);
			}
			useTicks = Math.max(1, stack.getUseDuration(witch));
		}
		return new Drink(Math.max(1, CHECK_SECONDS) * 20, useTicks);
	}

	/**
	 * Глоток воды, огнестойкости или лечения. Его не отменяем:
	 * иначе ведьма тонула бы или горела ради невидимости.
	 * Скорость сюда не входит — этот глоток можно сменить на невидимость.
	 */
	private static boolean isUrgentDrink(Witch witch) {
		if (!witch.isDrinkingPotion()) {
			return false;
		}
		PotionContents contents = witch.getMainHandItem().get(DataComponents.POTION_CONTENTS);
		if (contents == null) {
			return false;
		}
		boolean[] urgent = { false };
		contents.forEachEffect(effect -> {
			Holder<MobEffect> type = effect.getEffect();
			if (type.equals(MobEffects.WATER_BREATHING)
					|| type.equals(MobEffects.FIRE_RESISTANCE)
					|| type.equals(MobEffects.INSTANT_HEALTH)) {
				urgent[0] = true;
			}
		}, 1.0f);
		return urgent[0];
	}

	private static ItemStack potionStack() {
		int ticks = Math.max(1, INVISIBILITY_SECONDS) * 20;
		ItemStack stack = new ItemStack(Items.POTION);
		stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
				Optional.empty(),
				Optional.of(MobEffects.INVISIBILITY.value().getColor()),
				List.of(new MobEffectInstance(
						MobEffects.INVISIBILITY,
						ticks,
						0,
						false,
						SHOW_PARTICLES,
						false)),
				Optional.empty()));
		return stack;
	}

	/** Пауза до проверки и длина нового глотка. 0 у длины — бутылку уже пьют, таймер глотка не трогать. */
	public record Drink(int cooldownTicks, int useTicks) {
	}
}
