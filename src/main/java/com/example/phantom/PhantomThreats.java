package com.example.phantom;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;

/**
 * Фантомы кусают больнее, чем в обычной игре, а стая растёт, если долго не спать.
 * Первые ночи бессонницы — ванильный размер группы. Дальше к ней прибавляются фантомы.
 */
public final class PhantomThreats {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — укус и размер стаи как в обычной игре. */
	public static final boolean ENABLED = true;

	/**
	 * Сколько здоровья снимает укус на лёгкой сложности, без брони.
	 * 3 = 1,5 сердца. В обычной игре 2 (1 сердце).
	 */
	public static final float EASY_BITE_HP = 3.0f;

	/**
	 * Сколько здоровья снимает укус на нормальной сложности, без брони.
	 * 6 = 3 сердца. Так фантом бил, когда его только добавили. В обычной игре сейчас 2.
	 */
	public static final float NORMAL_BITE_HP = 6.0f;

	/**
	 * Сколько здоровья снимает укус на сложной сложности, без брони.
	 * 9 = 4,5 сердца. В обычной игре 3 (1,5 сердца).
	 */
	public static final float HARD_BITE_HP = 9.0f;

	/**
	 * Сколько игровых дней без кровати стая ещё обычного размера.
	 * 3 = первые ночи, когда фантомы только начинают появляться.
	 * Меньше 3 не отключает сам спавн: игру по-прежнему нужно не спать три дня.
	 */
	public static final int CALM_DAYS = 3;

	/**
	 * За сколько лишних дней без сна в стаю добавляется один фантом.
	 * 2 = на 5-й день +1, на 7-й +2, на 9-й +3. 1 = плюс фантом каждый день после третьего.
	 */
	public static final int DAYS_PER_EXTRA_PHANTOM = 2;

	/**
	 * Потолок стаи за одну волну. Волна приходит раз в 1–2 минуты, пока игрок не ляжет спать.
	 * Обычная игра без этого потолка: лёгкая 1–2, нормальная 1–3, сложная 1–4.
	 * Если поставить потолок ниже обычного максимума, стая станет меньше ванильной.
	 */
	public static final int EASY_FLOCK_CAP = 3;
	public static final int NORMAL_FLOCK_CAP = 5;
	public static final int HARD_FLOCK_CAP = 6;

	// ========================================================================

	/** Укус обычного фантома до пересчёта сложности. От него отделяется прибавка зелья силы. */
	private static final float VANILLA_BITE_HP = 2.0f;

	/** Один игровой день в тиках. 24000 = 20 минут. */
	private static final int TICKS_PER_DAY = 24000;

	private PhantomThreats() {
	}

	/**
	 * Урон, который нужно передать в удар, чтобы игрок потерял ровно число из настроек.
	 * Игра сама уменьшает урон мобов на лёгкой и умножает на 1,5 на сложной.
	 * Зелье силы остаётся сверху и проходит через тот же пересчёт.
	 */
	public static float biteDamage(Phantom phantom, float incoming) {
		if (!ENABLED) {
			return incoming;
		}
		Difficulty difficulty = phantom.level().getDifficulty();
		float felt = biteFor(difficulty);
		float strengthBonus = Math.max(0.0f, incoming - VANILLA_BITE_HP);
		float raw = felt + strengthBonus;
		if (!(phantom.getTarget() instanceof Player)) {
			return raw;
		}
		return beforeDifficultyScale(difficulty, raw);
	}

	/**
	 * Сколько фантомов выпустить в эту волну.
	 * {@code vanillaCount} — сколько выбрала обычная игра (на сложной от 1 до 4).
	 * Сверху прибавка за бессонницу, потом потолок.
	 */
	public static int groupSize(ServerPlayer player, Difficulty difficulty, int vanillaCount) {
		if (!ENABLED) {
			return vanillaCount;
		}
		int ticksAwake = Math.max(0, player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)));
		int daysAwake = ticksAwake / TICKS_PER_DAY;
		int extraDays = Math.max(0, daysAwake - CALM_DAYS);
		int step = Math.max(1, DAYS_PER_EXTRA_PHANTOM);
		int bonus = extraDays / step;
		int cap = switch (difficulty) {
			case EASY -> EASY_FLOCK_CAP;
			case NORMAL -> NORMAL_FLOCK_CAP;
			case HARD -> HARD_FLOCK_CAP;
			case PEACEFUL -> vanillaCount;
		};
		return Math.min(cap, vanillaCount + bonus);
	}

	private static float biteFor(Difficulty difficulty) {
		return switch (difficulty) {
			case EASY -> EASY_BITE_HP;
			case HARD -> HARD_BITE_HP;
			default -> NORMAL_BITE_HP;
		};
	}

	/**
	 * Обратный пересчёт сложности, чтобы после него осталось {@code feltHp}.
	 * Лёгкая: игра берёт половину урона и прибавляет 1, но не выше исходного.
	 * Сложная: игра умножает на 1,5. Нормальная число не трогает.
	 */
	private static float beforeDifficultyScale(Difficulty difficulty, float feltHp) {
		if (feltHp <= 0.0f) {
			return 0.0f;
		}
		return switch (difficulty) {
			case EASY -> feltHp <= 2.0f ? feltHp : (feltHp - 1.0f) * 2.0f;
			case HARD -> feltHp * 2.0f / 3.0f;
			default -> feltHp;
		};
	}
}
