package com.example.sleep;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * How hard it is for the night to reach a bed.
 * The easiest path wins. Every {@link #HALVE_EVERY} points cuts the wake chance in half.
 * 0 points = 100%, 2 = 50%, 4 = 25%, 6 = 12.5%, 8 = 6%.
 *
 * <p>No Minecraft types here, so the numbers can be tested without starting the game.
 * What each block counts as is decided in {@link SleepShelter}.
 */
public final class SleepShelterMath {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — beds work as in the normal game. */
	public static final boolean ENABLED = true;

	/**
	 * How far from the bed the search looks, in blocks, flat along the ground.
	 * 60 = the yard, the fence, and the forest around a real base.
	 * Larger = you must push torches and fences farther out before the chance falls.
	 * Smaller = a small torch ring already looks like a finished fortress.
	 */
	public static final int RADIUS = 60;

	/**
	 * How far above the bed a hole to the sky still counts, in blocks.
	 * 12 = an open roof above the room counts. The search does not go under the floor.
	 * A mine, a cellar, and the floors below a tower do not change this bed.
	 */
	public static final int VERTICAL = 12;

	/**
	 * How far past a window or a wall the night still counts, in blocks.
	 * 16 = a torch beside a high window cannot hide the sky. One torch lights about 7 blocks,
	 * so the dark air beyond it is still inside this reach.
	 * There is no floor out there, and the search does not climb down to the ground.
	 * A second floor and a box in the sky do not need a stair.
	 */
	public static final int OUTSIDE = 16;

	/**
	 * Points added for every block of walking.
	 * 0.125 = 8 blocks add 1 point. The chance halves once for every 16 blocks of open ground.
	 * Higher = distance starts to matter sooner. Lower = a far dark spot is almost as bad as a near one.
	 */
	public static final double STEP_POINTS = 0.125;

	/**
	 * Points for crossing one fence, wall, pane, or closed gate.
	 * 2 = one ring is about 50% before distance is added. A second ring adds 2 again.
	 */
	public static final double FENCE_POINTS = 2.0;

	/**
	 * Points for a closed wooden door.
	 * 2 = a door with night right outside is about 50%, plus a little for the step through it.
	 */
	public static final double WOOD_DOOR_POINTS = 2.0;

	/**
	 * Points for a closed iron door.
	 * 4 = two halvings, about 25% before distance.
	 */
	public static final double IRON_DOOR_POINTS = 4.0;

	/**
	 * Points for one block of earth, wood, sand, or glass that must be broken.
	 * 3 = one block is about 35%, two blocks are about 12%.
	 */
	public static final double SOFT_WALL_POINTS = 3.0;

	/**
	 * Points for one stone or brick block that must be broken.
	 * 4 = one block is 25%, two blocks are about 6%.
	 */
	public static final double STONE_WALL_POINTS = 4.0;

	/**
	 * Points for one obsidian or bedrock block.
	 * 8 = a single block is about 6%, two blocks are about 0.4%.
	 */
	public static final double TOUGH_WALL_POINTS = 8.0;

	/**
	 * A floor block this bright or dimmer still counts as night.
	 * 7 = open ground at night (about 4) counts, and a torch (8 or brighter) does not.
	 * Higher = dim corners count too. Lower = only pitch-black spots count, so torches on open ground barely help.
	 */
	public static final int DARK_LIGHT = 7;

	/**
	 * How many solid blocks in a row the search will break through.
	 * 4 = a wall thicker than this, with no door and no way around, is treated as sealed.
	 */
	public static final int MAX_DIG = 4;

	/**
	 * When nothing can get in, how far from the bed to look for a wall to break, in blocks.
	 * 16 = a normal room. The hole is still two blocks, on the inside. Stone counts, not only a door or glass.
	 * 0 = a sealed room breaks nothing.
	 */
	public static final int SEALED_REACH = 16;

	/**
	 * A path with no door, fence, or wall counts as already inside
	 * when the dark spot is at most this many blocks from the bed.
	 * 2 = open ground, or a dark corner right beside the bed. Nothing is broken on that path.
	 * The mobs appear by the bed either way.
	 */
	public static final double STRIKE_RANGE = 2.0;

	/**
	 * If no path is found (the wall is thicker than {@link #MAX_DIG} and nothing can walk in),
	 * use this many points. 32 = about 0.002%. A sealed room almost never wakes you.
	 */
	public static final double SEALED_POINTS = 32.0;

	// ========================================================================

	private static final int VISIT_CAP = 80_000;
	private static final double COST_CAP = 40.0;

	private SleepShelterMath() {
	}

	/**
	 * How many points it takes to cut the wake chance in half.
	 * 2 = a wooden door with night outside is about 50%, and one stone wall is about 25%.
	 * 4 = the raised curve, where that same door was about 70%.
	 */
	public static final double HALVE_EVERY = 2.0;

	/** 0 points → 1.0 (100%). {@link #HALVE_EVERY} points → 0.5. Never below 0. */
	public static double wakeChance(double points) {
		if (points <= 0.0 || HALVE_EVERY <= 0.0) {
			return 1.0;
		}
		return Math.pow(0.5, points / HALVE_EVERY);
	}

	public enum Kind {
		AIR,
		SOFT,
		STONE,
		TOUGH,
		FENCE,
		WOOD_DOOR,
		IRON_DOOR,
		/** Ladder or stairs. Climbed like open ground, not broken like a wall. */
		CLIMB,
		/** Chunk not loaded. Counts as the edge of the night so the search does not load chunks. */
		UNLOADED
	}

	/** What the search is allowed to ask about the world. Coordinates are real block positions. */
	public interface View {
		Kind kind(int x, int y, int z);

		/** Effective light, 0–15. Night ground is about 4. A torch nearby is 8 or more. */
		int light(int x, int y, int z);

		boolean seesSky(int x, int y, int z);
	}

	/**
	 * The first door, fence, or wall on the winning path. At most two blocks.
	 * A door stores both halves. A wall stores the inner block and the one above it
	 * when that block is solid, otherwise the next block deeper in.
	 * Null when the night never had to break anything (open sky, dark room).
	 */
	public record Breach(int x, int y, int z, int x2, int y2, int z2, boolean second) {
		static Breach one(int x, int y, int z) {
			return new Breach(x, y, z, 0, Integer.MIN_VALUE, 0, false);
		}

		static Breach two(int x, int y, int z, int x2, int y2, int z2) {
			return new Breach(x, y, z, x2, y2, z2, true);
		}
	}

	/**
	 * The winning path.
	 *
	 * @param points how much defense that path has
	 * @param x      where the night arrives (the dark block, or the bed itself)
	 * @param strike true = the dark spot is already in the room
	 * @param found  false = nothing could get in; {@code points} is {@link #SEALED_POINTS} or the rim
	 * @param breach the door or wall to open, or null
	 */
	public record Route(double points, int x, int y, int z, boolean strike, boolean found, Breach breach) {
	}

	public static Route search(View view, int ox, int oy, int oz) {
		return search(view, ox, oy, oz, RADIUS, VERTICAL);
	}

	static Route search(View view, int ox, int oy, int oz, int radius, int vertical) {
		if (view.seesSky(ox, oy, oz) || view.seesSky(ox, oy + 1, oz)) {
			return new Route(0.0, ox, oy, oz, true, true, null);
		}
		int pillow = Math.max(view.light(ox, oy, oz), view.light(ox, oy + 1, oz));
		if (pillow <= DARK_LIGHT) {
			return new Route(0.0, ox, oy, oz, true, true, null);
		}

		PriorityQueue<Node> queue = new PriorityQueue<>();
		Map<Long, Double> best = new HashMap<>();
		expand(view, queue, best, ox, oy, oz, 0.0, null, ox, oy, oz, radius, vertical);

		int popped = 0;
		while (!queue.isEmpty()) {
			Node node = queue.poll();
			double known = best.getOrDefault(node.key, Double.POSITIVE_INFINITY);
			if (node.cost > known + 1.0e-9) {
				continue;
			}
			if (node.threat) {
				return arrive(node, ox, oz);
			}
			if (++popped > VISIT_CAP) {
				// Gave up before the edge. Price it like an open walk to the rim, not like a free skip.
				return new Route(radius * STEP_POINTS, ox + radius, oy, oz, false, false, null);
			}
			if (view.light(node.x, node.y, node.z) <= DARK_LIGHT) {
				return arrive(node, ox, oz);
			}
			expand(view, queue, best, node.x, node.y, node.z, node.cost, node.breach, ox, oy, oz, radius, vertical);
		}
		return new Route(SEALED_POINTS, ox, oy, oz, false, false, nearestWall(view, ox, oy, oz));
	}

	private static Route arrive(Node node, int ox, int oz) {
		boolean strike = node.breach == null && horizontal(node.x - ox, node.z - oz) <= STRIKE_RANGE;
		return new Route(node.cost, node.x, node.y, node.z, strike, true, node.breach);
	}

	private static void expand(View view, PriorityQueue<Node> queue, Map<Long, Double> best,
			int x, int y, int z, double cost, Breach breach,
			int ox, int oy, int oz, int radius, int vertical) {
		// Night air just outside this spot counts. No floor, no stair, no walk down to the ground.
		lookOut(view, queue, best, x, y, z, cost, breach, ox, oy, oz, radius, vertical);
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] dir : dirs) {
			int nx = x + dir[0];
			int nz = z + dir[1];
			Kind kind = view.kind(nx, y, nz);
			if (kind == Kind.UNLOADED) {
				offer(queue, best, nx, y, nz, cost + STEP_POINTS, breach, true);
				continue;
			}
			if (kind == Kind.CLIMB) {
				tryStand(view, queue, best, nx, y, nz, cost + STEP_POINTS, breach, ox, oy, oz, radius, vertical);
				tryStand(view, queue, best, nx, y + 1, nz, cost + STEP_POINTS, breach, ox, oy, oz, radius, vertical);
				tryStand(view, queue, best, nx, y - 1, nz, cost + STEP_POINTS, breach, ox, oy, oz, radius, vertical);
				continue;
			}
			if (isSolid(kind)) {
				tryStand(view, queue, best, nx, y + 1, nz, cost + STEP_POINTS, breach, ox, oy, oz, radius, vertical);
				dig(view, queue, best, x, y, z, dir[0], dir[1], cost, breach, ox, oy, oz, radius, vertical);
				continue;
			}
			if (kind == Kind.FENCE || kind == Kind.WOOD_DOOR || kind == Kind.IRON_DOOR) {
				double extra = switch (kind) {
					case FENCE -> FENCE_POINTS;
					case WOOD_DOOR -> WOOD_DOOR_POINTS;
					case IRON_DOOR -> IRON_DOOR_POINTS;
					default -> 0.0;
				};
				Breach hole = breach != null ? breach : opening(view, nx, y, nz, kind);
				int lx = nx + dir[0];
				int lz = nz + dir[1];
				double cross = cost + STEP_POINTS * 2.0 + extra;
				tryStand(view, queue, best, lx, y, lz, cross, hole, ox, oy, oz, radius, vertical);
				tryStand(view, queue, best, lx, y + 1, lz, cross, hole, ox, oy, oz, radius, vertical);
				tryStand(view, queue, best, lx, y - 1, lz, cross, hole, ox, oy, oz, radius, vertical);
				lookOut(view, queue, best, lx, y, lz, cross, hole, ox, oy, oz, radius, vertical);
				continue;
			}
			tryStand(view, queue, best, nx, y, nz, cost + STEP_POINTS, breach, ox, oy, oz, radius, vertical);
			tryStand(view, queue, best, nx, y + 1, nz, cost + STEP_POINTS, breach, ox, oy, oz, radius, vertical);
			tryStand(view, queue, best, nx, y - 1, nz, cost + STEP_POINTS, breach, ox, oy, oz, radius, vertical);
		}
	}

	/**
	 * Dark air near this spot is the night, even when nothing can stand there.
	 * A window on a tower and a box in the sky both end here.
	 */
	private static void lookOut(View view, PriorityQueue<Node> queue, Map<Long, Double> best,
			int x, int y, int z, double cost, Breach breach,
			int ox, int oy, int oz, int radius, int vertical) {
		if (OUTSIDE <= 0) {
			return;
		}
		if (countsAsNight(view, x, y, z, oy, vertical)) {
			offer(queue, best, x, y, z, cost, breach, true);
		}
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
		for (int[] dir : dirs) {
			for (int step = 1; step <= OUTSIDE; step++) {
				int cx = x + dir[0] * step;
				int cy = y + dir[1] * step;
				int cz = z + dir[2] * step;
				// Under the bed's floor nothing counts: not a mine, not a fence on the ground.
				if (cy < oy) {
					break;
				}
				Kind kind = view.kind(cx, cy, cz);
				if (kind == Kind.UNLOADED) {
					offer(queue, best, cx, cy, cz, cost + STEP_POINTS * step, breach, true);
					break;
				}
				if (!roomy(kind)) {
					break;
				}
				if (countsAsNight(view, cx, cy, cz, oy, vertical)) {
					offer(queue, best, cx, cy, cz, cost + STEP_POINTS * step, breach, true);
					break;
				}
			}
		}
	}

	/**
	 * Night on the bed's own floor, or the open sky above a hole in the roof.
	 * Dark air under the floor is not night. A cave stays dark and the bed does not care.
	 */
	private static boolean countsAsNight(View view, int x, int y, int z, int oy, int vertical) {
		if (y < oy || y - oy > vertical) {
			return false;
		}
		if (view.light(x, y, z) > DARK_LIGHT) {
			return false;
		}
		if (y <= oy + 1) {
			return true;
		}
		return view.seesSky(x, y, z);
	}

	/** A door is both halves. A fence is the one block they step through, plus another fence above it. */
	private static Breach opening(View view, int x, int y, int z, Kind kind) {
		if (kind == Kind.WOOD_DOOR || kind == Kind.IRON_DOOR) {
			return Breach.two(x, y, z, x, y + 1, z);
		}
		Kind above = view.kind(x, y + 1, z);
		if (above == Kind.FENCE || above == Kind.WOOD_DOOR || above == Kind.IRON_DOOR) {
			return Breach.two(x, y, z, x, y + 1, z);
		}
		return Breach.one(x, y, z);
	}

	private static void dig(View view, PriorityQueue<Node> queue, Map<Long, Double> best,
			int x, int y, int z, int dx, int dz, double cost, Breach breach,
			int ox, int oy, int oz, int radius, int vertical) {
		double dig = 0.0;
		int depth = 0;
		int wx = x + dx;
		int wz = z + dz;
		while (depth < MAX_DIG) {
			Kind kind = view.kind(wx, y, wz);
			if (kind == Kind.UNLOADED) {
				Breach hole = breach;
				if (hole == null && depth > 0) {
					hole = wallHole(view, x, y, z, dx, dz, depth);
				}
				offer(queue, best, wx, y, wz, cost + dig + STEP_POINTS * Math.max(depth, 1), hole, true);
				return;
			}
			if (!isSolid(kind)) {
				break;
			}
			dig += material(kind);
			depth++;
			wx += dx;
			wz += dz;
		}
		if (depth == 0) {
			return;
		}
		Breach hole = breach != null ? breach : wallHole(view, x, y, z, dx, dz, depth);
		double total = cost + dig + STEP_POINTS * depth;
		tryStand(view, queue, best, wx, y, wz, total, hole, ox, oy, oz, radius, vertical);
		tryStand(view, queue, best, wx, y + 1, wz, total, hole, ox, oy, oz, radius, vertical);
		tryStand(view, queue, best, wx, y - 1, wz, total, hole, ox, oy, oz, radius, vertical);
		lookOut(view, queue, best, wx, y, wz, total, hole, ox, oy, oz, radius, vertical);
	}

	/**
	 * The closest door, glass, or wall around the bed when no path can get in.
	 * Walks the air of the room, then takes the first block of the shell.
	 * Obsidian is skipped. The floor under the bed is not a wall.
	 */
	private static Breach nearestWall(View view, int ox, int oy, int oz) {
		if (SEALED_REACH <= 0) {
			return null;
		}
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		enqueueRoom(view, queue, seen, ox, oy, oz, ox, oy, oz);
		enqueueRoom(view, queue, seen, ox, oy + 1, oz, ox, oy, oz);
		int best = Integer.MAX_VALUE;
		int bx = 0;
		int by = 0;
		int bz = 0;
		Kind bestKind = null;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] cell = queue.poll();
			int x = cell[0];
			int y = cell[1];
			int z = cell[2];
			for (int[] dir : dirs) {
				int nx = x + dir[0];
				int nz = z + dir[1];
				Kind kind = view.kind(nx, y, nz);
				if (isShell(kind) && y >= oy && !(nx == ox && y == oy && nz == oz)) {
					int score = (nx - ox) * (nx - ox) + (nz - oz) * (nz - oz) + Math.abs(y - oy);
					if (betterWall(score, nx, nz, best, bx, bz)) {
						best = score;
						bx = nx;
						by = y;
						bz = nz;
						bestKind = kind;
					}
				}
				enqueueRoom(view, queue, seen, nx, y, nz, ox, oy, oz);
			}
			enqueueRoom(view, queue, seen, x, y + 1, z, ox, oy, oz);
			enqueueRoom(view, queue, seen, x, y - 1, z, ox, oy, oz);
		}
		if (bestKind == null) {
			return null;
		}
		if (bestKind == Kind.FENCE || bestKind == Kind.WOOD_DOOR || bestKind == Kind.IRON_DOOR) {
			return opening(view, bx, by, bz, bestKind);
		}
		if (isSolid(view.kind(bx, by + 1, bz)) || isShell(view.kind(bx, by + 1, bz))) {
			return Breach.two(bx, by, bz, bx, by + 1, bz);
		}
		return Breach.one(bx, by, bz);
	}

	private static boolean betterWall(int score, int x, int z, int best, int bx, int bz) {
		if (score != best) {
			return score < best;
		}
		if (x != bx) {
			return x < bx;
		}
		return z < bz;
	}

	private static void enqueueRoom(View view, ArrayDeque<int[]> queue, Set<Long> seen,
			int x, int y, int z, int ox, int oy, int oz) {
		if (Math.abs(y - oy) > 2 || horizontal(x - ox, z - oz) > SEALED_REACH) {
			return;
		}
		if (view.kind(x, y, z) != Kind.AIR) {
			return;
		}
		if (!seen.add(key(x, y, z))) {
			return;
		}
		queue.add(new int[] {x, y, z});
	}

	/** Stone, wood, glass, a fence, or a door. Obsidian and bedrock are left alone. */
	private static boolean isShell(Kind kind) {
		return kind == Kind.SOFT || kind == Kind.STONE
				|| kind == Kind.FENCE || kind == Kind.WOOD_DOOR || kind == Kind.IRON_DOOR;
	}

	/**
	 * Two blocks of the wall, chosen so the hole faces the room.
	 * The inner block and the one above it when the wall is tall.
	 * A low wall loses the inner block and the next one deeper in.
	 */
	private static Breach wallHole(View view, int x, int y, int z, int dx, int dz, int depth) {
		int fx = x + dx;
		int fz = z + dz;
		if (isSolid(view.kind(fx, y + 1, fz))) {
			return Breach.two(fx, y, fz, fx, y + 1, fz);
		}
		if (depth >= 2) {
			return Breach.two(fx, y, fz, x + dx * 2, y, z + dz * 2);
		}
		return Breach.one(fx, y, fz);
	}

	private static void tryStand(View view, PriorityQueue<Node> queue, Map<Long, Double> best,
			int x, int y, int z, double cost, Breach breach,
			int ox, int oy, int oz, int radius, int vertical) {
		if (cost > COST_CAP || y < oy || y > oy + 1) {
			return;
		}
		boolean outside = horizontal(x - ox, z - oz) > radius;
		if (outside) {
			if (view.kind(x, y, z) == Kind.UNLOADED || canStand(view, x, y, z)) {
				offer(queue, best, x, y, z, cost, breach, true);
			}
			return;
		}
		if (!canStand(view, x, y, z)) {
			return;
		}
		offer(queue, best, x, y, z, cost, breach, false);
	}

	private static boolean canStand(View view, int x, int y, int z) {
		if (!roomy(view.kind(x, y, z)) || !roomy(view.kind(x, y + 1, z))) {
			return false;
		}
		Kind below = view.kind(x, y - 1, z);
		return isSolid(below) || below == Kind.CLIMB;
	}

	/** Air, or a ladder or stair, which a body can occupy. */
	private static boolean roomy(Kind kind) {
		return kind == Kind.AIR || kind == Kind.CLIMB;
	}

	private static boolean isSolid(Kind kind) {
		return kind == Kind.SOFT || kind == Kind.STONE || kind == Kind.TOUGH;
	}

	private static double material(Kind kind) {
		return switch (kind) {
			case STONE -> STONE_WALL_POINTS;
			case TOUGH -> TOUGH_WALL_POINTS;
			default -> SOFT_WALL_POINTS;
		};
	}

	private static void offer(PriorityQueue<Node> queue, Map<Long, Double> best,
			int x, int y, int z, double cost, Breach breach, boolean threat) {
		if (cost > COST_CAP) {
			return;
		}
		long key = key(x, y, z);
		double previous = best.getOrDefault(key, Double.POSITIVE_INFINITY);
		if (cost >= previous - 1.0e-9) {
			return;
		}
		best.put(key, cost);
		queue.add(new Node(key, cost, x, y, z, breach, threat));
	}

	private static double horizontal(int dx, int dz) {
		return Math.sqrt((double) dx * dx + (double) dz * dz);
	}

	private static long key(int x, int y, int z) {
		return (((long) x & 0x3FFFFFFL) << 38) | (((long) y & 0xFFFL) << 26) | ((long) z & 0x3FFFFFFL);
	}

	private static final class Node implements Comparable<Node> {
		final long key;
		final double cost;
		final int x;
		final int y;
		final int z;
		final Breach breach;
		final boolean threat;

		Node(long key, double cost, int x, int y, int z, Breach breach, boolean threat) {
			this.key = key;
			this.cost = cost;
			this.x = x;
			this.y = y;
			this.z = z;
			this.breach = breach;
			this.threat = threat;
		}

		@Override
		public int compareTo(Node other) {
			return Double.compare(this.cost, other.cost);
		}
	}
}
