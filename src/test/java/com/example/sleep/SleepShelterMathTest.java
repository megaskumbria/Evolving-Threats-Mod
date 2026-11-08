package com.example.sleep;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bed prices from the design, without a running game.
 * Feet are at y = 1. The floor is stone at y = 0. The headroom is y = 2.
 */
class SleepShelterMathTest {

	@Test
	void eachTwoPointsHalvesTheChance() {
		assertEquals(1.0, SleepShelterMath.wakeChance(0.0), 1.0e-9);
		assertEquals(0.5, SleepShelterMath.wakeChance(2.0), 1.0e-9);
		assertEquals(0.25, SleepShelterMath.wakeChance(4.0), 1.0e-9);
		assertEquals(0.125, SleepShelterMath.wakeChance(6.0), 1.0e-9);
	}

	@Test
	void openSkyIsCertainAndHits() {
		Grid grid = new Grid();
		grid.sky.put(key(0, 1, 0), true);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 16, 4);
		assertEquals(0.0, route.points(), 1.0e-9);
		assertTrue(route.strike());
		assertNull(route.breach());
	}

	@Test
	void darkRoomIsCertain() {
		Grid grid = floor();
		grid.light.put(key(0, 1, 0), 0);
		grid.light.put(key(0, 2, 0), 0);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 16, 4);
		assertEquals(0.0, route.points(), 1.0e-9);
		assertTrue(route.strike());
		assertNull(route.breach());
	}

	@Test
	void woodenDoorWithNightOutsideIsAboutHalf() {
		Grid grid = floor();
		for (int z = -8; z <= 8; z++) {
			grid.kind.put(key(1, 1, z), SleepShelterMath.Kind.TOUGH);
			grid.kind.put(key(1, 2, z), SleepShelterMath.Kind.TOUGH);
		}
		grid.kind.put(key(1, 1, 0), SleepShelterMath.Kind.WOOD_DOOR);
		grid.kind.put(key(1, 2, 0), SleepShelterMath.Kind.WOOD_DOOR);
		grid.light.put(key(2, 1, 0), 4);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 16, 4);
		assertEquals(2.25, route.points(), 1.0e-6);
		assertFalse(route.strike());
		assertEquals(0.458, SleepShelterMath.wakeChance(route.points()), 0.01);
		assertBreach(route, 1, 1, 0, 1, 2, 0);
	}

	@Test
	void twoDirtBlocksAreAboutTwelvePercent() {
		Grid grid = floor();
		for (int z = -8; z <= 8; z++) {
			wall(grid, 1, z, SleepShelterMath.Kind.SOFT);
			wall(grid, 2, z, SleepShelterMath.Kind.SOFT);
		}
		grid.light.put(key(3, 1, 0), 4);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 16, 4);
		assertEquals(6.25, route.points(), 1.0e-6);
		assertFalse(route.strike());
		assertEquals(0.115, SleepShelterMath.wakeChance(route.points()), 0.01);
		assertBreach(route, 1, 1, 0, 1, 2, 0);
	}

	@Test
	void aMineUnderTheFloorDoesNotCount() {
		Grid grid = floor();
		grid.kind.put(key(1, 1, 0), SleepShelterMath.Kind.CLIMB);
		grid.kind.put(key(1, 0, 0), SleepShelterMath.Kind.CLIMB);
		grid.kind.put(key(1, -1, 0), SleepShelterMath.Kind.CLIMB);
		grid.kind.put(key(1, -2, 0), SleepShelterMath.Kind.CLIMB);
		grid.light.put(key(1, -2, 0), 0);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 4, 8);
		// The cave is ignored. What remains is the lit circle, same as an open lit floor.
		assertEquals(0.625, route.points(), 1.0e-6);
		assertNull(route.breach());
	}

	@Test
	void aHighFloorIgnoresTheDarknessBelow() {
		Grid grid = new Grid();
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				grid.kind.put(key(x, 9, z), SleepShelterMath.Kind.STONE);
				grid.kind.put(key(x, 12, z), SleepShelterMath.Kind.STONE);
			}
		}
		grid.kind.put(key(0, 9, 0), SleepShelterMath.Kind.AIR);
		for (int y = 10; y <= 11; y++) {
			for (int z = -1; z <= 1; z++) {
				grid.kind.put(key(2, y, z), SleepShelterMath.Kind.FENCE);
				grid.kind.put(key(-2, y, z), SleepShelterMath.Kind.STONE);
			}
			for (int x = -1; x <= 1; x++) {
				grid.kind.put(key(x, y, 2), SleepShelterMath.Kind.STONE);
				grid.kind.put(key(x, y, -2), SleepShelterMath.Kind.STONE);
			}
		}
		grid.light.put(key(0, 10, 0), 15);
		grid.light.put(key(0, 11, 0), 15);
		grid.light.put(key(3, 10, 0), 4);
		grid.light.put(key(0, 1, 0), 0);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 10, 0, 16, 12);
		assertTrue(route.points() > 1.5);
		assertTrue(route.points() < 4.0);
		assertTrue(route.breach() != null);
	}

	@Test
	void stairsDownToTheNightAreAWalk() {
		Grid grid = floor();
		grid.kind.put(key(1, 1, 0), SleepShelterMath.Kind.CLIMB);
		grid.kind.put(key(1, 2, 0), SleepShelterMath.Kind.CLIMB);
		grid.light.put(key(2, 1, 0), 4);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 16, 4);
		assertTrue(route.points() < 1.0);
		assertNull(route.breach());
	}

	@Test
	void aBoxInTheSkyNeedsNoLadder() {
		Grid grid = new Grid();
		for (int x = -1; x <= 1; x++) {
			for (int y = 3; y <= 6; y++) {
				for (int z = -1; z <= 1; z++) {
					if (x == 0 && z == 0 && (y == 4 || y == 5)) {
						continue;
					}
					grid.kind.put(key(x, y, z), SleepShelterMath.Kind.STONE);
				}
			}
		}
		grid.light.put(key(0, 4, 0), 15);
		grid.light.put(key(0, 5, 0), 15);
		grid.light.put(key(2, 4, 0), 4);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 4, 0, 16, 8);
		assertTrue(route.points() < 8.0);
		assertTrue(SleepShelterMath.wakeChance(route.points()) > 0.2);
		assertTrue(route.breach() != null);
	}

	@Test
	void aRoofedPlatformTwoBlocksUpIsNotSealed() {
		Grid grid = new Grid();
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				grid.kind.put(key(x, 2, z), SleepShelterMath.Kind.STONE);
				grid.kind.put(key(x, 5, z), SleepShelterMath.Kind.STONE);
			}
		}
		grid.light.put(key(0, 3, 0), 15);
		grid.light.put(key(0, 4, 0), 15);
		// Night air beside the platform. Nothing to stand on out there.
		grid.light.put(key(2, 3, 0), 0);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 3, 0, 16, 8);
		assertTrue(route.points() < 2.0);
		assertTrue(SleepShelterMath.wakeChance(route.points()) > 0.7);
		assertNull(route.breach());
	}

	@Test
	void aTorchByAHighWindowDoesNotSealTheRoom() {
		Grid grid = new Grid();
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				grid.kind.put(key(x, 3, z), SleepShelterMath.Kind.STONE);
				grid.kind.put(key(x, 6, z), SleepShelterMath.Kind.STONE);
			}
		}
		for (int y = 4; y <= 5; y++) {
			for (int z = -1; z <= 1; z++) {
				grid.kind.put(key(2, y, z), SleepShelterMath.Kind.FENCE);
				grid.kind.put(key(-2, y, z), SleepShelterMath.Kind.STONE);
			}
			for (int x = -1; x <= 1; x++) {
				grid.kind.put(key(x, y, 2), SleepShelterMath.Kind.STONE);
				grid.kind.put(key(x, y, -2), SleepShelterMath.Kind.STONE);
			}
		}
		grid.light.put(key(0, 4, 0), 15);
		grid.light.put(key(0, 5, 0), 15);
		// The torch shines through the glass. The real night is past that glow.
		// There is no floor outside, so the search cannot walk there.
		for (int x = 3; x <= 10; x++) {
			grid.light.put(key(x, 4, 0), 14 - (x - 3));
		}
		grid.light.put(key(11, 4, 0), 0);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 4, 0, 16, 8);
		assertTrue(route.points() < 8.0);
		assertTrue(SleepShelterMath.wakeChance(route.points()) > 0.25);
		assertTrue(route.breach() != null);
	}

	@Test
	void aHighWindowSeesTheNightOutside() {
		Grid grid = floor();
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				grid.kind.put(key(x, 3, z), SleepShelterMath.Kind.STONE);
			}
		}
		for (int z = -2; z <= 2; z++) {
			grid.kind.put(key(2, 4, z), SleepShelterMath.Kind.FENCE);
			grid.kind.put(key(2, 5, z), SleepShelterMath.Kind.FENCE);
		}
		grid.light.put(key(0, 4, 0), 15);
		grid.light.put(key(0, 5, 0), 15);
		grid.light.put(key(3, 4, 0), 4);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 4, 0, 16, 8);
		assertTrue(route.points() < 4.0);
		assertTrue(route.points() > 1.5);
		assertTrue(SleepShelterMath.wakeChance(route.points()) > 0.4);
	}

	@Test
	void eightBlocksOfOpenGroundIsOnePoint() {
		Grid grid = floor();
		for (int x = 1; x <= 7; x++) {
			grid.light.put(key(x, 1, 0), 15);
		}
		grid.light.put(key(8, 1, 0), 4);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 16, 4);
		assertEquals(1.0, route.points(), 1.0e-6);
		assertFalse(route.strike());
	}

	@Test
	void aLitCirclePricesTheRim() {
		Grid grid = floor();
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 4, 4);
		// Four steps stay inside the circle of 4. The fifth step leaves it: 5 / 8.
		assertEquals(0.625, route.points(), 1.0e-6);
		assertFalse(route.strike());
	}

	@Test
	void aWallThickerThanTheDigIsSealed() {
		Grid grid = floor();
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				if (x == 0 && z == 0) {
					continue;
				}
				grid.kind.put(key(x, 1, z), SleepShelterMath.Kind.TOUGH);
				grid.kind.put(key(x, 2, z), SleepShelterMath.Kind.TOUGH);
			}
		}
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 8, 4);
		assertEquals(SleepShelterMath.SEALED_POINTS, route.points(), 1.0e-9);
		assertFalse(route.strike());
		assertNull(route.breach());
	}

	@Test
	void aSealedStoneRoomBreaksTheNearestWall() {
		Grid grid = floor();
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				if (x == 0 && z == 0) {
					continue;
				}
				grid.kind.put(key(x, 1, z), SleepShelterMath.Kind.STONE);
				grid.kind.put(key(x, 2, z), SleepShelterMath.Kind.STONE);
			}
		}
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 8, 4);
		assertEquals(SleepShelterMath.SEALED_POINTS, route.points(), 1.0e-9);
		assertBreach(route, -1, 1, 0, -1, 2, 0);
	}

	private static void assertBreach(SleepShelterMath.Route route, int x, int y, int z, int x2, int y2, int z2) {
		SleepShelterMath.Breach breach = route.breach();
		assertTrue(breach != null && breach.second());
		assertEquals(x, breach.x());
		assertEquals(y, breach.y());
		assertEquals(z, breach.z());
		assertEquals(x2, breach.x2());
		assertEquals(y2, breach.y2());
		assertEquals(z2, breach.z2());
	}

	private static Grid floor() {
		Grid grid = new Grid();
		for (int x = -8; x <= 8; x++) {
			for (int z = -8; z <= 8; z++) {
				grid.kind.put(key(x, 0, z), SleepShelterMath.Kind.STONE);
			}
		}
		grid.light.put(key(0, 1, 0), 15);
		grid.light.put(key(0, 2, 0), 15);
		return grid;
	}

	/** A full-height block so the search cannot simply step over it. */
	private static void wall(Grid grid, int x, int z, SleepShelterMath.Kind kind) {
		grid.kind.put(key(x, 1, z), kind);
		grid.kind.put(key(x, 2, z), kind);
	}

	private static long key(int x, int y, int z) {
		return (((long) x & 0x3FFFFFFL) << 38) | (((long) y & 0xFFFL) << 26) | ((long) z & 0x3FFFFFFL);
	}

	private static final class Grid implements SleepShelterMath.View {
		final Map<Long, SleepShelterMath.Kind> kind = new HashMap<>();
		final Map<Long, Integer> light = new HashMap<>();
		final Map<Long, Boolean> sky = new HashMap<>();

		@Override
		public SleepShelterMath.Kind kind(int x, int y, int z) {
			return kind.getOrDefault(key(x, y, z), SleepShelterMath.Kind.AIR);
		}

		@Override
		public int light(int x, int y, int z) {
			return light.getOrDefault(key(x, y, z), 15);
		}

		@Override
		public boolean seesSky(int x, int y, int z) {
			return sky.getOrDefault(key(x, y, z), false);
		}
	}
}
