package com.example.advancement;

/**
 * Yes/no checks for advancements, with no Minecraft types.
 * The game code asks these before it grants a card.
 * Change a number in the mechanic class, not here: these methods only compare.
 */
public final class AdvancementMoments {

	private AdvancementMoments() {
	}

	/**
	 * The tug is at least as strong as sprint, so the player can no longer hold the edge.
	 * A weaker tug (the start of the ramp) does not count. Negative numbers do not count.
	 */
	public static boolean magnetHolds(double tugPerTick, double sprintPerTick) {
		return sprintPerTick > 0.0 && tugPerTick >= sprintPerTick;
	}

	/**
	 * The player is inside the blast. The edge counts.
	 * {@code distanceSquared} is the square of the distance, the same way the game measures it.
	 * A radius of 0 or less never counts.
	 */
	public static boolean blastReaches(double distanceSquared, double radius) {
		return radius > 0.0 && distanceSquared <= radius * radius;
	}

	/**
	 * Enough spiders actually appeared, including the one that started the pack.
	 * A smaller pack (no room in a tunnel) does not count.
	 */
	public static boolean swarmBigEnough(int present, int minimum) {
		return minimum > 0 && present >= minimum;
	}

	/**
	 * The arrow hit from farther than a normal bow can shoot.
	 * Exactly the normal range does not count: a normal bow still reaches that far.
	 */
	public static boolean fartherThanNormalBow(double distance, double normalRange) {
		return normalRange > 0.0 && distance > normalRange;
	}

	/**
	 * The skeleton's size matches the small one.
	 * 0.05 leaves room for a tiny save/load difference. An adult (1.0) does not match 0.5.
	 */
	public static boolean smallEnough(double scale, double smallScale) {
		return smallScale > 0.0 && Math.abs(scale - smallScale) <= 0.05;
	}

	/** Punch on the bow is the strong kind. Weaker punch, including a captain's, does not count. */
	public static boolean punchStrongEnough(int level, int minimum) {
		return minimum > 0 && level >= minimum;
	}

	/** Enough arrows from the same volley hit. One arrow does not count. */
	public static boolean volleyEnough(int hits, int needed) {
		return needed > 0 && hits >= needed;
	}

	/**
	 * This wave is larger than the size the game rolled for the first sleepless nights.
	 * The same size as that roll does not count.
	 */
	public static boolean flockBigger(int grown, int vanillaCount) {
		return vanillaCount > 0 && grown > vanillaCount;
	}
}
