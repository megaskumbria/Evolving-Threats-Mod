package com.example;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

/**
 * Случайное здоровье при появлении. Число целое, оба края входят: 20 и 40 — тоже возможны.
 * Гнилой надсмотрщик потом ставит свои 50. Паучиха-матка — свои 40. Якорь рейда — свои 35.
 */
public final class MobHealth {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — у всех остаётся ванильное здоровье. */
	public static final boolean ENABLED = true;

	/**
	 * Зомби, кадавр, зомби-житель, утопленник, скелет и странник.
	 * Сейчас у них 20. 20 и 40 = от 20 до 40 жизней (от 10 до 20 сердец).
	 */
	public static final int ZOMBIE_SKELETON_MIN = 20;
	public static final int ZOMBIE_SKELETON_MAX = 40;

	/**
	 * Паук и пещерный паук. У обычного паука сейчас 16, у пещерного 12.
	 * 16 и 30 = оба начинают минимум с 16.
	 */
	public static final int SPIDER_MIN = 16;
	public static final int SPIDER_MAX = 30;

	/** Крипер, в том числе подрывник, метатель и ломатель щита. Сейчас 20. */
	public static final int CREEPER_MIN = 20;
	public static final int CREEPER_MAX = 30;

	/** Эндермен. Сейчас 40. */
	public static final int ENDERMAN_MIN = 40;
	public static final int ENDERMAN_MAX = 60;

	// ========================================================================

	private MobHealth() {
	}

	/** Один раз при появлении. При загрузке мира заново не бросается: здоровье уже записано. */
	public static void tryRoll(Mob mob) {
		if (!ENABLED || !(mob.level() instanceof ServerLevel)) {
			return;
		}
		if (mob instanceof ZombifiedPiglin) {
			return;
		}
		int min;
		int max;
		if (mob instanceof Zombie || mob instanceof Skeleton || mob instanceof Stray) {
			min = ZOMBIE_SKELETON_MIN;
			max = ZOMBIE_SKELETON_MAX;
		} else if (mob instanceof Spider) {
			min = SPIDER_MIN;
			max = SPIDER_MAX;
		} else if (mob instanceof Creeper) {
			min = CREEPER_MIN;
			max = CREEPER_MAX;
		} else if (mob instanceof Enderman) {
			min = ENDERMAN_MIN;
			max = ENDERMAN_MAX;
		} else {
			return;
		}
		int low = Math.min(min, max);
		int high = Math.max(min, max);
		int health = low + mob.getRandom().nextInt(high - low + 1);
		var maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		maxHealth.setBaseValue(health);
		mob.setHealth(health);
	}
}
