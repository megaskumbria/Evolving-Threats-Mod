package com.example.illager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The house-patrol card. 12 is the yard radius (IllagerPatrol.ARRIVE_BLOCKS).
 * 48 is how close the player must be (IllagerPatrol.PATROL_WITNESS_BLOCKS).
 */
class PatrolArrivalTest {

	private static final double YARD = 12.0;
	private static final double WITNESS = 48.0;

	@Test
	void grantsWhenAllThreeAreInTheYard() {
		assertEquals(PatrolArrival.Verdict.GRANT, PatrolArrival.verdict(List.of(
				member(PatrolArrival.Kind.PILLAGER, 0),
				member(PatrolArrival.Kind.VINDICATOR, 4),
				member(PatrolArrival.Kind.RAVAGER, 10)
		), YARD));
	}

	@Test
	void grantsOnTheYardEdge() {
		assertEquals(PatrolArrival.Verdict.GRANT, PatrolArrival.verdict(List.of(
				member(PatrolArrival.Kind.PILLAGER, YARD),
				member(PatrolArrival.Kind.VINDICATOR, YARD),
				member(PatrolArrival.Kind.RAVAGER, YARD)
		), YARD));
	}

	@Test
	void waitsWhenTheRavagerIsStillOnTheRoad() {
		assertEquals(PatrolArrival.Verdict.WAIT, PatrolArrival.verdict(List.of(
				member(PatrolArrival.Kind.PILLAGER, 2),
				member(PatrolArrival.Kind.VINDICATOR, 2),
				member(PatrolArrival.Kind.RAVAGER, YARD + 0.01)
		), YARD));
	}

	@Test
	void onePillagerInTheYardIsEnoughIfAnotherIsStillComing() {
		assertEquals(PatrolArrival.Verdict.GRANT, PatrolArrival.verdict(List.of(
				member(PatrolArrival.Kind.PILLAGER, 1),
				member(PatrolArrival.Kind.PILLAGER, 80),
				member(PatrolArrival.Kind.VINDICATOR, 3),
				member(PatrolArrival.Kind.RAVAGER, 5)
		), YARD));
	}

	@Test
	void doesNotGrantWithoutARavager() {
		assertEquals(PatrolArrival.Verdict.ABANDON, PatrolArrival.verdict(List.of(
				member(PatrolArrival.Kind.PILLAGER, 1),
				member(PatrolArrival.Kind.VINDICATOR, 1)
		), YARD));
	}

	@Test
	void doesNotGrantWithoutAVindicator() {
		assertEquals(PatrolArrival.Verdict.ABANDON, PatrolArrival.verdict(List.of(
				member(PatrolArrival.Kind.PILLAGER, 1),
				member(PatrolArrival.Kind.RAVAGER, 1)
		), YARD));
	}

	@Test
	void doesNotGrantWithoutAPillager() {
		assertEquals(PatrolArrival.Verdict.ABANDON, PatrolArrival.verdict(List.of(
				member(PatrolArrival.Kind.VINDICATOR, 1),
				member(PatrolArrival.Kind.RAVAGER, 1)
		), YARD));
	}

	@Test
	void aDeadRavagerDoesNotCount() {
		assertEquals(PatrolArrival.Verdict.ABANDON, PatrolArrival.verdict(List.of(
				member(PatrolArrival.Kind.PILLAGER, 1),
				member(PatrolArrival.Kind.VINDICATOR, 1),
				new PatrolArrival.Member(false, PatrolArrival.Kind.RAVAGER, 1)
		), YARD));
	}

	@Test
	void waitsWhileALivingRavagerIsOutsideAndTheBodyInTheYardIsDead() {
		assertEquals(PatrolArrival.Verdict.WAIT, PatrolArrival.verdict(List.of(
				member(PatrolArrival.Kind.PILLAGER, 1),
				member(PatrolArrival.Kind.VINDICATOR, 1),
				new PatrolArrival.Member(false, PatrolArrival.Kind.RAVAGER, 1),
				member(PatrolArrival.Kind.RAVAGER, 40)
		), YARD));
	}

	@Test
	void anotherMobInTheYardDoesNotReplaceAMissingKind() {
		assertEquals(PatrolArrival.Verdict.ABANDON, PatrolArrival.verdict(List.of(
				member(PatrolArrival.Kind.PILLAGER, 1),
				member(PatrolArrival.Kind.VINDICATOR, 1),
				member(PatrolArrival.Kind.OTHER, 1)
		), YARD));
	}

	@Test
	void emptySquadIsAbandoned() {
		assertEquals(PatrolArrival.Verdict.ABANDON, PatrolArrival.verdict(List.of(), YARD));
	}

	@Test
	void negativeYardRadiusNeverGrants() {
		assertEquals(PatrolArrival.Verdict.WAIT, PatrolArrival.verdict(List.of(
				member(PatrolArrival.Kind.PILLAGER, 0),
				member(PatrolArrival.Kind.VINDICATOR, 0),
				member(PatrolArrival.Kind.RAVAGER, 0)
		), -1));
	}

	@Test
	void playerOnTheWitnessEdgeCounts() {
		assertTrue(PatrolArrival.playerCloseEnough(WITNESS * WITNESS, WITNESS));
	}

	@Test
	void playerJustOutsideTheWitnessRangeDoesNotCount() {
		assertFalse(PatrolArrival.playerCloseEnough(WITNESS * WITNESS + 0.01, WITNESS));
	}

	@Test
	void playerAtTheHouseCounts() {
		assertTrue(PatrolArrival.playerCloseEnough(0, WITNESS));
	}

	@Test
	void negativeWitnessRangeCountsNobody() {
		assertFalse(PatrolArrival.playerCloseEnough(0, -1));
	}

	private static PatrolArrival.Member member(PatrolArrival.Kind kind, double blocks) {
		return new PatrolArrival.Member(true, kind, blocks * blocks);
	}
}
