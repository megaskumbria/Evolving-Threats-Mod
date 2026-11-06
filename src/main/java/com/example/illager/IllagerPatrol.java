package com.example.illager;

import com.example.advancement.ModAdvancements;
import com.example.entity.IllagerPatrolMember;
import com.example.siege.SiegeNight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 The squad of the player: robbers, champions and one destroyer.
 Appears during the day away from the player and goes to the point where he stood, including if the house is in the village.
 The usual patrol of the game (only robbers and only away from the village) does not turn off.
 */
public final class IllagerPatrol {

	// ===================== SETTINGS (change these numbers) =====================

	/** False - such groups do not appear. Those who are already out come to the player as before. */
	public static final boolean ENABLED = true;

	/**
	 The shortest break between squads, in game days.
	 * 8 = eight days. One day lasts 20 minutes. Less, more squads.
	 */
	public static final int DAYS_MIN = 8;

	/**
	 The longest pause between squads, on game days.
	 * 14 = two weeks. Each time a random number is taken from {@link #DAYS MIN} before that.
	 If you put less than the minimum, the game will take the minimum.
	 */
	public static final int DAYS_MAX = 14;

	/**
	 * Check without waiting days. 0 = normal random pause {@link #DAYS MIN}-{@link #DAYS MAX}.
	 * 200 = next squad in 10 seconds. After checking, return 0.
	 */
	public static final int TEST_ATTEMPT_TICKS = 0;

	/**
	 How often to try again if time is up, but it's night, there's no room or player in the creative.
	 * Ticks. 1200 = once per minute until it works. It's not a new pause for days.
	 */
	public static final int RETRY_TICKS = 1200;

	/**
	 True - only during the day, like old patrols. False - and at night too.
	 */
	public static final boolean DAYTIME_ONLY = true;

	/**
	 How far from the player to look for a place in blocks.
	 64 and 96 = four or six chanks: the squad can be seen from afar, it does not appear in the courtyard.
	 If you put the distance of loading, they will appear and will stand until the Chunk is revived.
	 */
	public static final int SPAWN_DISTANCE_MIN = 64;
	public static final int SPAWN_DISTANCE_MAX = 96;

	/**
	 How many men are in the squad. 2 and 3 = two or three.
	 */
	public static final int VINDICATOR_MIN = 2;
	public static final int VINDICATOR_MAX = 3;

	/**
	 How many robbers are in the squad. 2 and 3 = two or three.
	 One of them is a captain with a banner: an ominous bottle still falls from him.
	 */
	public static final int PILLAGER_MIN = 2;
	public static final int PILLAGER_MAX = 3;

	/**
	 How many ruiners. 1 = one in each order. 0 = no ruiner.
	 */
	public static final int RAVAGER_COUNT = 1;

	/**
	 How many blocks do you have to go to the remembered point to stop?
	 12 = yard by the house, not one block under the feet. Less crowded in the same cage.
	 */
	public static final int ARRIVE_BLOCKS = 12;

	/**
	 * How close (blocks) a player must be to the house point when the squad is in the yard
	 * for the advancement to count. 48 = the house and the ground around it.
	 * Farther than this, the squad reached an empty spot and the card does not fire.
	 * A member in an unloaded chunk is invisible, so a missing ravager there abandons the squad.
	 */
	public static final double PATROL_WITNESS_BLOCKS = 48.0;

	/**
	 * Step speed to point. 0.6 = normal step. More, almost running.
	 */
	public static final double MARCH_SPEED = 0.6;

	/**
	 If there is already a squad in this radius around the player, a new one does not come out.
	 * 140 = a little further away than {@link #SPAWN DISTANCE MAX} so that the band still counts as "next".
	 */
	public static final double NEARBY_BLOCKS = 140.0;

	/*How many points to go looking for a clearing under the squad. */
	public static final int SPOT_TRIES = 24;

	/*How many points to pick for one mob near the center of the squad. */
	public static final int MEMBER_TRIES = 12;

