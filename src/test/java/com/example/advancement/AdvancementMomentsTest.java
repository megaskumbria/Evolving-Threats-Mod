package com.example.advancement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge cases for the newer advancement cards.
 * Numbers match the mechanic classes: sprint 0.28, blast edge, pack of 6,
 * normal bow 15 blocks, small scale 0.5, Punch V, two volley hits.
 */
class AdvancementMomentsTest {

	@Test
	void magnetCountsOnlyOnceTheTugMatchesSprint() {
		assertFalse(AdvancementMoments.magnetHolds(0.10, 0.28));
		assertTrue(AdvancementMoments.magnetHolds(0.28, 0.28));
		assertTrue(AdvancementMoments.magnetHolds(0.60, 0.28));
		assertFalse(AdvancementMoments.magnetHolds(1.0, 0.0));
	}

	@Test
	void blastCountsTheEdgeAndNotPastIt() {
		assertTrue(AdvancementMoments.blastReaches(9.0, 3.0));
		assertTrue(AdvancementMoments.blastReaches(0.0, 3.0));
		assertFalse(AdvancementMoments.blastReaches(9.01, 3.0));
		assertFalse(AdvancementMoments.blastReaches(0.0, 0.0));
	}

	@Test
	void swarmNeedsTheFullPack() {
		assertTrue(AdvancementMoments.swarmBigEnough(6, 6));
		assertTrue(AdvancementMoments.swarmBigEnough(10, 6));
		assertFalse(AdvancementMoments.swarmBigEnough(5, 6));
		assertFalse(AdvancementMoments.swarmBigEnough(6, 0));
	}

	@Test
	void farShotIsPastTheNormalBow() {
		assertFalse(AdvancementMoments.fartherThanNormalBow(15.0, 15.0));
		assertTrue(AdvancementMoments.fartherThanNormalBow(15.01, 15.0));
		assertFalse(AdvancementMoments.fartherThanNormalBow(10.0, 15.0));
	}

	@Test
	void smallScaleDoesNotMatchAnAdult() {
		assertTrue(AdvancementMoments.smallEnough(0.5, 0.5));
		assertTrue(AdvancementMoments.smallEnough(0.54, 0.5));
		assertFalse(AdvancementMoments.smallEnough(1.0, 0.5));
		assertFalse(AdvancementMoments.smallEnough(0.5, 0.0));
	}

	@Test
	void punchFiveCountsAndFourDoesNot() {
		assertTrue(AdvancementMoments.punchStrongEnough(5, 5));
		assertTrue(AdvancementMoments.punchStrongEnough(6, 5));
		assertFalse(AdvancementMoments.punchStrongEnough(4, 5));
		assertFalse(AdvancementMoments.punchStrongEnough(5, 0));
	}

	@Test
	void volleyNeedsTwoHits() {
		assertFalse(AdvancementMoments.volleyEnough(1, 2));
		assertTrue(AdvancementMoments.volleyEnough(2, 2));
		assertFalse(AdvancementMoments.volleyEnough(2, 0));
	}

	@Test
	void flockMustBeLargerThanTheVanillaRoll() {
		assertTrue(AdvancementMoments.flockBigger(4, 3));
		assertFalse(AdvancementMoments.flockBigger(3, 3));
		assertFalse(AdvancementMoments.flockBigger(2, 3));
		assertFalse(AdvancementMoments.flockBigger(4, 0));
	}
}
