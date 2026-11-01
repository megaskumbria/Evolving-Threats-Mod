package com.example.witch;

import com.example.entity.NullifierWitchEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 A witch's throw at a player.
 Part of the bottles - new effects. Some of the usual and new remains in the air fog.
 Ally treatment in the raid and white potion nullifier do not fall here.
 As long as the witch drinks herself, there is no throw: the game does not make it.
 */
public final class WitchThrownPotions {

	// ===================== SETTINGS (change these numbers) =====================

	/** False - the witch throws only poison, slowing down, weakness and instant damage. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a throw at a player will be from a new list rather than a regular one.
	 0.50 = 50%, approximately one in two. 0 = no new effects. 1 = ordinary players do not fly.
	 */
	public static final float SPECIAL_CHANCE = 0.50f;

	/**
	 * Chance (0.0–1.0) that the bottle is foggy: a cloud remains at the place of impact.
	 * 0.35 = 35%. It concerns both new effects and ordinary poison, slowdown, weakness and damage.
	 * 0 = explosive spray only. 1 = each throw at the player leaves a fog.
	 White potion nullifier always explosive: fog effects do not remove.
	 */
	public static final float LINGERING_CHANCE = 0.35f;

	/**
	 Slowdown IV, seconds. Random number from minimum to maximum inclusive.
	 * 15 and 20 = 15 to 20 seconds. IV hardly lets you walk.
	 * 0 at maximum turns off this potion. Force: 3 = IV, 0 would be a normal deceleration.
	 */
	public static final int SLOWNESS_MIN_SECONDS = 15;
	public static final int SLOWNESS_MAX_SECONDS = 20;
	public static final int SLOWNESS_AMPLIFIER = 3;

	/** Blind, seconds. 5 and 10 = 5 to 10. The screen gets dark, the witch can't see well. */
	public static final int BLINDNESS_MIN_SECONDS = 5;
	public static final int BLINDNESS_MAX_SECONDS = 10;

	/**
	 Drying I, seconds. 10 = ten seconds.
	 Removes 1 health about once every 2 seconds, in 10 seconds it is about 2 hearts of black damage.
	 1 = Drying II, ticking twice as often. 0 = no drying.
	 */
	public static final int WITHER_SECONDS = 10;
	public static final int WITHER_AMPLIFIER = 0;

	/**
	 Hunger I, seconds. 20 and 30 = 20 to 30.
	 * For the fight noticeably puts satiety. Not minutes: the witch throws often, long hunger would not let go.
	 */
	public static final int HUNGER_MIN_SECONDS = 20;
	public static final int HUNGER_MAX_SECONDS = 30;

	/** Nausea, seconds. 8 and 12 = the screen does not last long. There's almost no damage. */
	public static final int NAUSEA_MIN_SECONDS = 8;
	public static final int NAUSEA_MAX_SECONDS = 12;

	/**
	 Fatigue I, seconds. 15 and 20 = dig about three times slower.
	 1 in strength = Fatigue II, the blocks hardly break. 2 = as the ray of the ancient guard.
	 */
	public static final int FATIGUE_MIN_SECONDS = 15;
	public static final int FATIGUE_MAX_SECONDS = 20;
	public static final int FATIGUE_AMPLIFIER = 0;

	/**
	 Levitation, seconds. 4 and 6 = short rise and fall.
	 8 seconds is lifted by about 8 blocks, like a long arrow of the skeleton. It's shorter.
	 */
	public static final int LEVITATION_MIN_SECONDS = 4;
	public static final int LEVITATION_MAX_SECONDS = 6;

	/**
	 How much shorter is the effect of a foggy bottle with an ordinary potion (poison, slowdown, weakness).
	 0.25 = a quarter, like a foggy potion in a normal game.
	 New effects this multiplier does not cut: they have seconds on top.
	 */
	public static final float VANILLA_LINGERING_SCALE = 0.25f;

	// ========================================================================

	private WitchThrownPotions() {
	}