	/*How many blocks can the members of the squad stand aside from the destroyer? */
	public static final int CLUSTER_RADIUS = 4;

	// ========================================================================

	/*One game day. 24,000 tics = 20 minutes. */
	private static final int TICKS_PER_DAY = 24000;

	private static final WeakHashMap<ServerLevel, Integer> TICKS_LEFT = new WeakHashMap<>();

	private IllagerPatrol() {
	}

	/*End-of-tick of the world. In hell and in the region, the squad does not go out. */
	public static void tick(ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		// A squad already on the road can still finish after the mechanic is turned off.
		noticeArrivals(level);
		if (!ENABLED) {
			return;
		}
		Integer left = TICKS_LEFT.get(level);
		if (left == null) {
			TICKS_LEFT.put(level, rollDelay(level.getRandom()));
			return;
		}
		if (left > 1) {
			TICKS_LEFT.put(level, left - 1);
			return;
		}
		if (trySpawn(level)) {
			TICKS_LEFT.put(level, rollDelay(level.getRandom()));
		} else {
			TICKS_LEFT.put(level, Math.max(1, RETRY_TICKS));
		}
	}

	/*Accidental pause: from Days MIN to Days MAX game days. The test has fixed ticks. */
	private static int rollDelay(RandomSource random) {
		if (TEST_ATTEMPT_TICKS > 0) {
			return TEST_ATTEMPT_TICKS;
		}
		int min = Math.max(1, DAYS_MIN);
		int max = Math.max(min, DAYS_MAX);
		return random.nextIntBetweenInclusive(min, max) * TICKS_PER_DAY;
	}

	/**
	 Call the squad right now for the test team.
	 A day, creativity and a pause of a few days do not interfere here.
	 * The squad still appears at {@link #SPAWN DISTANCE MIN}-{@link #SPAWN DISTANCE MAX}
	 Blocks and goes to the point where the player is at that moment.
	 *
	 * @return false if there is no free land nearby
	 */
	public static boolean summonFor(ServerLevel level, ServerPlayer player) {
		BlockPos center = findClusterCenter(level, player);
		if (center == null) {
			return false;
		}
		return spawnSquad(level, player, center);
	}

