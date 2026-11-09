package com.example.threat;

import com.example.siege.SiegeNight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * More hostile mobs at night by raising the number the world is allowed to hold, not by
 * dropping extras beside one mob. The game then fills its own dark spots across the spawn ring
 * (at least 24 blocks from a player, not on lit ground). Tag in mobs.txt: FROM DAY 40, GROWING.
 *
 * <p>Vanilla's monster limit is 70 per player when the spawn area is fully loaded. Both the
 * world limit and the per-player limit use that same number, so this class multiplies it.
 * 2.0 means about 140 hostile mobs can be alive around you at once.
 *
 * <p>Before {@link ThreatTable#HOSTILE_SPAWN_CAP_DAY} the limit is vanilla. From that day the
 * same slow curve as the rest of the scale is squeezed into the stretch until day 100, so the
 * night it turns on is still almost normal. Day 100 at full moon reaches {@link #CAP_MULTIPLIER_END}.
 * A new moon is weaker (about 1.7 when the end value is 2). A full moon on day 40 adds only the
 * small early-moon bump, a few mobs, not a jump to double.
 *
 * <p>Overworld only. Not the Nether or the End. Not during a siege (the siege has its own waves).
 * Not in daylight, unless {@link #ONLY_AT_NIGHT} is false. Mobs already alive are not deleted
 * when morning brings the limit back down; new ones just stop appearing until some are gone.
 */
public final class HostileSpawnCap {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — hostile nights stay at the vanilla limit. */
	public static final boolean ENABLED = true;

	/**
	 * How many times the vanilla monster limit at day 100, full moon.
	 * 2.0 = about twice as many hostile mobs alive around you. 1.0 = no change.
	 * Higher fills the dark land more densely. It does not spawn them closer than 24 blocks.
	 */
	public static final float CAP_MULTIPLIER_END = 2.0f;

	/** true — only when it is dark outside (night, storm). false — caves by day too. */
	public static final boolean ONLY_AT_NIGHT = true;

	/** true — siege night keeps the vanilla limit. false — the siege night can hold more too. */
	public static final boolean SKIP_DURING_SIEGE = true;

	// ========================================================================

	/** Level whose spawn tick is running right now. Null outside that tick. */
	private static final ThreadLocal<ServerLevel> SPAWNING_LEVEL = new ThreadLocal<>();

	private HostileSpawnCap() {
	}

	/** Called at the start of a world's spawn tick. */
	public static void begin(ServerLevel level) {
		SPAWNING_LEVEL.set(level);
	}

	/** Called when that spawn tick finishes. */
	public static void end() {
		SPAWNING_LEVEL.remove();
	}

	/**
	 * Multiplier for the monster limit during the current spawn tick.
	 * 1.0 means leave vanilla's number alone. Only monsters; animals are untouched.
	 */
	public static float multiplier() {
		ServerLevel level = SPAWNING_LEVEL.get();
		if (level == null) {
			return 1.0f;
		}
		return multiplier(level);
	}

	/** Same as {@link #multiplier()}, for a known world. */
	public static float multiplier(ServerLevel level) {
		if (!ENABLED || CAP_MULTIPLIER_END <= 1.0f) {
			return 1.0f;
		}
		if (level.dimension() != Level.OVERWORLD) {
			return 1.0f;
		}
		if (ONLY_AT_NIGHT && !level.isDarkOutside()) {
			return 1.0f;
		}
		if (SKIP_DURING_SIEGE && SiegeNight.isWindowOpen(level)) {
			return 1.0f;
		}
		// Scale off: every GROWING mechanic is at its end value, and day gates are open.
		if (!ThreatScale.ENABLED) {
			return CAP_MULTIPLIER_END;
		}
		if (!ThreatScale.reached(level, ThreatTable.HOSTILE_SPAWN_CAP_DAY)) {
			return 1.0f;
		}
		float shaped = shape(level);
		return 1.0f + (CAP_MULTIPLIER_END - 1.0f) * shaped;
	}

	/**
	 * 0 at the day the limit turns on, 1 at day 100 full moon.
	 * Days 40–100 are stretched onto the normal 0–100 curve, then the moon is applied.
	 */
	private static float shape(ServerLevel level) {
		double day = ThreatScale.dayExact(level);
		int from = ThreatTable.HOSTILE_SPAWN_CAP_DAY;
		double span = Math.max(1.0, ThreatScale.DAYS_TO_MAX - from);
		double stretched = (day - from) / span * ThreatScale.DAYS_TO_MAX;
		return ThreatScale.thetaEff(stretched, ThreatScale.worldDay(level));
	}
}
