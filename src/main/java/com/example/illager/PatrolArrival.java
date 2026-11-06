package com.example.illager;

import java.util.List;

/**
 * When a daytime patrol counts as having reached the house.
 * The card needs one living pillager, one living vindicator, and one living ravager
 * inside the yard at the same time. One type still on the road means wait.
 * One type dead, and none of that type left, means the squad will never finish:
 * forget the house point so a later squad is not blocked.
 * A vanilla patrol is not in this list. Radius and the witness distance are passed in
 * from {@link IllagerPatrol}, so the tests and the game share one rule.
 */
public final class PatrolArrival {

	/** Wait, pay the card, or let the squad go because it can no longer arrive whole. */
	public enum Verdict {
		WAIT,
		GRANT,
		ABANDON
	}

	public enum Kind {
		PILLAGER,
		VINDICATOR,
		RAVAGER,
		OTHER
	}

	/**
	 * One squad member.
	 *
	 * @param alive            false — a corpse, does not count
	 * @param kind             pillager, vindicator, ravager, or someone else
	 * @param distanceSquared  squared blocks from the remembered house point
	 */
	public record Member(boolean alive, Kind kind, double distanceSquared) {
	}

	private PatrolArrival() {
	}

	/**
	 * @param arriveBlocks yard radius. A member exactly on the edge is inside.
	 *                     Negative — nobody is inside, so the squad can only wait or be abandoned.
	 */
	public static Verdict verdict(List<Member> members, double arriveBlocks) {
		boolean livingPillager = false;
		boolean livingVindicator = false;
		boolean livingRavager = false;
		boolean yardPillager = false;
		boolean yardVindicator = false;
		boolean yardRavager = false;
		double limit = arriveBlocks * arriveBlocks;
		boolean yardCounts = arriveBlocks >= 0.0;
		for (Member member : members) {
			if (!member.alive()) {
				continue;
			}
			boolean inYard = yardCounts && member.distanceSquared() <= limit;
			switch (member.kind()) {
				case PILLAGER -> {
					livingPillager = true;
					yardPillager = yardPillager || inYard;
				}
				case VINDICATOR -> {
					livingVindicator = true;
					yardVindicator = yardVindicator || inYard;
				}
				case RAVAGER -> {
					livingRavager = true;
					yardRavager = yardRavager || inYard;
				}
				case OTHER -> {
				}
			}
		}
		if (yardPillager && yardVindicator && yardRavager) {
			return Verdict.GRANT;
		}
		if (livingPillager && livingVindicator && livingRavager) {
			return Verdict.WAIT;
		}
		return Verdict.ABANDON;
	}

	/**
	 * @param witnessBlocks how close the player must be to the house point. Exactly on the edge counts.
	 */
	public static boolean playerCloseEnough(double distanceSquared, double witnessBlocks) {
		if (witnessBlocks < 0.0) {
			return false;
		}
		return distanceSquared <= witnessBlocks * witnessBlocks;
	}
}
