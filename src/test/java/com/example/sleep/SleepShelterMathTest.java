package com.example.sleep;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cheapest path to the bed, without a running game.
 * Feet are at y = 1 unless a test says otherwise. Light defaults to night (0).
 * A lit block is 8 or brighter and costs 1 point. The ground underfoot is not a wall.
 */
class SleepShelterMathTest {

	@Test
	void theSlowCurveBottomsAtTenPercent() {
		assertEquals(1.00, SleepShelterMath.wakeChance(0.0), 1.0e-9);
		assertEquals(0.92, SleepShelterMath.wakeChance(2.0), 1.0e-9);
		assertEquals(0.84, SleepShelterMath.wakeChance(4.0), 1.0e-9);
		assertEquals(0.76, SleepShelterMath.wakeChance(6.0), 1.0e-9);
		assertEquals(0.36, SleepShelterMath.wakeChance(16.0), 1.0e-9);
		assertEquals(0.20, SleepShelterMath.wakeChance(20.0), 1.0e-9);
		assertEquals(0.10, SleepShelterMath.wakeChance(24.0), 1.0e-9);
		assertEquals(0.10, SleepShelterMath.wakeChance(46.0), 1.0e-9);
		assertEquals(0.10, SleepShelterMath.wakeChance(80.0), 1.0e-9);
	}

