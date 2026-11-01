package com.example;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

/**
 * Accidental health upon appearance. The number is integer, both edges are included: 20 and 40 are also possible.
 The rotten overseer then puts his 100. Spider womb is 40. The anchor of the raid is 100.
 Basilisk then puts his 100 (BasiliskSkeleton.MAX HEALTH)
 Bastion then bets his 1000 (Bastion.MAX HEALTH)
 The 700 IQ skeleton then puts its 50 (TacticianSkeleton.HEALTH MIN and HEALTH MAX).
 Excalibur then puts its 50 (ZombieExcalibur.MAX HEALTH).
 The ritual pack then puts its 50 (RitualBand.MAX HEALTH).
 */
public final class MobHealth {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False: Everyone has vanilla health. */
	public static final boolean ENABLED = true;

	/**
	 Zombie, husk, zombie villager, drowned, skeleton and wanderer.
	 * They have 20 now. 20 and 40 = 20 to 40 lives (10 to 20 hearts).
	 MAX is the ceiling on day 100: at the beginning of the game it is lower and grows with the threat scale.
	 * (Starting value in ThreatTable.ZOMBIE SKELETON HEALTH MAX START)
	 */
	public static final int ZOMBIE_SKELETON_MIN = 20;
	public static final int ZOMBIE_SKELETON_MAX = 40;

	/**
	 Spider and cave spider. The average spider is now 16, the cave 12.
	 * 16 and 30 = both start at least 16.
	 */
	public static final int SPIDER_MIN = 16;
	public static final int SPIDER_MAX = 30;

	/** Creeper, including bomber, thrower and shield breaker. It's 20. */
	public static final int CREEPER_MIN = 20;
	public static final int CREEPER_MAX = 30;

	// Enderman doesn't come in here: he's always 40, like in a normal game.

	// ========================================================================

	private MobHealth() {
	}

	/** Once upon appearance. When downloading the world again does not throw: health is already recorded. */
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
			// GROWING: The health ceiling rises from ThreatTable.ZOMBIE SKELETON HEALTH MAX START to MAX by day 100.
			min = ZOMBIE_SKELETON_MIN;
			max = Math.round(ThreatScale.lerp(mob.level(), (float) ThreatTable.ZOMBIE_SKELETON_HEALTH_MAX_START,
					(float) ZOMBIE_SKELETON_MAX));
		} else if (mob instanceof Spider) {
			min = SPIDER_MIN;
			max = SPIDER_MAX;
		} else if (mob instanceof Creeper) {
			min = CREEPER_MIN;
			max = CREEPER_MAX;
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
