package com.example.skeleton;

import com.example.entity.FastBowSkeletonEntity;
import com.example.mixin.RangedBowAttackGoalAccessor;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.Items;

/**
 * Скелет с дальним быстрым луком. Обычный видит игрока на 16 блоков и стреляет на 15.
 * Этот замечает дальше и выпускает стрелу чаще, уже на полном натяжении.
 */
public final class FastBowSkeleton {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — такие скелеты не появляются, уже живущие стреляют как обычно. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что скелет или странник с луком будет стрелять дальше и быстрее.
	 * 0.15 = 15%. Бросается после оружия: если лука нет (два меча), шанс не тратится.
	 * 0 = таких скелетов не будет.
	 */
	public static final float SPAWN_CHANCE = 0.15f;

	/**
	 * Дальность, на которой он останавливается и стреляет, в блоках.
	 * У обычного скелета 15. 30 = в два раза дальше. Больше = начинает обстрел издалека.
	 */
	public static final float ATTACK_RANGE_BLOCKS = 30.0f;

	/**
	 * Как далеко замечает игрока и не теряет его, в блоках.
	 * У обычного скелета 16. Должно быть не меньше дальности лука, иначе он не видит цель, до которой мог бы дострелить.
	 * 36 = чуть дальше лука на 30.
	 */
	public static final double FOLLOW_RANGE = 36.0;

	/**
	 * Сколько тиков (20 = 1 секунда) он натягивает лук перед выстрелом.
	 * У обычного скелета 20. 10 = полсекунды. Стрела всё равно вылетает на полной силе, а не ослабленной.
	 */
	public static final int DRAW_TICKS = 10;

	/**
	 * Пауза после выстрела на сложной сложности, в тиках. У обычного скелета 20 (1 секунда).
	 * 10 = полсекунды. Вместе с натяжением в 10 тиков выстрел примерно раз в секунду вместо раза в две.
	 */
	public static final int HARD_PAUSE_TICKS = 10;

	/**
	 * Пауза после выстрела на лёгкой и нормальной сложности, в тиках.
	 * У обычного скелета 40 (2 секунды). 20 = 1 секунда.
	 */
	public static final int OTHER_PAUSE_TICKS = 20;

	/**
	 * Скорость стрелы. У обычного скелета 1.6. 2.4 = в полтора раза быстрее,
	 * чтобы стрела долетала до новой дальности, а не падала по пути.
	 * Прицел подстраивается сам: быстрее стрела — меньше задирается вверх.
	 * Иначе она проходит над головой.
	 */
	public static final float ARROW_SPEED = 2.4f;

	/** Скорость стрелы у обычного скелета. От неё считается прицел. */
	private static final float VANILLA_ARROW_SPEED = 1.6f;

	/** Насколько обычный скелет задирает стрелу вверх. Подобрано игрой под скорость 1.6. */
	private static final float VANILLA_ARC = 0.2f;

	// ========================================================================

	private FastBowSkeleton() {
	}

	/**
	 * Насколько задирать стрелу. Не отдельная настройка: считается из {@link #ARROW_SPEED}.
	 * При 1.6 получается 0.2, как у обычного скелета. При 2.4 — ниже, чтобы попасть в тело, а не над головой.
	 */
	public static float arrowArc() {
		if (ARROW_SPEED <= 0.01f) {
			return VANILLA_ARC;
		}
		return (float) (VANILLA_ARC * Math.pow(VANILLA_ARROW_SPEED / ARROW_SPEED, 1.25));
	}

	/** Вызвать при появлении, когда оружие уже выдано. */
	public static void tryRoll(AbstractSkeleton skeleton) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f) {
			return;
		}
		if (!skeleton.isHolding(Items.BOW)) {
			return;
		}
		if (!(skeleton instanceof FastBowSkeletonEntity data)) {
			return;
		}
		if (skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		data.evolvingthreats$setFastBow(true);
	}

	/** Повесить дальность и паузу. Вызывать после того, как игра заново выбрала «стрелять из лука». */
	public static void apply(AbstractSkeleton skeleton, RangedBowAttackGoal<?> bowGoal) {
		if (!(skeleton instanceof FastBowSkeletonEntity data) || !data.evolvingthreats$isFastBow()) {
			return;
		}
		var follow = skeleton.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(FOLLOW_RANGE);
		}
		int pause = skeleton.level().getDifficulty() == Difficulty.HARD ? HARD_PAUSE_TICKS : OTHER_PAUSE_TICKS;
		bowGoal.setMinAttackInterval(pause);
		((RangedBowAttackGoalAccessor) bowGoal).evolvingthreats$setAttackRadiusSqr(ATTACK_RANGE_BLOCKS * ATTACK_RANGE_BLOCKS);
	}
}
