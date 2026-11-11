package com.example.sleep;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * How hard it is for the night to reach a bed.
 * The cheapest walkable path wins: along the ground, ladders, and stairs, inside {@link #RADIUS}.
 * A fence with a gap loses to the gap. A thick wall on the other side does not matter.
 * If the bed cannot be walked to (an upper floor with no stair, a walled hole),
 * the path comes to the column under or over the bed and then goes straight up or down that column.
 * A cave is walked along its own floor. The stone beside the tunnel is not on the path.
 *
 * <p>Until {@link #FLAT_UNTIL} points, each point removes {@link #PERCENT_PER_POINT} percent.
 * After that, every {@link #DROP_EVERY} points removes {@link #DROP_PERCENT} percent.
 * The chance never falls below {@link #MIN_PERCENT} percent.
 * 0 = 100%, 2 = 92%, 4 = 84%, 16 = 36%, 20 = 20%, 24 and above = 10%.
 *
 * <p>No Minecraft types here, so the numbers can be tested without starting the game.
 * What each block counts as is decided in {@link SleepShelter}.
 */
public final class SleepShelterMath {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — beds work as in the normal game. */
	public static final boolean ENABLED = true;

	/**
	 * How far from the bed the path is allowed to start, in blocks.
	 * 60 = a fence at the edge of the yard still counts. A gap there is the cheap way in.
	 * The shape is a circle. Larger = light and fences must cover a wider approach.
	 */
	public static final int RADIUS = 60;

	/**
	 * How far above and below the bed the walk and the straight column may go, in blocks.
	 * 96 = a cave or a tower within this band still connects.
	 * Farther than this, with no walkable way, the bed is priced at the 10% floor.
	 */
	public static final int VERTICAL = 96;

	/**
	 * How many solid blocks in a row a horizontal step will break through.
	 * 64 = a wall inside the circle is priced per block. The path still prefers a gap,
	 * because a gap costs nothing and a stone block costs {@link #STONE_WALL_POINTS}.
	 */
	public static final int MAX_CROSS = 64;

	/**
	 * Points for crossing one fence, wall, pane, or closed gate.
	 * 2 = one fence on the cheapest path is 92% before any light is added.
	 */
	public static final double FENCE_POINTS = 2.0;

	/**
	 * Points for a closed wooden door.
	 * 2 = a door by itself is 92%. Light and fences along the same path do the real work.
	 */
	public static final double WOOD_DOOR_POINTS = 2.0;

	/**
	 * Points for a closed iron door.
	 * 4 = an iron door by itself is 84%.
	 */
	public static final double IRON_DOOR_POINTS = 4.0;

	/**
	 * Points for one block of earth, wood, sand, or glass that the path breaks through.
	 * 3 = one wooden floor on the column up to the bed is 88% before anything else.
	 * A floor the column passes through counts. A tall tower gets safer from those floors.
	 */
	public static final double SOFT_WALL_POINTS = 3.0;

	/**
	 * Points for one stone or brick block the path breaks through.
	 * 4 = one block is 84%. Four blocks (a short dug roof) are 16 points, about 36%.
	 * Stone beside a tunnel the path does not enter does not count.
	 */
	public static final double STONE_WALL_POINTS = 4.0;

	/**
	 * Points for one obsidian or bedrock block.
	 * 8 = one block is 68%. Three blocks are 24 points and sit on the 10% floor.
	 * The block itself is not broken.
	 */
	public static final double TOUGH_WALL_POINTS = 8.0;

	/**
	 * A path block this bright adds {@link #LIGHT_POINTS}.
	 * 8 = a torch. Open night ground is about 4 and adds nothing.
	 * Only blocks on the cheapest path count. A torch on a side the path does not use adds nothing.
	 */
	public static final int LIT_LIGHT = 8;

	/**
	 * Points for one lit block on the path.
	 * 1 = forty lit blocks are 40 points. Together with a door (2) and two fences (4) that sits at 10%.
	 */
	public static final double LIGHT_POINTS = 1.0;

	/**
	 * How many percent one point removes, up to {@link #FLAT_UNTIL}.
	 * 4 = a wooden door (2 points) is 92%, one stone (4) is 84%, four stone (16) is 36%.
	 * Higher = the same house is woken less often. Lower = points barely help.
	 */
	public static final double PERCENT_PER_POINT = 4.0;

	/**
	 * Up to this many points, each point removes {@link #PERCENT_PER_POINT} percent.
	 * 20 = 20 points is 20%. The slower drop starts after this.
	 */
	public static final double FLAT_UNTIL = 20.0;

	/**
	 * After {@link #FLAT_UNTIL}, this many points remove {@link #DROP_PERCENT} from the chance.
	 * 4 = 24 points is already the 10% floor.
	 */
	public static final double DROP_EVERY = 4.0;

	/**
	 * How many percent each {@link #DROP_EVERY} points remove after {@link #FLAT_UNTIL}.
	 * 10 = the examples in {@link #DROP_EVERY}.
	 */
	public static final double DROP_PERCENT = 10.0;

	/**
	 * The chance of being woken never goes below this percent.
	 * 10 = a fortress still fails about one night in ten. It does not become zero.
	 */
	public static final double MIN_PERCENT = 10.0;

	// ========================================================================

	/** Enough for the ground inside the circle, plus ladders and a few cave floors. */
	private static final int VISIT_CAP = 80_000;

	/**
	 * Used when no walk and no column can leave the circle.
	 * 24 points is the first total that the formula already clamps to {@link #MIN_PERCENT}.
	 */
	private static final double CLOSED_POINTS = 24.0;

	private SleepShelterMath() {
	}

	/**
	 * Chance that the night wakes the sleeper, from 0.10 to 1.0.
	 * 0 points → 1.0. 2 → 0.92. 16 → 0.36. 20 → 0.20. 24 or more → 0.10.
	 */
	public static double wakeChance(double points) {
		if (points <= 0.0) {
			return 1.0;
		}
		double atBend = 100.0 - FLAT_UNTIL * PERCENT_PER_POINT;
		double percent = points <= FLAT_UNTIL
				? 100.0 - points * PERCENT_PER_POINT
				: atBend - ((points - FLAT_UNTIL) / DROP_EVERY) * DROP_PERCENT;
		if (percent < MIN_PERCENT) {
			percent = MIN_PERCENT;
		}
		return percent / 100.0;
	}

	public enum Kind {
		AIR,
		SOFT,
		STONE,
		TOUGH,
		FENCE,
		WOOD_DOOR,
		IRON_DOOR,
		/** Ladder or stairs. Walked, not broken. */
		CLIMB,
		/** Chunk not loaded. The circle ends here so the search does not load chunks. */
		UNLOADED
	}

	/** What the search is allowed to ask about the world. Coordinates are real block positions. */
	public interface View {
		Kind kind(int x, int y, int z);

		/** Effective light, 0–15. Open night ground is about 4. A torch is 8 or more. */
		int light(int x, int y, int z);

		boolean seesSky(int x, int y, int z);
	}

	/**
	 * The first door, fence, or wall on the winning path. At most two blocks.
	 * A door stores both halves. A wall stores the inner block and the one above it
	 * when that block is solid, otherwise the next block along the path.
	 * Null when the path crossed nothing (open ground).
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
	 * @param x      where the path leaves the circle
	 * @param strike true = nothing had to be broken
	 * @param found  false = no walk and no column could leave the circle
	 * @param breach the door or wall to open, or null
	 */
	public record Route(double points, int x, int y, int z, boolean strike, boolean found, Breach breach) {
	}

	public static Route search(View view, int ox, int oy, int oz) {
		return search(view, ox, oy, oz, RADIUS, VERTICAL);
	}

	static Route search(View view, int ox, int oy, int oz, int radius, int vertical) {
		PriorityQueue<Node> queue = new PriorityQueue<>();
		Map<Long, Double> best = new HashMap<>();
		offer(queue, best, ox, oy, oz, 0.0, null, false);

		int popped = 0;
		while (!queue.isEmpty()) {
			Node node = queue.poll();
			double known = best.getOrDefault(node.key, Double.POSITIVE_INFINITY);
			if (node.cost > known + 1.0e-9) {
				continue;
			}
			if (node.exit) {
				return new Route(node.cost, node.x, node.y, node.z, node.breach == null, true, node.breach);
			}
			if (++popped > VISIT_CAP) {
				// Gave up while the cheap paths were still open. Treat that as no defense
				// rather than as a sealed room.
				return new Route(0.0, ox, oy, oz, true, false, null);
			}
			expand(view, queue, best, node.x, node.y, node.z, node.cost, node.breach, ox, oy, oz, radius, vertical);
		}
		return new Route(CLOSED_POINTS, ox, oy, oz, false, false, null);
	}

	private static void expand(View view, PriorityQueue<Node> queue, Map<Long, Double> best,
			int x, int y, int z, double cost, Breach breach,
			int ox, int oy, int oz, int radius, int vertical) {
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] dir : dirs) {
			step(view, queue, best, x, y, z, dir[0], dir[1], cost, breach, ox, oy, oz, radius, vertical);
		}
		climb(view, queue, best, x, y, z, cost, breach, ox, oy, oz, radius, vertical);
		if (x == ox && z == oz) {
			column(view, queue, best, x, y, z, cost, breach, ox, oy, oz, radius, vertical, 1);
			column(view, queue, best, x, y, z, cost, breach, ox, oy, oz, radius, vertical, -1);
		}
	}

	/** One step along the ground: flat, one block up or down, or through a barrier. */
	private static void step(View view, PriorityQueue<Node> queue, Map<Long, Double> best,
			int x, int y, int z, int dx, int dz, double cost, Breach breach,
			int ox, int oy, int oz, int radius, int vertical) {
		int nx = x + dx;
		int nz = z + dz;
		if (leaves(nx, nz, ox, oz, radius)) {
			offer(queue, best, nx, y, nz, cost, breach, true);
			return;
		}
		Kind kind = view.kind(nx, y, nz);
		if (kind == Kind.UNLOADED) {
			offer(queue, best, nx, y, nz, cost, breach, true);
			return;
		}
		offerStand(view, queue, best, nx, y, nz, cost, breach, ox, oy, oz, radius, vertical);
		offerStand(view, queue, best, nx, y + 1, nz, cost, breach, ox, oy, oz, radius, vertical);
		offerStand(view, queue, best, nx, y - 1, nz, cost, breach, ox, oy, oz, radius, vertical);
		if (pays(kind)) {
			cross(view, queue, best, nx, y, nz, dx, dz, cost, breach, ox, oy, oz, radius, vertical);
		}
	}

	/** Break straight through a fence, a door, or a wall, then stand on the far side. */
	private static void cross(View view, PriorityQueue<Node> queue, Map<Long, Double> best,
			int x, int y, int z, int dx, int dz, double cost, Breach breach,
			int ox, int oy, int oz, int radius, int vertical) {
		double add = 0.0;
		Breach hole = breach;
		int cx = x;
		int cz = z;
		for (int depth = 0; depth < MAX_CROSS; depth++) {
			if (leaves(cx, cz, ox, oz, radius)) {
				offer(queue, best, cx, y, cz, cost + add, hole, true);
				return;
			}
			Kind kind = view.kind(cx, y, cz);
			if (kind == Kind.UNLOADED) {
				offer(queue, best, cx, y, cz, cost + add, hole, true);
				return;
			}
			if (!pays(kind)) {
				break;
			}
			add += price(kind) + lightPoints(view, cx, y, cz);
			if (hole == null) {
				hole = holeFor(view, cx, y, cz, kind, dx, 0, dz);
			}
			cx += dx;
			cz += dz;
		}
		offerStand(view, queue, best, cx, y, cz, cost + add, hole, ox, oy, oz, radius, vertical);
		offerStand(view, queue, best, cx, y + 1, cz, cost + add, hole, ox, oy, oz, radius, vertical);
		offerStand(view, queue, best, cx, y - 1, cz, cost + add, hole, ox, oy, oz, radius, vertical);
	}

	/** A ladder or a stair joins the floors. It is not a wall. */
	private static void climb(View view, PriorityQueue<Node> queue, Map<Long, Double> best,
			int x, int y, int z, double cost, Breach breach,
			int ox, int oy, int oz, int radius, int vertical) {
		if (view.kind(x, y, z) == Kind.CLIMB || view.kind(x, y + 1, z) == Kind.CLIMB) {
			offerStand(view, queue, best, x, y + 1, z, cost, breach, ox, oy, oz, radius, vertical);
		}
		if (view.kind(x, y, z) == Kind.CLIMB || view.kind(x, y - 1, z) == Kind.CLIMB) {
			offerStand(view, queue, best, x, y - 1, z, cost, breach, ox, oy, oz, radius, vertical);
		}
	}

	/**
	 * Straight up or down the bed's own column, when the floors are not a walk.
	 * Each block on the way adds its points. Stops at the first place a person can stand,
	 * and the ground path continues from there. A walk that already reached the bed
	 * is a different route; the cheaper of the two wins.
	 */
	private static void column(View view, PriorityQueue<Node> queue, Map<Long, Double> best,
			int x, int y, int z, double cost, Breach breach,
			int ox, int oy, int oz, int radius, int vertical, int dir) {
		double add = 0.0;
		Breach hole = breach;
		int cy = y;
		for (int step = 0; step < vertical; step++) {
			cy += dir;
			if (cy < oy - vertical || cy > oy + vertical) {
				return;
			}
			Kind kind = view.kind(x, cy, z);
			if (kind == Kind.UNLOADED) {
				return;
			}
			if (canStand(view, x, cy, z)) {
				offer(queue, best, x, cy, z, cost + add + lightPoints(view, x, cy, z), hole, false);
				return;
			}
			if (pays(kind)) {
				add += price(kind) + lightPoints(view, x, cy, z);
				if (hole == null) {
					hole = holeFor(view, x, cy, z, kind, 0, dir, 0);
				}
				continue;
			}
			add += lightPoints(view, x, cy, z);
		}
	}

	/** Stand here and keep walking. Lit ground adds its point. The block underfoot is not a wall. */
	private static void offerStand(View view, PriorityQueue<Node> queue, Map<Long, Double> best,
			int x, int y, int z, double cost, Breach breach,
			int ox, int oy, int oz, int radius, int vertical) {
		if (y < oy - vertical || y > oy + vertical) {
			return;
		}
		if (!canStand(view, x, y, z)) {
			return;
		}
		if (leaves(x, z, ox, oz, radius)) {
			offer(queue, best, x, y, z, cost, breach, true);
			return;
		}
		offer(queue, best, x, y, z, cost + lightPoints(view, x, y, z), breach, false);
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

	private static boolean pays(Kind kind) {
		return price(kind) > 0.0;
	}

	private static double price(Kind kind) {
		return switch (kind) {
			case FENCE -> FENCE_POINTS;
			case WOOD_DOOR -> WOOD_DOOR_POINTS;
			case IRON_DOOR -> IRON_DOOR_POINTS;
			case STONE -> STONE_WALL_POINTS;
			case TOUGH -> TOUGH_WALL_POINTS;
			case SOFT -> SOFT_WALL_POINTS;
			default -> 0.0;
		};
	}

	private static double lightPoints(View view, int x, int y, int z) {
		return view.light(x, y, z) >= LIT_LIGHT ? LIGHT_POINTS : 0.0;
	}

	/** A door is both halves. A fence is the block they step through, plus another fence above it. */
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

	/**
	 * The first block on the way in, and a second one so the hole is about as tall as a player.
	 * The second block is the one above when the barrier is tall, otherwise the next block along the path.
	 */
	private static Breach holeFor(View view, int x, int y, int z, Kind kind, int dx, int dy, int dz) {
		if (kind == Kind.FENCE || kind == Kind.WOOD_DOOR || kind == Kind.IRON_DOOR) {
			return opening(view, x, y, z, kind);
		}
		if (isSolid(view.kind(x, y + 1, z))) {
			return Breach.two(x, y, z, x, y + 1, z);
		}
		int nx = x + dx;
		int ny = y + dy;
		int nz = z + dz;
		if (isSolid(view.kind(nx, ny, nz))) {
			return Breach.two(x, y, z, nx, ny, nz);
		}
		return Breach.one(x, y, z);
	}

	private static void offer(PriorityQueue<Node> queue, Map<Long, Double> best,
			int x, int y, int z, double cost, Breach breach, boolean exit) {
		long key = key(x, y, z);
		double previous = best.getOrDefault(key, Double.POSITIVE_INFINITY);
		if (cost >= previous - 1.0e-9) {
			return;
		}
		best.put(key, cost);
		queue.add(new Node(key, cost, x, y, z, breach, exit));
	}

	private static boolean leaves(int x, int z, int ox, int oz, int radius) {
		return horizontal(x - ox, z - oz) > radius;
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
		final boolean exit;

		Node(long key, double cost, int x, int y, int z, Breach breach, boolean exit) {
			this.key = key;
			this.cost = cost;
			this.x = x;
			this.y = y;
			this.z = z;
			this.breach = breach;
			this.exit = exit;
		}

		@Override
		public int compareTo(Node other) {
			return Double.compare(this.cost, other.cost);
		}
	}
}
