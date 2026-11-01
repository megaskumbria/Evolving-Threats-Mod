package com.example.threat;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * World threat scale: one per world, grows from 0 to 100 days, "breathes" with the moon at night.
 * Math only lives here. Day thresholds and start chances — in {@link ThreatTable}.
 *
 * <h3>How it is computed (step by step)</h3>
 * <ol>
 *   <li><b>Day</b> = (world time − mod install time) / 24000 (fractional: 37.5 = mid day 38).
 *       Scale starts at zero when the mod first loads the world, even on an old world.
 *       Sleeping skips time faster — normal: the world "ages" while you sleep.</li>
 *   <li><b>Progress</b> t = day / {@link #DAYS_TO_MAX}, capped at 1.</li>
 *   <li><b>Threat</b> Θ = t raised to {@link #CURVE_POWER}: slow start, faster toward the end.</li>
 *   <li><b>Moon</b>: full moon strengthens, new moon weakens (see {@link #thetaEff(Level)}).</li>
 * </ol>
 * Resulting Θ_eff (0.0–1.0) drives armor, weapons, enchants, potions, and other GROWING mechanics in mobs.txt.
 * "From day N" mechanics only call {@link #reached(Level, int)} — moon ignored.
 */
public final class ThreatScale {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * Master switch. false = scale off: all chances use their maximum (pre-scale behavior),
	 * all day gates open. Handy to compare "with scale" vs "without".
	 */
	public static final boolean ENABLED = true;

	/** Game days until scale reaches 100%. 100 days ≈ 33 hours without sleeping. Stays at max after that. */
	public static final int DAYS_TO_MAX = 100;

	/**
	 * Growth curve exponent. 1.0 = linear by day. 1.5 = near zero early, then accelerates.
	 * 2.0 = even slower start; most events cluster at the end. Below 1.0 — fast start (avoid).
	 */
	public static final double CURVE_POWER = 1.5;

	/**
	 * New moon: threat × this factor. 0.7 = 30% weaker than baseline.
	 * Moon phase uses world day number (whole day from dawn to dawn, like the sky).
	 */
	public static final float MOON_NEW_FACTOR = 0.7f;

	/** Full moon: threat × this factor. 1.2 = 20% stronger than baseline. */
	public static final float MOON_FULL_FACTOR = 1.2f;

	/**
	 * Flat full-moon bonus at game start (scale fraction 0.0–1.0). 0.05 = +5% scale.
	 * Makes full moon noticeable on day 5: pure multiply barely moves while Θ ≈ 0.
	 * Bonus fades as Θ grows. 0 = moon multiply only.
	 */
	public static final float MOON_FLAT_BONUS = 0.05f;

	// ========================================================================

	/** Ticks per game day. 24000 ticks = 20 minutes. */
	public static final long TICKS_PER_DAY = 24000L;

	/** Moon phase count. Each phase lasts one day; full cycle = 8 days. */
	public static final int MOON_PHASES = 8;

	/**
	 * Moon brightness per phase (vanilla): 0 = full moon, 4 = new moon.
	 * Order: full, waning gibbous, last quarter, old, new, waxing crescent, first quarter, waxing gibbous.
	 */
	private static final float[] MOON_LIGHT = { 1.0f, 0.75f, 0.5f, 0.25f, 0.0f, 0.25f, 0.5f, 0.75f };

	private ThreatScale() {
	}

	// ----------------------------------------------------------------- time

	/**
	 * World time (ticks) when scale counting starts. Set once when the mod first loads the world,
	 * stored in world data (ThreatData). Client gets it from the server (ThreatEvents).
	 * On an old world at day 24, scale still starts at zero.
	 */
	private static volatile long startClock = 0L;

	public static long getStartClock() {
		return startClock;
	}

	/** Called by the mod (server on start/command, client on packet). */
	public static void setStartClock(long clock) {
		startClock = clock;
	}

	/** Ticks since scale start. Never negative. */
	public static long elapsedTicks(Level level) {
		return Math.max(0L, level.getOverworldClockTime() - startClock);
	}

	/** Fractional scale day: 0.0 at start, 37.5 = mid 38th day after mod install. */
	public static double dayExact(Level level) {
		return elapsedTicks(level) / (double) TICKS_PER_DAY;
	}

	/** Integer scale day (0 = first day after install). Used by "from day N" gates. */
	public static int day(Level level) {
		return (int) (elapsedTicks(level) / TICKS_PER_DAY);
	}

	/** Vanilla world day (sky and vanilla logic), from world creation. Used for moon and sieges. */
	public static long worldDay(Level level) {
		return Math.floorDiv(level.getOverworldClockTime(), TICKS_PER_DAY);
	}

	/**
	 * World day (worldDay) when scale day {@code threatDay} begins.
	 * Sieges use world days; the first must land on scale day 25.
	 */
	public static long worldDayOfThreatDay(int threatDay) {
		return Math.ceilDiv(startClock + threatDay * TICKS_PER_DAY, TICKS_PER_DAY);
	}

	/** Has scale reached {@code fromDay}? For "from day N" mechanics. If scale disabled, always true. */
	public static boolean reached(Level level, int fromDay) {
		return !ENABLED || day(level) >= fromDay;
	}

	// ----------------------------------------------------------------- moon

	/** Moon phase 0–7 (0 = full, 4 = new). From world day, like the sky. */
	public static int moonPhase(Level level) {
		return moonPhaseOfDay(worldDay(level));
	}

	/** Moon phase for a given world day. */
	public static int moonPhaseOfDay(long day) {
		return (int) Math.floorMod(day, (long) MOON_PHASES);
	}

	/** Moon strength: 1.0 full … 0.0 new. */
	public static float moonLight(int phase) {
		return MOON_LIGHT[Math.floorMod(phase, MOON_PHASES)];
	}

	// ----------------------------------------------------------------- scale

	/** Day progress 0.0–1.0: 0.37 = day 37 of 100. Drawn as the on-screen bar. */
	public static float progress(Level level) {
		return progress(dayExact(level));
	}

	public static float progress(double dayExact) {
		if (!ENABLED) {
			return 1.0f;
		}
		return (float) Math.min(1.0, dayExact / DAYS_TO_MAX);
	}

	/** Threat without moon: progress raised to {@link #CURVE_POWER}. 0.0–1.0. */
	public static float theta(Level level) {
		return theta(dayExact(level));
	}

	public static float theta(double dayExact) {
		return (float) Math.pow(progress(dayExact), CURVE_POWER);
	}

	/**
	 * Threat with moon, 0.0–1.0. Used by all GROWING mechanics.
	 *
	 * <p>Formula: Θ × (0.7 … 1.2 by phase) + full-moon flat × (1 − Θ), then clamped to 1.
	 * New moon pulls threat down, but each full moon is higher than the last.
	 */
	public static float thetaEff(Level level) {
		return thetaEff(dayExact(level), worldDay(level));
	}

	/** {@code worldDay} is for moon only — moon follows world days, not scale days. */
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

	// ------------------------------------------------------------- helpers

	/** Interpolate between start (day 0) and end (day 100) using {@link #thetaEff(Level)}. */
	public static float lerp(Level level, float start, float end) {
		return start + (end - start) * thetaEff(level);
	}

	/** Same for doubles. */
	public static double lerp(Level level, double start, double end) {
		return start + (end - start) * thetaEff(level);
	}

	/**
	 * Chance growing from {@code endChance × startRatio} (day 0) to {@code endChance} (day 100).
	 * {@code endChance} is the value in the mechanic's class today.
	 * {@code startRatio}: 0.10 = one tenth of max at game start.
	 */
	public static float growingChance(Level level, float endChance, float startRatio) {
		return lerp(level, endChance * startRatio, endChance);
	}

	/**
	 * "Dangerous ability" chance (spawner head, tactician, fast bow, etc. — GROWING in mobs.txt):
	 * from {@code endChance × DANGEROUS_START_RATIO} (day 0) to {@code endChance} (day 100).
	 */
	public static float dangerousChance(Level level, float endChance) {
		return growingChance(level, endChance, ThreatTable.DANGEROUS_START_RATIO);
	}

	/**
	 * Pick an index from weight arrays where each weight lerps
	 * from {@code startWeights} (day 0) to {@code endWeights} (day 100).
	 * Weights need not sum to 100 — relative values matter.
	 *
	 * @return index (0, 1, 2, …)
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

	/** Same with int end weights (legacy mod settings). */
	public static int pickWeighted(Level level, RandomSource random, double[] startWeights, int[] endWeights) {
		double[] end = new double[endWeights.length];
		for (int i = 0; i < endWeights.length; i++) {
			end[i] = endWeights[i];
		}
		return pickWeighted(level, random, startWeights, end);
	}

	/**
	 * Short text for {@code /evolvingthreats day}: what the scale shows now.
	 */
	public static String describe(Level level) {
		double exact = dayExact(level);
		int phase = moonPhase(level);
		return String.format(
				"Scale day %.1f of %d (world day %d) | moon %d (strength %.2f) | progress %.0f%% | theta=%.3f | theta_eff=%.3f",
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