	@Test
	void openGroundIsCertain() {
		Grid grid = ground(8);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 8, 16);
		assertEquals(0.0, route.points(), 1.0e-9);
		assertEquals(1.0, SleepShelterMath.wakeChance(route.points()), 1.0e-9);
		assertNull(route.breach());
	}

	@Test
	void aLitApproachCostsOnePointPerBlock() {
		Grid grid = ground(4);
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				grid.light.put(key(x, 1, z), 15);
			}
		}
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 4, 8);
		assertEquals(4.0, route.points(), 1.0e-9);
		assertEquals(0.84, SleepShelterMath.wakeChance(route.points()), 1.0e-9);
		assertNull(route.breach());
	}

	@Test
	void aDarkGapBeatsALitDoor() {
		Grid grid = ground(6);
		ring(grid, 3, SleepShelterMath.Kind.STONE);
		grid.kind.put(key(3, 1, 0), SleepShelterMath.Kind.WOOD_DOOR);
		grid.kind.put(key(3, 2, 0), SleepShelterMath.Kind.WOOD_DOOR);
		grid.kind.put(key(3, 1, 2), SleepShelterMath.Kind.AIR);
		grid.kind.put(key(3, 2, 2), SleepShelterMath.Kind.AIR);
		for (int x = 4; x <= 6; x++) {
			grid.light.put(key(x, 1, 0), 15);
		}
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 6, 8);
		assertEquals(0.0, route.points(), 1.0e-9);
		assertNull(route.breach());
	}

	@Test
	void aWoodenDoorIsTwoPoints() {
		Grid grid = ground(6);
		ring(grid, 3, SleepShelterMath.Kind.STONE);
		grid.kind.put(key(3, 1, 0), SleepShelterMath.Kind.WOOD_DOOR);
		grid.kind.put(key(3, 2, 0), SleepShelterMath.Kind.WOOD_DOOR);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 6, 8);
		assertEquals(2.0, route.points(), 1.0e-9);
		assertEquals(0.92, SleepShelterMath.wakeChance(route.points()), 1.0e-9);
		assertBreach(route, 3, 1, 0, 3, 2, 0);
	}

	@Test
	void anIronDoorIsFourPoints() {
		Grid grid = ground(6);
		// Obsidian, not stone: a stone block is also 4, so the door would not be the only cheap way.
		ring(grid, 3, SleepShelterMath.Kind.TOUGH);
		grid.kind.put(key(3, 1, 0), SleepShelterMath.Kind.IRON_DOOR);
		grid.kind.put(key(3, 2, 0), SleepShelterMath.Kind.IRON_DOOR);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 6, 8);
		assertEquals(4.0, route.points(), 1.0e-9);
		assertBreach(route, 3, 1, 0, 3, 2, 0);
	}

	@Test
	void twoFencesAddFour() {
		Grid grid = ground(8);
		ring(grid, 2, SleepShelterMath.Kind.FENCE);
		ring(grid, 4, SleepShelterMath.Kind.FENCE);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 8, 8);
		assertEquals(4.0, route.points(), 1.0e-9);
		assertEquals(0.84, SleepShelterMath.wakeChance(route.points()), 1.0e-9);
	}

	@Test
	void fourStoneBlocksOnTheColumnAreSixteenPoints() {
		Grid grid = new Grid();
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				grid.kind.put(key(x, 5, z), SleepShelterMath.Kind.STONE);
			}
		}
		grid.kind.put(key(0, 0, 0), SleepShelterMath.Kind.STONE);
		for (int y = 2; y <= 5; y++) {
			grid.kind.put(key(0, y, 0), SleepShelterMath.Kind.STONE);
		}
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 6, 16);
		assertEquals(16.0, route.points(), 1.0e-9);
		assertEquals(0.36, SleepShelterMath.wakeChance(route.points()), 1.0e-9);
		assertTrue(route.breach() != null);
	}

	@Test
	void aCaveTunnelIgnoresTheRockBesideIt() {
		Grid grid = new Grid();
		for (int x = -8; x <= 8; x++) {
			for (int z = -2; z <= 2; z++) {
				grid.kind.put(key(x, 0, z), SleepShelterMath.Kind.STONE);
				grid.kind.put(key(x, 3, z), SleepShelterMath.Kind.STONE);
				grid.kind.put(key(x, 4, z), SleepShelterMath.Kind.STONE);
			}
		}
		for (int x = -8; x <= 8; x++) {
			wall(grid, x, 2, SleepShelterMath.Kind.STONE);
			wall(grid, x, -2, SleepShelterMath.Kind.STONE);
		}
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 6, 16);
		assertEquals(0.0, route.points(), 1.0e-9);
		assertEquals(1.0, SleepShelterMath.wakeChance(route.points()), 1.0e-9);
		assertNull(route.breach());
	}

	@Test
	void anUpperFloorUsesTheGroundFence() {
		Grid grid = ground(8);
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				grid.kind.put(key(x, 4, z), SleepShelterMath.Kind.STONE);
			}
		}
		for (int i = -8; i <= 8; i++) {
			wall(grid, 5, i, SleepShelterMath.Kind.FENCE);
			wall(grid, -5, i, SleepShelterMath.Kind.FENCE);
			wall(grid, i, 5, SleepShelterMath.Kind.FENCE);
			wall(grid, i, -5, SleepShelterMath.Kind.FENCE);
		}
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 5, 0, 8, 16);
		assertEquals(6.0, route.points(), 1.0e-9);
		assertEquals(0.76, SleepShelterMath.wakeChance(route.points()), 1.0e-9);
		assertTrue(route.breach() != null);
	}

	@Test
	void aGapInTheGroundFenceLeavesTheOpenSide() {
		Grid grid = ground(8);
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				grid.kind.put(key(x, 4, z), SleepShelterMath.Kind.STONE);
			}
		}
		for (int i = -8; i <= 8; i++) {
			wall(grid, 5, i, SleepShelterMath.Kind.FENCE);
		}
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 5, 0, 8, 16);
		assertEquals(4.0, route.points(), 1.0e-9);
		assertEquals(0.84, SleepShelterMath.wakeChance(route.points()), 1.0e-9);
	}

	@Test
	void stairsAreAWalkNotAWall() {
		Grid grid = new Grid();
		for (int x = -2; x <= 1; x++) {
			for (int z = -2; z <= 2; z++) {
				grid.kind.put(key(x, 0, z), SleepShelterMath.Kind.STONE);
			}
		}
		for (int x = 1; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				grid.kind.put(key(x, 2, z), SleepShelterMath.Kind.STONE);
			}
		}
		grid.kind.put(key(1, 1, 0), SleepShelterMath.Kind.CLIMB);
		grid.kind.put(key(1, 2, 0), SleepShelterMath.Kind.CLIMB);
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 1, 0, 6, 8);
		assertEquals(0.0, route.points(), 1.0e-9);
		assertNull(route.breach());
	}

	@Test
	void aBoxWithNoStairIsTheColumnThroughItsFloor() {
		Grid grid = ground(8);
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				grid.kind.put(key(x, 3, z), SleepShelterMath.Kind.STONE);
				grid.kind.put(key(x, 6, z), SleepShelterMath.Kind.STONE);
			}
		}
		for (int y = 4; y <= 5; y++) {
			for (int z = -1; z <= 1; z++) {
				grid.kind.put(key(2, y, z), SleepShelterMath.Kind.STONE);
				grid.kind.put(key(-2, y, z), SleepShelterMath.Kind.STONE);
			}
			for (int x = -1; x <= 1; x++) {
				grid.kind.put(key(x, y, 2), SleepShelterMath.Kind.STONE);
				grid.kind.put(key(x, y, -2), SleepShelterMath.Kind.STONE);
			}
		}
		SleepShelterMath.Route route = SleepShelterMath.search(grid, 0, 4, 0, 8, 16);
		assertEquals(4.0, route.points(), 1.0e-9);
		assertEquals(0.84, SleepShelterMath.wakeChance(route.points()), 1.0e-9);
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

	/** Stone under y = 1, out to the circle, so the path can walk to the edge. */
	private static Grid ground(int radius) {
		Grid grid = new Grid();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				grid.kind.put(key(x, 0, z), SleepShelterMath.Kind.STONE);
			}
		}
		return grid;
	}

	/** A block and the one above it, so the path cannot step over. */
	private static void wall(Grid grid, int x, int z, SleepShelterMath.Kind kind) {
		grid.kind.put(key(x, 1, z), kind);
		grid.kind.put(key(x, 2, z), kind);
	}

	/** A square ring. The path has to cross it; an open side of the map is not a way around. */
	private static void ring(Grid grid, int at, SleepShelterMath.Kind kind) {
		for (int i = -at; i <= at; i++) {
			wall(grid, at, i, kind);
			wall(grid, -at, i, kind);
			wall(grid, i, at, kind);
			wall(grid, i, -at, kind);
		}
	}

	private static long key(int x, int y, int z) {
		return (((long) x & 0x3FFFFFFL) << 38) | (((long) y & 0xFFFL) << 26) | ((long) z & 0x3FFFFFFL);
	}

	private static final class Grid implements SleepShelterMath.View {
		final Map<Long, SleepShelterMath.Kind> kind = new HashMap<>();
		final Map<Long, Integer> light = new HashMap<>();

		@Override
		public SleepShelterMath.Kind kind(int x, int y, int z) {
			return kind.getOrDefault(key(x, y, z), SleepShelterMath.Kind.AIR);
		}

		@Override
		public int light(int x, int y, int z) {
			return light.getOrDefault(key(x, y, z), 0);
		}

		@Override
		public boolean seesSky(int x, int y, int z) {
			return false;
		}
	}
}
