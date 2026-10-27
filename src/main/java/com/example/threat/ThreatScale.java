package com.example.threat;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Шкала угрозы мира: одна на весь мир, растёт от 0 до 100 дней, ночью «дышит» вместе с луной.
 * Здесь только математика. Все пороги дней и стартовые шансы лежат в {@link ThreatTable}.
 *
 * <h3>Как это считается (по шагам)</h3>
 * <ol>
 *   <li><b>День</b> = (время мира − момент установки мода) / 24000 (дробное: 37.5 = середина 38-го дня).
 *       Шкала стартует с нуля, когда мод впервые загрузил мир, даже если мир уже старый.
 *       Спишь ночью — счётчик идёт быстрее, это нормально: мир «стареет».</li>
 *   <li><b>Прогресс</b> t = день / {@link #DAYS_TO_MAX}, не больше 1.</li>
 *   <li><b>Угроза</b> Θ = t в степени {@link #CURVE_POWER}: медленный старт, быстрее к концу.</li>
 *   <li><b>Луна</b>: полнолуние усиливает, новолуние ослабляет (см. {@link #thetaEff(Level)}).</li>
 * </ol>
 * Результат «Θ_eff» (0.0–1.0) используют броня, оружие, чары, зелья и прочие механики из mobs.txt с пометкой РАСТЁТ.
 * Механики с пометкой «С N-го дня» проверяют только {@link #reached(Level, int)} и луну не учитывают.
 */
public final class ThreatScale {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/**
	 * Главный выключатель. false = шкала не работает: все шансы равны своему максимуму (как было до шкалы),
	 * все пороги дней открыты. Удобно, чтобы быстро сравнить «с шкалой» и «без».
	 */
	public static final boolean ENABLED = true;

	/** За сколько игровых дней шкала доходит до 100%. 100 дней = 33 часа без сна. После этого остаётся на максимуме. */
	public static final int DAYS_TO_MAX = 100;

	/**
	 * Степень кривой роста. 1.0 = ровно по дням. 1.5 = первые дни почти ноль, потом всё быстрее.
	 * 2.0 = ещё медленнее старт, почти всё случается в конце. Меньше 1.0 — быстрый старт (так делать не стоит).
	 */
	public static final double CURVE_POWER = 1.5;

	/**
	 * Новолуние: угроза умножается на это число. 0.7 = на 30% слабее обычной.
	 * Луна считается по номеру дня (весь день с утра до утра, как в небе).
	 */
	public static final float MOON_NEW_FACTOR = 0.7f;

	/** Полнолуние: угроза умножается на это число. 1.2 = на 20% сильнее обычной. */
	public static final float MOON_FULL_FACTOR = 1.2f;

	/**
	 * Добавка на полнолуние в самом начале игры (доля шкалы, 0.0–1.0). 0.05 = плюс 5% шкалы.
	 * Нужна, чтобы даже на 5-й день полнолуние было заметно: просто умножение почти ничего не даёт, пока шкала около нуля.
	 * С ростом шкалы добавка уменьшается сама. 0 = полнолуние только умножает.
	 */
	public static final float MOON_FLAT_BONUS = 0.05f;

	// ========================================================================

	/** Один игровой день в тиках. 24000 тиков = 20 минут. */
	public static final long TICKS_PER_DAY = 24000L;

	/** Сколько фаз у луны. Каждая длится один день, полный круг = 8 дней. */
	public static final int MOON_PHASES = 8;

	/**
	 * Освещённость луны для каждой фазы (как в ванили): 0 = полнолуние, 4 = новолуние.
	 * Порядок: полная, убывающая, последняя четверть, старая, новая, молодая, первая четверть, прибывающая.
	 */
	private static final float[] MOON_LIGHT = { 1.0f, 0.75f, 0.5f, 0.25f, 0.0f, 0.25f, 0.5f, 0.75f };

	private ThreatScale() {
	}

	// ----------------------------------------------------------------- время

	/**
	 * Момент (время мира в тиках), с которого шкала считает дни. Один раз ставится, когда мод впервые
	 * загрузил этот мир, и хранится в папке мира (ThreatData). Клиент получает его с сервера (ThreatEvents).
	 * Поэтому в старом мире, где уже прошло 24 дня, шкала всё равно начинается с нуля.
	 */
	private static volatile long startClock = 0L;

	public static long getStartClock() {
		return startClock;
	}

	/** Вызывается самим модом (сервер при старте/команде, клиент при получении пакета). */
	public static void setStartClock(long clock) {
		startClock = clock;
	}

	/** Сколько тиков прошло от начала шкалы. Не бывает меньше нуля. */
	public static long elapsedTicks(Level level) {
		return Math.max(0L, level.getOverworldClockTime() - startClock);
	}

	/** Дробный день шкалы: 0.0 — самое начало, 37.5 — середина 38-го дня после установки мода. */
	public static double dayExact(Level level) {
		return elapsedTicks(level) / (double) TICKS_PER_DAY;
	}

	/** Номер текущего дня шкалы целым числом (0 = первые сутки после установки мода). Его проверяют пороги «С N-го дня». */
	public static int day(Level level) {
		return (int) (elapsedTicks(level) / TICKS_PER_DAY);
	}

	/** Номер дня самого мира (как в небе и в ванили), считается от создания мира. Нужен луне и осадам. */
	public static long worldDay(Level level) {
		return Math.floorDiv(level.getOverworldClockTime(), TICKS_PER_DAY);
	}

	/**
	 * Номер дня мира (worldDay), в который наступает день шкалы {@code threatDay}.
	 * Нужен осадам: они назначаются по дням мира, а первая должна быть на 25-й день шкалы.
	 */
	public static long worldDayOfThreatDay(int threatDay) {
		return Math.ceilDiv(startClock + threatDay * TICKS_PER_DAY, TICKS_PER_DAY);
	}

	/** Дошёл ли мир до дня {@code fromDay}. Для механик «С N-го дня». При выключенной шкале всегда true. */
	public static boolean reached(Level level, int fromDay) {
		return !ENABLED || day(level) >= fromDay;
	}

	// ----------------------------------------------------------------- луна

	/** Фаза луны 0–7 (0 = полнолуние, 4 = новолуние). Берётся из номера дня мира, как в небе. */
	public static int moonPhase(Level level) {
		return moonPhaseOfDay(worldDay(level));
	}

	/** Фаза луны для заданного номера дня. */
	public static int moonPhaseOfDay(long day) {
		return (int) Math.floorMod(day, (long) MOON_PHASES);
	}

	/** Сила луны: 1.0 полнолуние … 0.0 новолуние. */
	public static float moonLight(int phase) {
		return MOON_LIGHT[Math.floorMod(phase, MOON_PHASES)];
	}

	// ----------------------------------------------------------------- шкала

	/** Прогресс по дням, 0.0–1.0: 0.37 = 37-й день из 100. Его рисует полоска на экране. */
	public static float progress(Level level) {
		return progress(dayExact(level));
	}

	public static float progress(double dayExact) {
		if (!ENABLED) {
			return 1.0f;
		}
		return (float) Math.min(1.0, dayExact / DAYS_TO_MAX);
	}

	/** Угроза без луны: прогресс в степени {@link #CURVE_POWER}. 0.0–1.0. */
	public static float theta(Level level) {
		return theta(dayExact(level));
	}

	public static float theta(double dayExact) {
		return (float) Math.pow(progress(dayExact), CURVE_POWER);
	}

	/**
	 * Угроза с учётом луны, 0.0–1.0. Её используют все механики со словом РАСТЁТ.
	 *
	 * <p>Формула: Θ × (0.7 … 1.2 по фазе луны) + добавка полнолуния × (1 − Θ), потом ограничено до 1.
	 * Новолуние откатывает угрозу назад, но к следующему полнолунию она всегда выше, чем была.
	 */
	public static float thetaEff(Level level) {
		return thetaEff(dayExact(level), worldDay(level));
	}

	/** {@code worldDay} нужен только луне: она идёт по дням мира, а не по дням шкалы. */
	public static float thetaEff(double dayExact, long worldDay) {
		if (!ENABLED) {
			return 1.0f;
		}
		float theta = theta(dayExact);
		float moon = moonLight(moonPhaseOfDay(worldDay));
		float factor = MOON_NEW_FACTOR + (MOON_FULL_FACTOR - MOON_NEW_FACTOR) * moon;
		float value = theta * factor + MOON_FLAT_BONUS * moon * (1.0f - theta);
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	// ------------------------------------------------------------- помощники

	/** Значение между стартом (день 0) и концом (день 100) по {@link #thetaEff(Level)}. */
	public static float lerp(Level level, float start, float end) {
		return start + (end - start) * thetaEff(level);
	}

	/** То же для дробных чисел. */
	public static double lerp(Level level, double start, double end) {
		return start + (end - start) * thetaEff(level);
	}

	/**
	 * Шанс, который растёт от {@code endChance × startRatio} (день 0) до {@code endChance} (день 100).
	 * {@code endChance} — это число, которое сейчас стоит в коде у механики.
	 * {@code startRatio}: 0.10 = в начале игры шанс в 10 раз меньше максимального.
	 */
	public static float growingChance(Level level, float endChance, float startRatio) {
		return lerp(level, endChance * startRatio, endChance);
	}

	/**
	 * Шанс «опасной способности» (спавнер на голове, тактик, быстрый лук и т.п. — всё, что в mobs.txt помечено РАСТЁТ):
	 * от {@code endChance × DANGEROUS_START_RATIO} (день 0) до {@code endChance} (день 100).
	 */
	public static float dangerousChance(Level level, float endChance) {
		return growingChance(level, endChance, ThreatTable.DANGEROUS_START_RATIO);
	}

	/**
	 * Выбирает номер из набора весов, где вес каждого пункта плавно меняется
	 * от {@code startWeights} (день 0) до {@code endWeights} (день 100).
	 * Веса не обязаны давать в сумме 100: игра сама посчитает долю.
	 *
	 * @return номер пункта (0, 1, 2, …)
	 */
	public static int pickWeighted(Level level, RandomSource random, double[] startWeights, double[] endWeights) {
		float theta = thetaEff(level);
		double total = 0.0;
		double[] weights = new double[endWeights.length];
		for (int i = 0; i < endWeights.length; i++) {
			double start = i < startWeights.length ? startWeights[i] : endWeights[i];
			weights[i] = start + (endWeights[i] - start) * theta;
			total += weights[i];
		}
		if (total <= 0.0) {
			return 0;
		}
		double roll = random.nextDouble() * total;
		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];
			if (roll < 0.0) {
				return i;
			}
		}
		return weights.length - 1;
	}

	/** То же, но конечные веса заданы целыми числами (как в старых настройках мода). */
	public static int pickWeighted(Level level, RandomSource random, double[] startWeights, int[] endWeights) {
		double[] end = new double[endWeights.length];
		for (int i = 0; i < endWeights.length; i++) {
			end[i] = endWeights[i];
		}
		return pickWeighted(level, random, startWeights, end);
	}

	/**
	 * Короткий текст для команды {@code /evolvingthreats day}: что сейчас показывает шкала.
	 */
	public static String describe(Level level) {
		double exact = dayExact(level);
		int phase = moonPhase(level);
		return String.format(
				"День шкалы %.1f из %d (день мира %d) | луна %d (сила %.2f) | прогресс %.0f%% | Θ=%.3f | Θ_eff=%.3f",
				exact,
				DAYS_TO_MAX,
				worldDay(level),
				phase,
				moonLight(phase),
				progress(level) * 100.0f,
				theta(level),
				thetaEff(level)
		);
	}
}
