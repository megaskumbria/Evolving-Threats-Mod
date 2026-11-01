package com.example.spider;

import com.example.entity.LethalPoisonEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import org.jetbrains.annotations.Nullable;

/**
 * Cave spider bites can kill again, like beta 1.8.
 * The mod does not change poison duration — vanilla sets it on the bite:
 * easy — no poison, normal — Poison I for 7 seconds, hard — 15 seconds.
 * Each tick removes half a heart (1 HP) every 1.25 seconds. Armor does not reduce this damage.
 * Unlike modern vanilla, the final tick can bring health to zero.
 * Witch, slime, and potion poison do not behave this way: their source is not a cave spider.
 */
public final class CaveSpiderPoison {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * true — poison from a cave spider can kill.
	 * false — like vanilla: poison stops at half a heart.
	 * Bite duration is not changed by this switch.
	 */
	public static final boolean ENABLED = true;

	// ========================================================================

	/** HP removed per poison tick in vanilla. 1 = half a heart. Armor does not help. */
	private static final float TICK_DAMAGE = 1.0f;

	private CaveSpiderPoison() {
	}

	/**
	 * Poison was just applied or refreshed.
	 * If the new effect is weaker than what is already active, the tag is left alone:
	 * older cave-spider poison stays lethal.
	 */
	public static void onPoisonApplied(LivingEntity target, MobEffectInstance effect, @Nullable Entity source, boolean applied) {
		if (!applied || !effect.getEffect().equals(MobEffects.POISON)) {
			return;
		}
		if (!(target instanceof LethalPoisonEntity data)) {
			return;
		}
		data.evolvingthreats$setLethalCavePoison(ENABLED && source instanceof CaveSpider);
	}

	/**
	 * Commands and beacons can replace poison without the normal apply path.
	 * If replacement did not happen, the old poison tag remains.
	 */
	public static void onPoisonForced(LivingEntity target, MobEffectInstance effect, @Nullable Entity source) {
		if (!effect.getEffect().equals(MobEffects.POISON) || target.getEffect(MobEffects.POISON) != effect) {
			return;
		}
		onPoisonApplied(target, effect, source, true);
	}

	/**
	 * Poison tick. While health is above half a heart, vanilla handles damage.
	 * At half a heart and below, only cave-spider poison finishes the kill.
	 */
	public static void onPoisonTick(ServerLevel level, LivingEntity target) {
		if (!ENABLED || target.getHealth() > 1.0f) {
			return;
		}
		if (!(target instanceof LethalPoisonEntity data) || !data.evolvingthreats$isLethalCavePoison()) {
			return;
		}
		target.hurtServer(level, target.damageSources().magic(), TICK_DAMAGE);
	}
}
