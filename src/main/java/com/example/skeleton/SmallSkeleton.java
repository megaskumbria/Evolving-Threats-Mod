package com.example.skeleton;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;

/**
 * Редкий маленький скелет: ниже ростом, быстрее, с меньшим здоровьем.
 * Лук у него обычный. Дальний лук, тотем и два оружия ближнего боя остаются взрослым.
 * На солнце горит так же, как обычный скелет: отдельного правила для этого нет.
 *
 * Рост — атрибут SCALE. Игра сама уменьшает и модель, и зону, по которой в него попадают.
 * Здоровье ставится здесь уже после общего броска 20–40, поэтому у маленького остаётся свой диапазон.
 */
public final class SmallSkeleton {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — маленькие скелеты не появляются. Уже живущие остаются маленькими. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный скелет или странник появится маленьким.
	 * 0.10 = 10%. 0 = таких скелетов не будет.
	 */
	public static final float SPAWN_CHANCE = 0.10f;

	/**
	 * Рост: 1.0 = как обычный скелет, 0.5 = вдвое меньше (как детёныш в игре).
	 * Меньше 0.5 — в него совсем трудно попасть. Больше 0.7 — уже почти взрослый.
	 */
	public static final double SCALE = 0.5;

	/**
	 * Во сколько раз быстрее обычного скелета. 1.5 = на 50% быстрее, как детёныш зомби.
	 * 1.0 = та же скорость, что у взрослого. 2.0 = вдвое быстрее.
	 */
	public static final double SPEED_MULTIPLIER = 1.5;

	/**
	 * Здоровье маленького скелета, оба края входят.
	 * У взрослого в этом моде 20–40. 10 и 16 = от 5 до 8 сердец.
	 */
	public static final int HEALTH_MIN = 10;
	public static final int HEALTH_MAX = 16;

	// ========================================================================

	/** Скорость обычного скелета в игре. Множитель считается от неё. */
	private static final double VANILLA_MOVEMENT_SPEED = 0.25;

	private SmallSkeleton() {
	}

	/**
	 * Бросить шанс и, если выпало, уменьшить скелета.
	 * true — этот скелет маленький: дальний лук, тотем и два оружия ему не выдавать.
	 */
	public static boolean tryRoll(AbstractSkeleton skeleton) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f || !(skeleton.level() instanceof ServerLevel)) {
			return false;
		}
		if (skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		apply(skeleton);
		return true;
	}

	private static void apply(AbstractSkeleton skeleton) {
		var scale = skeleton.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(SCALE);
		}
		var speed = skeleton.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(VANILLA_MOVEMENT_SPEED * SPEED_MULTIPLIER);
		}
		int low = Math.min(HEALTH_MIN, HEALTH_MAX);
		int high = Math.max(HEALTH_MIN, HEALTH_MAX);
		int health = low + skeleton.getRandom().nextInt(high - low + 1);
		var maxHealth = skeleton.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(health);
			skeleton.setHealth(health);
		}
	}
}
