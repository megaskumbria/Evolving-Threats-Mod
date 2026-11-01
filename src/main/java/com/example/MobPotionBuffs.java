package com.example;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import com.example.tool.FloatingTool;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;

import java.util.ArrayList;
import java.util.List;

/**
 Mob can appear immediately under several useful potions.
 The effect does not end while the mob is alive, and sprinkles particles, like a player with a potion.
 *
 In the game 25 useful potions, but the long version with an infinite effect is no different
 From the usual: time does not go by. So here are 10 different effects.
 There is no cure: it is an instant potion, and if you make it endless, the mob is healed every moment.
 */
public final class MobPotionBuffs {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - Mobs appear without these potions. */
	public static final boolean ENABLED = true;

	/**
	 How many effects on day 100 (end of threat scale) Index 0 = none, 1 = one, ... 5 = five.
	 Numbers - weight (a chance relative to the rest). {50, 24, 13, 7, 4, 2} in the sum of 100:
	 * without the potion 50%, one 24%, two 13%, three 7%, four 4%, five 2%.
	 ThreatTable.POTION COUNT WEIGHTS START: almost all have no potions
	 * and smoothly arrive at these values along with the scale and the moon.
	 If you want the five effects to almost never occur, reduce the last number.
	 Do not add more than five: there are only 10 different effects on the moba.
	 */
	public static final int[] COUNT_WEIGHTS = { 50, 24, 13, 7, 4, 2 };

	/**
	 Chance (0.0-1.0) that the effect will be level II if it has a strong potion.
	 0.33 ≈ 33%, that is, about one of three: ordinary, long and strong, and long is the same as usual.
	 * Refers only to jump, speed, regeneration and strength. The others don't have a strong version.
	 * 0 = always level I. 1 = always II, where it is.
	 */
	public static final float STRONG_LEVEL_CHANCE = 0.33f;

	/**
	 How many seconds does the effect of the cryper fog hold on who entered it?
	 The fog itself lives 15 seconds, like a regular cryper: it does the game, not this number.
	 45 = as in an explosive potion of normal length (a quarter of three minutes).
	 On a live cryper, the effect still does not end. Only the fog changes after the explosion.
	 */
	public static final int CLOUD_EFFECT_SECONDS = 45;

	/**
	 The game multiplies the effect time in the cryper fog by this number. 0.25 = a quarter.
	 * Therefore, the fog is recorded 4 times more, and the player remains {@link #CLOUD EFFECT SECONDS}.
	 */
	private static final float VANILLA_CREEPER_CLOUD_SCALE = 0.25f;

	// ========================================================================

	/**
	 Useful effects. The second flag is whether there is a strong potion (level II).
	 Night vision and luck do not help the mob in combat, but remain on the list:
	 These are also useful potions, and particles from them are visible.
	 Underwater breath does not allow zombies to drown and turn into a drowned person.
	 */
	private static final List<Buff> BUFFS = List.of(
			new Buff(MobEffects.NIGHT_VISION, false),
			new Buff(MobEffects.INVISIBILITY, false),
			new Buff(MobEffects.JUMP_BOOST, true),
			new Buff(MobEffects.FIRE_RESISTANCE, false),
			new Buff(MobEffects.SPEED, true),
			new Buff(MobEffects.WATER_BREATHING, false),
			new Buff(MobEffects.REGENERATION, true),
			new Buff(MobEffects.STRENGTH, true),
			new Buff(MobEffects.LUCK, false),
			new Buff(MobEffects.SLOW_FALLING, false)
	);

	private MobPotionBuffs() {
	}

	/**
	 Creeper, Enderman, Blaze, Phantom, Slug and Magma Cube, Guardian and Ancient Guardian,
	 * shalker, piglin and brut, hoglin and zoglin.
	 The ancient guard is also a guard, so there is no separate check.
	 Some of them do not have their appearance, so the call comes from a common mob spawn.
	 */
	public static void tryApplyExtra(Mob mob) {
		if (mob instanceof Creeper
				|| mob instanceof Enderman
				|| mob instanceof Blaze
				|| mob instanceof Phantom
				|| mob instanceof Slime
				|| mob instanceof MagmaCube
				|| mob instanceof Guardian
				|| mob instanceof Shulker
				|| mob instanceof Piglin
				|| mob instanceof PiglinBrute
				|| mob instanceof Hoglin
				|| mob instanceof Zoglin) {
			tryApply(mob);
		}
	}

	/*Call once when the mob first appeared. Re-loading the world is not called. */
	public static void tryApply(Mob mob) {
		if (!ENABLED || !(mob.level() instanceof ServerLevel)) {
			return;
		}
		int count = rollCount(mob);
		if (count <= 0) {
			return;
		}
		List<Buff> pool = new ArrayList<>(BUFFS);
		// Kreeper and a hovering instrument without invisibility.
		// Creeper is easy to miss, and the tool has no body: the potion hides it entirely.
		// The rest of the effects on the list still fall out. Lifting the ban is removing that check.
		if (mob instanceof Creeper || mob instanceof FloatingTool) {
			pool.removeIf(buff -> buff.effect == MobEffects.INVISIBILITY);
		}
		RandomSource random = mob.getRandom();
		for (int i = 0; i < count && !pool.isEmpty(); i++) {
			Buff buff = pool.remove(random.nextInt(pool.size()));
			int amplifier = buff.canBeStrong && random.nextFloat() < STRONG_LEVEL_CHANCE ? 1 : 0;
			// ambient = false - ordinary particles, as in a drunken potion, and not dull from a lighthouse.
			mob.addEffect(new MobEffectInstance(
					buff.effect,
					MobEffectInstance.INFINITE_DURATION,
					amplifier,
					false,
					true,
					true));
		}
	}

	/**
	 The fog after the cryper explosion copies potions from the mob. Infinite time would remain that way.
	 Infinite: The game does not shorten it. Here it becomes finite.
	 Ordinary potion with its time (cripper doused from a bottle) do not touch.
	 */
	public static MobEffectInstance forDeathCloud(MobEffectInstance effect) {
		if (effect == null || !effect.isInfiniteDuration()) {
			return effect;
		}
		int seconds = Math.max(1, CLOUD_EFFECT_SECONDS);
		int storedTicks = Math.max(1, Math.round(seconds * 20.0f / VANILLA_CREEPER_CLOUD_SCALE));
		return new MobEffectInstance(
				effect.getEffect(),
				storedTicks,
				effect.getAmplifier(),
				effect.isAmbient(),
				effect.isVisible(),
				effect.showIcon());
	}

	/** 0 = no effect. The more right the index in {@link #COUNT WEIGHTS}, the more effects. */
	private static int rollCount(Mob mob) {
		// GROWING: At the beginning of the game, almost everyone has zero potions, the weights smoothly reach COUNT WEIGHTS by day 100.
		return ThreatScale.pickWeighted(mob.level(), mob.getRandom(), ThreatTable.POTION_COUNT_WEIGHTS_START, COUNT_WEIGHTS);
	}

	private record Buff(Holder<MobEffect> effect, boolean canBeStrong) {
	}
}
