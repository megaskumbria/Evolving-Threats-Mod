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
 * Witches can drink Invisibility in combat.
 * Drinking works like vanilla potion use: bottle in hand, slow down, then effect.
 * While drinking she does not throw potions. After drinking she is invisible but throws and sounds remain.
 * Water breathing, fire resistance, and healing drinks are not interrupted.
 */
public final class WitchInvisibility {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — witch no longer drinks invisibility. Active effect runs out on its own. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) to drink when a check runs. 0.10 = 10%.
	 * Was 0.35 — she drank almost every fight. Checks are not every tick: see {@link #CHECK_SECONDS}.
	 * 0 = never. 1 = drink on every check.
	 */
	public static final float DRINK_CHANCE = 0.10f;

	/**
	 * How often to roll in combat, seconds. 15 = every fifteen seconds while she has a target.
	 * Was 6: with high chance, invisibility lasted most of the fight.
	 * No target or while already invisible — timer does not advance.
	 * First check runs as soon as she acquires a target.
	 * 0 is treated as 1 or the roll would fire every tick.
	 */
	public static final int CHECK_SECONDS = 15;

	/**
	 * Invisibility duration after drinking, seconds. 20 = twenty seconds.
	 * Normal invisibility potion lasts 3 minutes (180). Shorter here so fights are not
	 * endless hide-and-seek. Use 180 for full bottle length.
	 * 0 = never drinks.
	 */
	public static final int INVISIBILITY_SECONDS = 20;

	/**
	 * Particles around the witch while invisible.
	 * false — no puff (harder to spot). Thrown bottles and sounds still visible/audible.
	 * true — particles like normal potion, easier to track.
	 */
	public static final boolean SHOW_PARTICLES = false;

	// ========================================================================

	/**
	 * Same slow modifier vanilla applies while any witch drinks.
	 * Shared id with vanilla: game removes it when drinking ends.
	 */
	private static final Identifier DRINKING_SLOW_ID = Identifier.withDefaultNamespace("drinking");
	private static final AttributeModifier DRINKING_SLOW = new AttributeModifier(
			DRINKING_SLOW_ID, -0.25, AttributeModifier.Operation.ADD_VALUE);

	/** Negative cooldown means roll succeeded, waiting for a free drink window. */
	private static final int QUEUED = -1;

	private WitchInvisibility() {
	}

	/**
	 * Start of witch tick, before she picks water, fire resist, heal, or speed.
	 * {@code cooldownTicks} — ticks until next check; return the same in the witch field.
	 */
	public static Drink tick(Witch witch, int cooldownTicks) {
		if (!ENABLED || DRINK_CHANCE <= 0.0f || INVISIBILITY_SECONDS <= 0
				|| witch.level().isClientSide() || !witch.isAlive()) {
			return new Drink(Math.max(0, cooldownTicks), 0);
		}
		if (witch.hasEffect(MobEffects.INVISIBILITY)) {
			// While invisible, timer holds at full interval. After fade, next check in CHECK_SECONDS.
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
	 * Water, fire resistance, or instant health drink — do not cancel:
	 * otherwise she would drown or burn for invisibility.
	 * Speed is not urgent — can swap to invisibility.
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

	/** Cooldown until next check and length of a new drink. useTicks 0 — already drinking, do not reset drink timer. */
	public record Drink(int cooldownTicks, int useTicks) {
	}
}