	/**
	 Replacement of the bottle at the time of throwing.
	 If there is nothing to change, the one that the witch has already chosen leaves.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Projectile launch(
			Projectile.ProjectileFactory creator,
			ServerLevel level,
			ItemStack stack,
			LivingEntity source,
			double x,
			double y,
			double z,
			float power,
			float uncertainty) {
		Choice choice = choose(source, stack);
		if (choice == null) {
			return Projectile.spawnProjectileUsingShoot(creator, level, stack, source, x, y, z, power, uncertainty);
		}
		Projectile.ProjectileFactory factory = choice.lingering()
				? (Projectile.ProjectileFactory) ThrownLingeringPotion::new
				: ThrownSplashPotion::new;
		return Projectile.spawnProjectileUsingShoot(factory, level, choice.stack(), source, x, y, z, power, uncertainty);
	}

	private static Choice choose(LivingEntity source, ItemStack vanillaStack) {
		if (!ENABLED || !(source instanceof Witch witch) || !(witch.getTarget() instanceof Player)) {
			return null;
		}
		// The white bottle is already assigned. She'll change her code, she doesn't need fog.
		if (witch instanceof NullifierWitchEntity data && data.evolvingthreats$isNullifyArmed()) {
			return null;
		}
		RandomSource random = witch.getRandom();
		boolean lingering = LINGERING_CHANCE > 0.0f && random.nextFloat() < LINGERING_CHANCE;
		boolean special = SPECIAL_CHANCE > 0.0f && random.nextFloat() < SPECIAL_CHANCE;
		if (special) {
			ItemStack stack = specialStack(random, lingering);
			if (stack != null) {
				return new Choice(stack, lingering);
			}
		}
		if (!lingering) {
			return null;
		}
		return new Choice(lingeringVanilla(vanillaStack), true);
	}

	private static ItemStack specialStack(RandomSource random, boolean lingering) {
		List<Brew> brews = brews();
		if (brews.isEmpty()) {
			return null;
		}
		Brew brew = brews.get(random.nextInt(brews.size()));
		int seconds = rollSeconds(random, brew.minSeconds(), brew.maxSeconds());
		ItemStack stack = new ItemStack(lingering ? Items.LINGERING_POTION : Items.SPLASH_POTION);
		stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
				Optional.empty(),
				Optional.of(brew.effect().value().getColor()),
				List.of(new MobEffectInstance(
						brew.effect(),
						Math.max(1, seconds) * 20,
						Math.max(0, brew.amplifier()),
						false,
						true,
						true)),
				Optional.empty()));
		return stack;
	}

	/** Ordinary poison, slowdown, weakness and damage, but the bottle is foggy. */
	private static ItemStack lingeringVanilla(ItemStack splash) {
		ItemStack stack = new ItemStack(Items.LINGERING_POTION);
		PotionContents contents = splash.get(DataComponents.POTION_CONTENTS);
		if (contents != null) {
			stack.set(DataComponents.POTION_CONTENTS, contents);
		}
		stack.set(DataComponents.POTION_DURATION_SCALE, VANILLA_LINGERING_SCALE);
		return stack;
	}

	private static List<Brew> brews() {
		List<Brew> list = new ArrayList<>();
		add(list, MobEffects.SLOWNESS, SLOWNESS_MIN_SECONDS, SLOWNESS_MAX_SECONDS, SLOWNESS_AMPLIFIER);
		add(list, MobEffects.BLINDNESS, BLINDNESS_MIN_SECONDS, BLINDNESS_MAX_SECONDS, 0);
		add(list, MobEffects.WITHER, WITHER_SECONDS, WITHER_SECONDS, WITHER_AMPLIFIER);
		add(list, MobEffects.HUNGER, HUNGER_MIN_SECONDS, HUNGER_MAX_SECONDS, 0);
		add(list, MobEffects.NAUSEA, NAUSEA_MIN_SECONDS, NAUSEA_MAX_SECONDS, 0);
		add(list, MobEffects.MINING_FATIGUE, FATIGUE_MIN_SECONDS, FATIGUE_MAX_SECONDS, FATIGUE_AMPLIFIER);
		add(list, MobEffects.LEVITATION, LEVITATION_MIN_SECONDS, LEVITATION_MAX_SECONDS, 0);
		return list;
	}

	/** A maximum of 0 or less – this potion is not on the list. */
	private static void add(List<Brew> list, Holder<MobEffect> effect, int minSeconds, int maxSeconds, int amplifier) {
		if (Math.max(minSeconds, maxSeconds) <= 0) {
			return;
		}
		list.add(new Brew(effect, minSeconds, maxSeconds, amplifier));
	}

	private static int rollSeconds(RandomSource random, int minSeconds, int maxSeconds) {
		int low = Math.min(minSeconds, maxSeconds);
		int high = Math.max(minSeconds, maxSeconds);
		if (high <= low) {
			return Math.max(1, low);
		}
		return low + random.nextInt(high - low + 1);
	}

	private record Brew(Holder<MobEffect> effect, int minSeconds, int maxSeconds, int amplifier) {
	}

	private record Choice(ItemStack stack, boolean lingering) {
	}
}
