package com.example.enderman;

import net.minecraft.util.Mth;

/**
 How often does the Enderman take the block next to him and put it on the ground?
 * The block search itself remains with the game: one random block in a pair of steps,
 * and only those that the Enderman can carry.
 If in the world “mobs break blocks” is turned off, it does not touch anything.
 *
 * <p>FIXED: From day one, the threat scale does not increase.
 */
public final class EndermanBlocks {

	// ===================== SETTINGS (change these numbers) =====================

	/** False - Takes and puts blocks as in a normal game. */
	public static final boolean ENABLED = true;

	/**
	 * Pause between attempts to TAKE a block, in ticks. 20 ticks = 1 second.
	 * 5 = on average once every quarter of a second. In a normal game about 10 (once in half a second).
	 Less, more often. 1 = tries every tick.
	 This is not a guarantee: one random block is watching nearby. He doesn't take air and rock.
	 */
	public static final int TAKE_INTERVAL = 5;

	/**
	 * The pause between trying to put a block that already carries in the ticks.
	 * 40 = on average once every 2 seconds. In a normal game about 1000 (every 50 seconds)
	 Therefore, the block hangs in the hands for a long time and almost does not move.
	 Less, puts it down faster. 1 = puts almost as soon as you find the place.
	 The place is also random: you need air over a solid block, otherwise the attempt is empty.
	 */
	public static final int PLACE_INTERVAL = 40;

	// ========================================================================

	private EndermanBlocks() {
	}

	/** Take the toss number. If the mechanics are off, how did the game count? */
	public static int takeInterval(int vanillaTicks) {
		return interval(vanillaTicks, TAKE_INTERVAL);
	}

	/** Set the toss number. If the mechanics are off, how did the game count? */
	public static int placeInterval(int vanillaTicks) {
		return interval(vanillaTicks, PLACE_INTERVAL);
	}

	private static int interval(int vanillaTicks, int ours) {
		if (!ENABLED) {
			// The game also pauses when the mechanics are turned off.
			return Mth.positiveCeilDiv(vanillaTicks, 2);
		}
		return Math.max(1, ours);
	}
}
