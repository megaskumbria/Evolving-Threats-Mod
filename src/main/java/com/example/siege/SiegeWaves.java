package com.example.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 Waves of siege. A group of mobs appears on the surface far from the player.
 * (like raiders before raiding the village), and goes to him. No sounds when it appears.
 * Who exactly is in the group decides the composition of the siege {@link SiegeComposition} (undead and creatures or llagers, in random fractions).
 *
 How Mobs Find Players – Individual Goals, Not This Class
 * {@link com.example.entity.ai.SiegeTargetGoal} holds the player a target,
 * {@link com.example.entity.ai.SiegeMarchGoal} leads to him while he's away,
 * and on the way holds a battle stand (looks at the player, zombies raised hands).
 * In the vicinity includes a regular fight or shooting.
 *
 * Siege mobs tag is a common entity tag {@link #TAG}. It stays with the mob itself.
 Mobs with a tag obey the siege only while it goes.
 *
 Does not put soaring tools, golems, mini-bosses and fashion events - only mobs from the siege.
 */
public final class SiegeWaves {

	// ===================== SETTINGS (change these numbers) =====================

	/*How many tics after the beginning of the night comes the first wave. 600 = 30 seconds. */
	public static final int FIRST_WAVE_DELAY_TICKS = 600;

	/**
	 Pause between waves: random number of ticks from minimum to maximum.
	 1200 and 1800 = 60 to 90 seconds. The night lasts about 8 minutes, so there are 6-8 waves.
	 */
	public static final int WAVE_PAUSE_MIN_TICKS = 1200;
	public static final int WAVE_PAUSE_MAX_TICKS = 1800;

	/*If the place under the wave is not found or there are too many mobs nearby, try again through so many ticks. */
	public static final int RETRY_TICKS = 100;

	/**
	 The size of the normal wave, from and to. Considered in the "price" of mobs (see SiegeComposition.Kind):
	 A regular mob costs 1, caller 2, ruiner 3. So in the wave of 12 fit 12 zombies or 4 ruiners.
	 */
	public static final int WAVE_SIZE_MIN = 8;
	public static final int WAVE_SIZE_MAX = 15;

	/**
	 Night is divided into fractions from 0.0 (early) to 1.0 (dawn).
	 Up to this fraction, the waves are weaker: the size is multiplied by EARLY SIZE FACTOR. 0.30 = first third of the night.
	 */
	public static final float EARLY_UNTIL = 0.30f;

	/*How many times less wave at the beginning of the night. 0.6 = 60% of normal size. 1.0 = whatever. */
	public static final float EARLY_SIZE_FACTOR = 0.6f;

	/*From what fraction of the night the waves come from both sides at once (ticks). 0.50 = from midnight. 1.1 = never. */
	public static final float TWO_SIDES_FROM = 0.50f;

	/*From what fraction of the night comes the last great wave (on both sides). 0.85 = before dawn. */
	public static final float FINAL_WAVE_FROM = 0.85f;

	/*How many times is the last wave larger than the usual one? 1.5 = half more. */
	public static final float FINAL_WAVE_SIZE_FACTOR = 1.5f;

	/**
	 How many mobs of siege can be alive around one player (within CONTROL RADIUS radius).
	 More is scarier, but more load on the server: each mob has a search for a path.
	 If there are so many mobs, a new wave is waiting.
	 */
	public static final int MAX_ALIVE_PER_PLAYER = 80;

	/*If there are fewer free seats, we do not call the wave, wait (so that one or two do not come). */
	public static final int MIN_FREE_SLOTS = 4;

	/**
	 Players in creative mode are also considered the target of the siege: waves come to them, mobs go to them.
	 * Convenient to check in creative mode. The creative player’s mobs still don’t beat (that’s how the game works).
	 False - as in a normal game: the creative mode for the siege "does not exist", there are no waves next to it
	 * and the mobs of siege stand still. The spectator is never considered.
	 */
	public static final boolean INCLUDE_CREATIVE_PLAYERS = true;

	/*At what distance from the player the wave appears in blocks. No further loading distance is allowed. */
	public static final int SPAWN_DISTANCE_MIN = 50;
	public static final int SPAWN_DISTANCE_MAX = 70;

	/** Dispersion of direction of one group, in radians. 0.35 = approximately ± 20 degrees. */
	public static final double SIDE_SPREAD_RADIANS = 0.35;

	/** There is no wave closer to any player, in blocks. 24 = like a regular spawn game. */
	public static final int MIN_PLAYER_DISTANCE = 24;

	/*In what radius from the center of the wave stand mobs in blocks. */
	public static final int CLUSTER_RADIUS = 5;

	/*How many points to pick for the center of the wave and for each mob? */
	public static final int SPOT_TRIES = 24;
	public static final int MEMBER_TRIES = 12;

	/*How often can you build a new path to the player, in ticks? 10 = no more than twice per second.
	 Each count stops the mob for a moment. 1 = jerks and lag, arrows also fly in leaps. */
	public static final int REPATH_COOLDOWN_TICKS = 10;

	/*In what radius from the player to look for and lead mobs of siege, in blocks. */
	public static final double CONTROL_RADIUS = 110.0;

	/** The radius of view of the siege mobs (attribute of the game). A typical zombie is 35. More is the way, but harder. */
	public static final double FOLLOW_RANGE = 96.0;

	/*Closer to this mob stops going in segments and begins to fight or shoot, in blocks. */
	public static final double MARCH_STOP_DISTANCE = 16.0;

	/**
	 Then he goes back to the player if he ran away.
	 There should be more MARCH STOP DISTANCE. If the numbers are equal, the mob twitches at the boundary:
	 It goes, it shoots, and the arrow falls off.
	 */
	public static final double MARCH_RESUME_DISTANCE = 28.0;

	/*The length of one leg of the path, when the target is far away, in blocks. */
	public static final double MARCH_STEP_BLOCKS = 24.0;

	/*Speed of the segment. 1.0 = normal mob pitch. More is faster. */
	public static final double MARCH_SPEED = 1.0;

	// ========================================================================

	/** Siege mob tag. Save with the mob. */
	public static final String TAG = "evolvingthreats_siege";

	private static int waveCountdown = -1;
	private static boolean finalWaveDone;

	private SiegeWaves() {
	}

	/*New Siege Night: The first wave through FIRST WAVE DELAY TICKS, the last one not yet. */
	public static void reset() {
		waveCountdown = Math.max(1, FIRST_WAVE_DELAY_TICKS);
		finalWaveDone = false;
	}

	/*Shutting down the server. */
	public static void clearRuntime() {
		waveCountdown = -1;
		finalWaveDone = false;
	}

	/*True - mob from the siege (wave or usual night spawn during the siege). */
	public static boolean isSiegeMob(Entity entity) {
		return entity.isAlive() && entity.entityTags().contains(TAG);
	}

	/**
	 Marks the mob as a siege mob and gives it a large radius of view.
	 Called for the wave, and for zombies and skeletons that appeared during the siege.
	 */
	public static void enlist(Mob mob) {
		mob.addTag(TAG);
		AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (range != null) {
			range.setBaseValue(FOLLOW_RANGE);
		}
	}

	/**
	 Every tick night of siege. Progress is the proportion of night from 0.0 to 1.0.
	 */
	public static void tick(ServerLevel level, double progress) {
		RandomSource random = level.getRandom();
		if (waveCountdown < 0) {
			waveCountdown = rollPause(random);
		}
		if (--waveCountdown <= 0) {
			boolean spawned = canSpawnWaves(level) && spawnWave(level, progress);
			waveCountdown = spawned ? rollPause(random) : Math.max(1, RETRY_TICKS);
		}
	}

	private static int rollPause(RandomSource random) {
		int min = Math.max(20, WAVE_PAUSE_MIN_TICKS);
		int max = Math.max(min, WAVE_PAUSE_MAX_TICKS);
		return random.nextIntBetweenInclusive(min, max);
	}

	/*The rules of the game "spawn mobs" and "spawn monsters" are turned off - there are no waves (as well as the usual night spawn). */
	private static boolean canSpawnWaves(ServerLevel level) {
		return Boolean.TRUE.equals(level.getGameRules().get(GameRules.SPAWN_MOBS))
				&& Boolean.TRUE.equals(level.getGameRules().get(GameRules.SPAWN_MONSTERS));
	}

	// ------------------------------------------------------------------
	// The appearance of a wave
	// ------------------------------------------------------------------

	/** True - at least one group appeared. */
	private static boolean spawnWave(ServerLevel level, double progress) {
		RandomSource random = level.getRandom();
		boolean finalWave = !finalWaveDone && progress >= FINAL_WAVE_FROM;
		boolean any = false;
		float[] shares = SiegeComposition.current(level);

		for (ServerPlayer player : eligiblePlayers(level)) {
			int room = MAX_ALIVE_PER_PLAYER - countAlive(level, player);
			if (room < MIN_FREE_SLOTS) {
				continue;
			}

			float factor = finalWave ? FINAL_WAVE_SIZE_FACTOR : (progress < EARLY_UNTIL ? EARLY_SIZE_FACTOR : 1.0f);
			int base = random.nextIntBetweenInclusive(Math.max(1, WAVE_SIZE_MIN), Math.max(WAVE_SIZE_MIN, WAVE_SIZE_MAX));
			int size = Math.min(room, Math.max(1, Math.round(base * factor)));

			boolean twoSides = finalWave || progress >= TWO_SIDES_FROM;
			double angle = random.nextDouble() * Math.PI * 2.0;
			int spawned;
			if (twoSides && size >= 2) {
				int first = size / 2;
				spawned = spawnGroup(level, player, angle, first, shares)
						+ spawnGroup(level, player, angle + Math.PI, size - first, shares);
			} else {
				spawned = spawnGroup(level, player, angle, size, shares);
			}
			any |= spawned > 0;
		}

		if (finalWave && any) {
			finalWaveDone = true;
		}
		return any;
	}

	/**
	 * Puts one group in the direction of the angle from the player. budget - group size in the "price" of mobs
	 * (a regular mob costs 1, ruiner 3). Returns how many mobs have actually appeared.
	 */
	private static int spawnGroup(ServerLevel level, ServerPlayer player, double angle, int budget, float[] shares) {
		BlockPos center = findCenter(level, player, angle);
		if (center == null) {
			return 0;
		}
		RandomSource random = level.getRandom();
		int spawned = 0;
		int remaining = budget;
		// Not all dots fit (especially large mobs), so attempts are twice the size.
		for (int attempt = 0; attempt < budget * 2 && remaining > 0; attempt++) {
			BlockPos pos = findMemberSpot(level, center, player);
			if (pos == null) {
				continue;
			}
			SiegeComposition.Kind kind = SiegeComposition.pickKindLimited(level, shares, random, player.blockPosition());
			if (spawnOne(level, player, pos, kind)) {
				spawned++;
				remaining -= Math.max(1, kind.cost);
			}
		}
		return spawned;
	}

	private static BlockPos findCenter(ServerLevel level, ServerPlayer player, double angle) {
		RandomSource random = level.getRandom();
		int min = Math.max(1, SPAWN_DISTANCE_MIN);
		int max = Math.max(min, SPAWN_DISTANCE_MAX);
		for (int attempt = 0; attempt < SPOT_TRIES; attempt++) {
			double a = angle + (random.nextDouble() * 2.0 - 1.0) * SIDE_SPREAD_RADIANS;
			double distance = min + random.nextDouble() * (max - min);
			int x = Mth.floor(player.getX() + Math.cos(a) * distance);
			int z = Mth.floor(player.getZ() + Math.sin(a) * distance);
			BlockPos column = new BlockPos(x, 0, z);
			// Do not touch alien and unloaded pieces of the world: otherwise the game will begin to generate them.
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (canStand(level, surface, EntityTypes.ZOMBIE) && farFromPlayers(level, surface)) {
				return surface;
			}
		}
		return null;
	}

	private static BlockPos findMemberSpot(ServerLevel level, BlockPos center, ServerPlayer player) {
		RandomSource random = level.getRandom();
		int radius = Math.max(1, CLUSTER_RADIUS);
		for (int attempt = 0; attempt < MEMBER_TRIES; attempt++) {
			int dx = random.nextInt(radius * 2 + 1) - radius;
			int dz = random.nextInt(radius * 2 + 1) - radius;
			BlockPos column = center.offset(dx, 0, dz);
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (canStand(level, surface, EntityTypes.ZOMBIE) && farFromPlayers(level, surface)) {
				return surface;
			}
		}
		return null;
	}

	private static boolean spawnOne(ServerLevel level, ServerPlayer player, BlockPos pos, SiegeComposition.Kind kind) {
		RandomSource random = level.getRandom();
		EntityType<?> type = SiegeComposition.typeOf(kind, level, pos);

		Entity created = type.create(level, EntitySpawnReason.NATURAL);
		if (!(created instanceof Mob mob)) {
			return false;
		}
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		double y = pos.getY();
		if (kind == SiegeComposition.Kind.PHANTOM) {
			int min = Math.max(1, SiegeComposition.PHANTOM_HEIGHT_MIN);
			int max = Math.max(min, SiegeComposition.PHANTOM_HEIGHT_MAX);
			y += random.nextIntBetweenInclusive(min, max);
		}
		float yaw = (float) (Mth.atan2(player.getZ() - z, player.getX() - x) * (180.0 / Math.PI)) - 90.0f;
		mob.snapTo(x, y, z, yaw, 0.0f);
		// Large mobs (ravageer, spider) do not get into cramped places: we try another mob at this point.
		if (!level.noCollision(mob)) {
			return false;
		}

		// Natural = like night spawn games: armor, weapons, charms and special mod roles are issued as usual.
		// Mini-bosses do not fall out at this time, they are turned off by SiegeNight.isWindowOpen.
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
		enlist(mob);
		mob.setTarget(player);
		level.addFreshEntity(mob);
		return true;
	}

	// ------------------------------------------------------------------
	// Who to lead to the player (using SiegeTargetGoal and SiegeMarchGoal)
	// ------------------------------------------------------------------

	/**
	 * The nearest live player in survival or adventure, no further {@link #CONTROL RADIUS}
	 Observers do not count, creative mode - according to INCLUDE CREATIVE PLAYERS. null - there's no one around.
	 */
	public static ServerPlayer nearestHuntTarget(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return null;
		}
		double max = CONTROL_RADIUS * CONTROL_RADIUS;
		ServerPlayer best = null;
		double bestDistance = max;
		for (ServerPlayer player : level.players()) {
			if (!isHuntable(player)) {
				continue;
			}
			double distance = mob.distanceToSqr(player);
			if (distance <= bestDistance) {
				bestDistance = distance;
				best = player;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------
	// Assistants
	// ------------------------------------------------------------------

	/*Does the siege of this player count as a goal: alive, not an observer, and (if INCLUDE CREATIVE PLAYERS = false) not creative? */
	private static boolean isHuntable(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && (INCLUDE_CREATIVE_PLAYERS || !player.isCreative());
	}

	/** See @link #isHuntable. */
	private static List<ServerPlayer> eligiblePlayers(ServerLevel level) {
		List<ServerPlayer> result = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (isHuntable(player)) {
				result.add(player);
			}
		}
		return result;
	}

	private static int countAlive(ServerLevel level, ServerPlayer player) {
		AABB area = player.getBoundingBox().inflate(CONTROL_RADIUS);
		return level.getEntitiesOfClass(Mob.class, area, SiegeWaves::isSiegeMob).size();
	}

	private static boolean farFromPlayers(ServerLevel level, BlockPos pos) {
		double limit = (double) MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < limit) {
				return false;
			}
		}
		return true;
	}

	/*Is it possible to put a mob on this cage: hard floor, no water, the body does not rest on anything. */
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