	/*True - the squad is out, and the next pause is again random days. */
	private static boolean trySpawn(ServerLevel level) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		// Squads don't go under siege. We'll try again through Retry TICKS, after dawn.
		if (SiegeNight.isWindowOpen(level)) {
			return false;
		}
		if (DAYTIME_ONLY && !level.isBrightOutside()) {
			return false;
		}
		if (!Boolean.TRUE.equals(level.getGameRules().get(GameRules.SPAWN_PATROLS))) {
			return false;
		}
		List<ServerPlayer> players = eligiblePlayers(level);
		if (players.isEmpty()) {
			return false;
		}
		ServerPlayer player = players.get(level.getRandom().nextInt(players.size()));
		if (patrolAlreadyNearby(level, player)) {
			return false;
		}
		BlockPos center = findClusterCenter(level, player);
		if (center == null) {
			return false;
		}
		return spawnSquad(level, player, center);
	}

	private static List<ServerPlayer> eligiblePlayers(ServerLevel level) {
		List<ServerPlayer> players = new ArrayList<>();
		for (Player player : level.players()) {
			if (player instanceof ServerPlayer serverPlayer
					&& serverPlayer.isAlive()
					&& !serverPlayer.isSpectator()
					&& !serverPlayer.isCreative()) {
				players.add(serverPlayer);
			}
		}
		return players;
	}

	private static boolean patrolAlreadyNearby(ServerLevel level, ServerPlayer player) {
		AABB area = player.getBoundingBox().inflate(NEARBY_BLOCKS);
		return !level.getEntitiesOfClass(Raider.class, area, raider ->
				raider instanceof IllagerPatrolMember member
						&& member.evolvingthreats$getPatrolDestination() != null
						&& raider.isAlive()).isEmpty();
	}

	private static BlockPos findClusterCenter(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		int min = Math.max(1, SPAWN_DISTANCE_MIN);
		int max = Math.max(min, SPAWN_DISTANCE_MAX);
		for (int attempt = 0; attempt < SPOT_TRIES; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = min + random.nextDouble() * (max - min);
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (canStand(level, surface, EntityTypes.RAVAGER)) {
				return surface;
			}
		}
		return null;
	}

	private static boolean spawnSquad(ServerLevel level, ServerPlayer player, BlockPos center) {
		RandomSource random = level.getRandom();
		int pillagers = rollCount(random, PILLAGER_MIN, PILLAGER_MAX);
		int vindicators = rollCount(random, VINDICATOR_MIN, VINDICATOR_MAX);
		int ravagers = Math.max(0, RAVAGER_COUNT);
		List<EntityType<? extends Raider>> members = new ArrayList<>();
		for (int i = 0; i < pillagers; i++) {
			members.add(EntityTypes.PILLAGER);
		}
		for (int i = 0; i < vindicators; i++) {
			members.add(EntityTypes.VINDICATOR);
		}
		for (int i = 0; i < ravagers; i++) {
			members.add(EntityTypes.RAVAGER);
		}
		boolean needCaptain = pillagers > 0;
		boolean ravagerAtCenter = false;
		boolean spawned = false;
		for (EntityType<? extends Raider> type : members) {
			BlockPos pos;
			if (type == EntityTypes.RAVAGER && !ravagerAtCenter) {
				pos = center;
				ravagerAtCenter = true;
			} else {
				pos = findMemberSpot(level, center, type);
			}
			if (pos == null || !canStand(level, pos, type)) {
				continue;
			}
			boolean captain = needCaptain && type == EntityTypes.PILLAGER;
			if (spawnOne(level, player, pos, type, captain)) {
				spawned = true;
				if (captain) {
					needCaptain = false;
				}
			}
		}
		return spawned;
	}

	private static int rollCount(RandomSource random, int min, int max) {
		int low = Math.max(0, min);
		int high = Math.max(low, max);
		return random.nextIntBetweenInclusive(low, high);
	}

	private static BlockPos findMemberSpot(ServerLevel level, BlockPos center, EntityType<?> type) {
		RandomSource random = level.getRandom();
		int radius = Math.max(1, CLUSTER_RADIUS);
		for (int attempt = 0; attempt < MEMBER_TRIES; attempt++) {
			int dx = random.nextInt(radius * 2 + 1) - radius;
			int dz = random.nextInt(radius * 2 + 1) - radius;
			BlockPos surface = level.getHeightmapPos(
					Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					center.offset(dx, 0, dz));
			if (canStand(level, surface, type)) {
				return surface;
			}
		}
		return null;
	}

	private static boolean spawnOne(
			ServerLevel level,
			ServerPlayer player,
			BlockPos pos,
			EntityType<? extends Raider> type,
			boolean captain) {
		Entity created = type.create(level, EntitySpawnReason.PATROL);
		if (!(created instanceof Raider raider)) {
			return false;
		}
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		float yaw = (float) (Mth.atan2(player.getZ() - z, player.getX() - x) * (180.0 / Math.PI)) - 90.0f;
		raider.snapTo(x, pos.getY(), z, yaw, 0.0f);
		if (captain) {
			// Until finalizeSpawn: The game hangs the captain's flag only if the flag is already standing.
			raider.setPatrolLeader(true);
		}
		if (raider instanceof IllagerPatrolMember patrol) {
			// The point where the player is standing right now. She doesn't follow him any further.
			patrol.evolvingthreats$setPatrolDestination(player.blockPosition());
		}
		raider.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.PATROL, null);
		raider.setCanJoinRaid(false);
		level.addFreshEntity(raider);
		return true;
	}

	/**
	 * A member is standing in the yard. Pillagers often get there first; the card waits
	 * until a vindicator and the ravager are in the same yard. Then every member forgets
	 * the point, including anyone still on the road, so the next squad is not blocked.
	 */
	private static void noticeArrivals(ServerLevel level) {
		double search = Math.max(0.0, PATROL_WITNESS_BLOCKS) + Math.max(0, ARRIVE_BLOCKS);
		java.util.Set<BlockPos> yards = new java.util.HashSet<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			AABB aroundPlayer = player.getBoundingBox().inflate(search);
			for (Raider raider : level.getEntitiesOfClass(Raider.class, aroundPlayer, IllagerPatrol::isMarching)) {
				BlockPos destination = ((IllagerPatrolMember) raider).evolvingthreats$getPatrolDestination();
				if (destination == null) {
					continue;
				}
				double reach = Math.max(0, ARRIVE_BLOCKS);
				if (raider.blockPosition().distSqr(destination) <= reach * reach) {
					yards.add(destination);
				}
			}
		}
		for (BlockPos destination : yards) {
			finishIfReady(level, destination);
		}
	}

	private static void finishIfReady(ServerLevel level, BlockPos destination) {
		double wide = Math.max(NEARBY_BLOCKS, SPAWN_DISTANCE_MAX);
		List<Raider> squad = level.getEntitiesOfClass(Raider.class, new AABB(destination).inflate(wide),
				raider -> isMarchingTo(raider, destination));
		List<PatrolArrival.Member> members = new ArrayList<>(squad.size());
		for (Raider raider : squad) {
			members.add(new PatrolArrival.Member(
					raider.isAlive(),
					kindOf(raider.getType()),
					raider.blockPosition().distSqr(destination)));
		}
		PatrolArrival.Verdict verdict = PatrolArrival.verdict(members, ARRIVE_BLOCKS);
		if (verdict == PatrolArrival.Verdict.WAIT) {
			return;
		}
		if (verdict == PatrolArrival.Verdict.GRANT) {
			double witness = Math.max(0.0, PATROL_WITNESS_BLOCKS);
			for (ServerPlayer player : level.players()) {
				if (!player.isAlive() || player.isSpectator()) {
					continue;
				}
				if (PatrolArrival.playerCloseEnough(player.blockPosition().distSqr(destination), witness)) {
					ModAdvancements.onPatrolArrived(player);
				}
			}
		}
		for (Raider raider : squad) {
			((IllagerPatrolMember) raider).evolvingthreats$setPatrolDestination(null);
			raider.getNavigation().stop();
		}
	}

	private static boolean isMarching(Raider raider) {
		return raider.isAlive()
				&& raider instanceof IllagerPatrolMember member
				&& member.evolvingthreats$getPatrolDestination() != null;
	}

	private static boolean isMarchingTo(Raider raider, BlockPos destination) {
		return isMarching(raider)
				&& destination.equals(((IllagerPatrolMember) raider).evolvingthreats$getPatrolDestination());
	}

	/** Pillager, vindicator, ravager. A captain is still a pillager. Everyone else does not count. */
	private static PatrolArrival.Kind kindOf(EntityType<?> type) {
		if (type == EntityTypes.PILLAGER) {
			return PatrolArrival.Kind.PILLAGER;
		}
		if (type == EntityTypes.VINDICATOR) {
			return PatrolArrival.Kind.VINDICATOR;
		}
		if (type == EntityTypes.RAVAGER) {
			return PatrolArrival.Kind.RAVAGER;
		}
		return PatrolArrival.Kind.OTHER;
	}

	private static boolean canStand(ServerLevel level, BlockPos feet, EntityType<?> type) {
		if (!level.isLoaded(feet) || !level.getWorldBorder().isWithinBounds(feet)) {
			return false;
		}
		BlockPos ground = feet.below();
		BlockState groundState = level.getBlockState(ground);
		if (groundState.getCollisionShape(level, ground).isEmpty()) {
			return false;
		}
		FluidState fluid = level.getFluidState(feet);
		if (!fluid.isEmpty()) {
			return false;
		}
		AABB body = type.getDimensions().makeBoundingBox(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
		return level.noCollision(body);
	}

}
