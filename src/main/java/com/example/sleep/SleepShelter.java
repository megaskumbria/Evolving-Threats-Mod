package com.example.sleep;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sleeping does not skip the night for free.
 * When someone lies down, the easiest way in for a zombie or skeleton is priced
 * ({@link SleepShelterMath}). That price is the chance they wake you.
 * A failed night keeps going from the moment you closed your eyes, and that same night
 * cannot be rolled again.
 *
 * <p>Numbers for walls, doors, and light on the path live in {@link SleepShelterMath}.
 */
public final class SleepShelter {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * How long you stay in bed before a failed sleep wakes you, in ticks.
	 * 30 = 1.5 seconds. Must stay under 100: at 100 the game itself decides you are asleep
	 * and would skip the night.
	 * Higher = a longer moment with eyes closed. Lower = the blow comes almost at once.
	 */
	public static final int LIE_TICKS = 30;

	/**
	 * A monster this close, in the same room, wakes you and does not bring a new one.
	 * 16 = something already in the room with you. A monster outside the walls does not count.
	 * Higher = a fight farther from the bed, but still in the same room, stops the skip.
	 */
	public static final double HOSTILE_RANGE = 16.0;

	/**
	 * A monster that is already chasing you, in the same room, out to this many blocks, also stops the skip.
	 * 60 = the far end of a large room. 0 = only {@link #HOSTILE_RANGE} matters.
	 * A chase on the other side of a wall does not count.
	 */
	public static final double CHASE_RANGE = 60.0;

	/** Fewest mobs a failed sleep can call. 1 = sometimes only one. */
	public static final int MIN_MOBS = 1;

	/**
	 * Most mobs a failed sleep can call. 2 = one or two, zombies and skeletons in any mix.
	 * They appear beside the bed. Only one of them lands the wake-up blow.
	 */
	public static final int MAX_MOBS = 2;

	/**
	 * How far from the bed a mob may appear, in blocks.
	 * 4 = the open floor of the room. A spot on the far side of a wall is never used.
	 */
	public static final int SPAWN_ROOM = 4;

	/**
	 * How tall the space must be for a mob to appear there, in blocks.
	 * 1.9 = a zombie fits in an ordinary room that is 2 blocks high.
	 * 2 = only taller rooms count, and the mob gets pushed out through the wall.
	 */
	public static final double SPAWN_HEIGHT = 1.9;

	/**
	 * How many blocks of the way in break when they appear by the bed.
	 * 2 = a door loses both halves, or a wall loses the inner block and the one above it.
	 * 0 = they still appear, and nothing breaks. Obsidian and bedrock never break.
	 */
	public static final int BREACH_BLOCKS = 2;

	// ========================================================================

	private static final Stat<?> INSOMNIA = Stats.CUSTOM.get(Stats.TIME_SINCE_REST);

	private static final String[] LOCKED_RU = {"Эту ночь уже не проспать."};
	private static final String[] LOCKED_EN = {"This night will not pass."};
	private static final String[] HOSTILE_RU = {"Они уже рядом."};
	private static final String[] HOSTILE_EN = {"They're already close."};
	private static final String[] HIT_RU = {"Кто-то разбудил тебя ударом."};
	private static final String[] HIT_EN = {"Something woke you with a blow."};
	private static final String[] BROKE_RU = {"Они проломили вход."};
	private static final String[] BROKE_EN = {"They broke in."};

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private SleepShelter() {
	}

	static void clearRuntime() {
		PENDING.clear();
	}

	/** Same night as a failed attempt: refuse before they lie down again. */
	public static Player.BedSleepingProblem onAllowSleeping(Player player, BlockPos pos) {
		if (!SleepShelterMath.ENABLED || !(player instanceof ServerPlayer serverPlayer)) {
			return null;
		}
		if (!(serverPlayer.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			return null;
		}
		if (!level.isDarkOutside()) {
			return null;
		}
		if (SleepShelterData.get(level).locked(serverPlayer.getUUID(), dayIndex(level))) {
			tell(serverPlayer, LOCKED_RU, LOCKED_EN);
			return Player.BedSleepingProblem.OTHER_PROBLEM;
		}
		return null;
	}

	/** The night skip counts this player only when the roll said the bed was safe. */
	public static boolean allowResettingTime(Player player) {
		if (!SleepShelterMath.ENABLED || !(player instanceof ServerPlayer serverPlayer)) {
			return true;
		}
		if (!(serverPlayer.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			return true;
		}
		if (PENDING.containsKey(serverPlayer.getUUID())) {
			return false;
		}
		return !SleepShelterData.get(level).locked(serverPlayer.getUUID(), dayIndex(level));
	}

	public static void onStartSleeping(LivingEntity entity, BlockPos pos) {
		if (!SleepShelterMath.ENABLED || !(entity instanceof ServerPlayer player)) {
			return;
		}
		if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			return;
		}
		if (player.isSpectator() || !level.isDarkOutside()) {
			return;
		}
		int insomnia = player.getStats().getValue(INSOMNIA);
		long day = dayIndex(level);
		if (hostileNearby(player)) {
			SleepShelterData.get(level).lock(player.getUUID(), day);
			PENDING.put(player.getUUID(), new Pending(player.getUUID(), insomnia, pos, null, true, LIE_TICKS));
			return;
		}
		WorldView world = new WorldView(level);
		SleepShelterMath.Route route = SleepShelterMath.search(world, pos.getX(), pos.getY(), pos.getZ());
		double chance = SleepShelterMath.wakeChance(route.points());
		if (level.getRandom().nextDouble() >= chance) {
			return;
		}
		SleepShelterData.get(level).lock(player.getUUID(), day);
		PENDING.put(player.getUUID(), new Pending(player.getUUID(), insomnia, pos, route.breach(), false, LIE_TICKS));
	}

	/** Left the bed early (sneak). The roll already happened, so the night still comes. */
	public static void onStopSleeping(LivingEntity entity, BlockPos pos) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}
		Pending pending = PENDING.remove(player.getUUID());
		if (pending != null) {
			finish(player, pending, false);
		}
	}

	public static void tick(ServerLevel level) {
		if (!SleepShelterMath.ENABLED || level.dimension() != Level.OVERWORLD) {
			return;
		}
		if (!PENDING.isEmpty()) {
			Iterator<Pending> it = PENDING.values().iterator();
			while (it.hasNext()) {
				Pending pending = it.next();
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(pending.player);
				if (player == null || player.level() != level) {
					continue;
				}
				pending.ticksLeft--;
				if (pending.ticksLeft > 0) {
					continue;
				}
				it.remove();
				finish(player, pending, true);
			}
		}
		wakeLockedSleepers(level);
	}

	/**
	 * Text for {@code /evolvingthreats sleep}. Stands in for the block under your feet.
	 * One dry roll is included so the chat shows what came up. Nothing spawns, and the night is not locked.
	 */
	public static String describe(ServerPlayer player) {
		if (!SleepShelterMath.ENABLED) {
			return "Sleep shelter is off.";
		}
		if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			return "Sleep shelter only checks the Overworld.";
		}
		if (hostileNearby(player)) {
			return "A monster is already close. The night would not pass.";
		}
		BlockPos pos = player.blockPosition();
		SleepShelterMath.Route route = SleepShelterMath.search(new WorldView(level), pos.getX(), pos.getY(), pos.getZ());
		double chance = SleepShelterMath.wakeChance(route.points());
		int percent = (int) Math.round(chance * 100.0);
		// Same comparison as lying down: a roll under the chance wakes you.
		double roll = level.getRandom().nextDouble();
		int rolled = (int) Math.floor(roll * 100.0);
		boolean wake = roll < chance;
		String outcome = wake
				? "Rolled " + rolled + ". Mobs would come."
				: "Rolled " + rolled + ". The night would pass.";
		String where = "";
		if (wake) {
			where = " They would appear by the bed, and one would hit."
					+ (route.breach() == null ? "" : " The way in would break.");
		}
		return "Wake chance " + percent + "% (" + String.format(Locale.ROOT, "%.1f", route.points()) + " points). "
				+ outcome + where;
	}

	private static void finish(ServerPlayer player, Pending pending, boolean wake) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		restoreInsomnia(player, pending.insomnia);
		List<BlockPos> spots = roomSpots(level, pending.bed);
		boolean broke = false;
		if (!pending.alreadyHostile) {
			if (pending.breach != null) {
				broke = breakIn(level, pending.bed, pending.breach);
			}
			spawnArrivals(level, player, pending, wake, spots);
		}
		if (wake && player.isSleeping()) {
			player.stopSleepInBed(true, true);
		}
		if (pending.alreadyHostile) {
			tell(player, HOSTILE_RU, HOSTILE_EN);
		} else if (broke) {
			tell(player, BROKE_RU, BROKE_EN);
		} else {
			tell(player, HIT_RU, HIT_EN);
		}
	}

	/** @return true when at least one block actually came down */
	private static boolean breakIn(ServerLevel level, BlockPos bed, SleepShelterMath.Breach breach) {
		int limit = BREACH_BLOCKS;
		if (limit <= 0) {
			return false;
		}
		boolean broke = breakOne(level, bed, new BlockPos(breach.x(), breach.y(), breach.z()));
		if (limit >= 2 && breach.second()) {
			broke |= breakOne(level, bed, new BlockPos(breach.x2(), breach.y2(), breach.z2()));
		}
		return broke;
	}

	/** Drops the block. Skips the bed, and anything as hard as obsidian. A door loses both halves. */
	private static boolean breakOne(ServerLevel level, BlockPos bed, BlockPos pos) {
		if (pos.equals(bed) || pos.equals(bed.above())) {
			return false;
		}
		if (level.getChunk(
				SectionPos.blockToSectionCoord(pos.getX()),
				SectionPos.blockToSectionCoord(pos.getZ()),
				ChunkStatus.FULL,
				false
		) == null) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.is(BlockTags.BEDS) || unbreakable(level, state, pos)) {
			return false;
		}
		BlockPos other = doorOtherHalf(state, pos);
		boolean broke = level.destroyBlock(pos, true, null);
		if (other == null || other.equals(bed) || other.equals(bed.above())) {
			return broke;
		}
		BlockState otherState = level.getBlockState(other);
		if (otherState.getBlock() instanceof DoorBlock && !unbreakable(level, otherState, other)) {
			broke |= level.destroyBlock(other, true, null);
		}
		return broke;
	}

	private static BlockPos doorOtherHalf(BlockState state, BlockPos pos) {
		if (!(state.getBlock() instanceof DoorBlock)) {
			return null;
		}
		return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
	}

	private static boolean unbreakable(ServerLevel level, BlockState state, BlockPos pos) {
		float speed = state.getDestroySpeed(level, pos);
		return speed < 0.0f || speed >= 50.0f;
	}

	private static void spawnArrivals(ServerLevel level, ServerPlayer player, Pending pending, boolean wake,
			List<BlockPos> spots) {
		RandomSource random = level.getRandom();
		int count = MIN_MOBS;
		if (MAX_MOBS > MIN_MOBS) {
			count = MIN_MOBS + random.nextInt(MAX_MOBS - MIN_MOBS + 1);
		}
		if (spots.isEmpty()) {
			spots = List.of(BlockPos.containing(player.getX(), player.getY(), player.getZ()));
		}
		Mob hitter = null;
		for (int i = 0; i < count; i++) {
			BlockPos spot = spots.get(Math.min(i, spots.size() - 1));
			boolean skeleton = random.nextBoolean();
			Mob mob = skeleton
					? EntityTypes.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED)
					: EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (mob == null) {
				continue;
			}
			double x = spot.getX() + 0.5;
			double z = spot.getZ() + 0.5;
			if (i > 0 && spot.equals(spots.get(0))) {
				x = player.getX();
				z = player.getZ();
			}
			mob.snapTo(x, spot.getY(), z, level.getRandom().nextFloat() * 360.0f, 0.0f);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.MOB_SUMMONED, null);
			mob.setPersistenceRequired();
			mob.setTarget(player);
			// Stay put for the wake-up blow. Pathing starts after, so they are not already outside.
			mob.setNoAi(true);
			level.addFreshEntity(mob);
			if (hitter == null) {
				hitter = mob;
			} else {
				mob.setNoAi(false);
			}
		}
		// Sneaking out already stands them up. The blow is only for the timed wake, while they are still down.
		if (wake && hitter != null && player.distanceToSqr(Vec3.atCenterOf(pending.bed)) <= 36.0) {
			hitter.doHurtTarget(level, player);
		}
		if (hitter != null) {
			hitter.setNoAi(false);
		}
	}

	/**
	 * Floor space the mob's body actually fits in, connected to the bed by empty blocks.
	 * A normal room is only 2 blocks high, so a full extra block of air is not required.
	 * Measured before the hole is broken, so the opening cannot be used as a spawn spot.
	 */
	private static List<BlockPos> roomSpots(ServerLevel level, BlockPos bed) {
		int reach = Math.max(1, SPAWN_ROOM);
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		List<BlockPos> spots = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				offerStep(level, queue, seen, bed.getX() + dx, bed.getY(), bed.getZ() + dz, bed, reach);
				offerStep(level, queue, seen, bed.getX() + dx, bed.getY() + 1, bed.getZ() + dz, bed, reach);
			}
		}
		while (!queue.isEmpty()) {
			BlockPos cell = queue.removeFirst();
			if (fits(level, cell) && underRoof(level, bed, cell)) {
				spots.add(cell);
			}
			offerStep(level, queue, seen, cell.getX() + 1, cell.getY(), cell.getZ(), bed, reach);
			offerStep(level, queue, seen, cell.getX() - 1, cell.getY(), cell.getZ(), bed, reach);
			offerStep(level, queue, seen, cell.getX(), cell.getY(), cell.getZ() + 1, bed, reach);
			offerStep(level, queue, seen, cell.getX(), cell.getY(), cell.getZ() - 1, bed, reach);
			offerStep(level, queue, seen, cell.getX(), cell.getY() + 1, cell.getZ(), bed, reach);
			offerStep(level, queue, seen, cell.getX(), cell.getY() - 1, cell.getZ(), bed, reach);
		}
		spots.sort(Comparator.comparingInt(spot -> spot.distManhattan(bed)));
		return spots;
	}

	private static void offerStep(ServerLevel level, ArrayDeque<BlockPos> queue, Set<Long> seen,
			int x, int y, int z, BlockPos bed, int reach) {
		if (Math.abs(x - bed.getX()) > reach || Math.abs(z - bed.getZ()) > reach) {
			return;
		}
		if (y < bed.getY() || y > bed.getY() + 1) {
			return;
		}
		if (!passable(level, new BlockPos(x, y, z))) {
			return;
		}
		if (!seen.add(BlockPos.asLong(x, y, z))) {
			return;
		}
		queue.add(new BlockPos(x, y, z));
	}

	/** Carpets and the bed's thin top do not block. A wall does. */
	private static boolean passable(ServerLevel level, BlockPos pos) {
		VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) < 0.5;
	}

	private static boolean fits(ServerLevel level, BlockPos cell) {
		VoxelShape floor = level.getBlockState(cell.below()).getCollisionShape(level, cell.below());
		if (floor.isEmpty()) {
			return false;
		}
		double x = cell.getX() + 0.5;
		double z = cell.getZ() + 0.5;
		AABB body = new AABB(
				x - 0.3, cell.getY() + 0.08, z - 0.3,
				x + 0.3, cell.getY() + SPAWN_HEIGHT, z + 0.3
		);
		return level.noCollision(body);
	}

	/** A roofed bed only accepts spots that are also under a roof. The yard under the open sky is left out. */
	private static boolean underRoof(ServerLevel level, BlockPos bed, BlockPos cell) {
		if (level.canSeeSky(bed) || level.canSeeSky(bed.above())) {
			return true;
		}
		for (int dy = 1; dy <= 3; dy++) {
			BlockPos above = cell.above(dy);
			VoxelShape shape = level.getBlockState(above).getCollisionShape(level, above);
			if (!shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.5) {
				return true;
			}
			if (level.canSeeSky(above)) {
				return false;
			}
		}
		return !level.canSeeSky(cell);
	}

	private static void restoreInsomnia(ServerPlayer player, int ticks) {
		ServerStatsCounter stats = player.getStats();
		stats.setValue(player, INSOMNIA, ticks);
	}

	private static void wakeLockedSleepers(ServerLevel level) {
		long day = dayIndex(level);
		SleepShelterData data = SleepShelterData.get(level);
		for (ServerPlayer player : level.players()) {
			if (!player.isSleeping() || PENDING.containsKey(player.getUUID())) {
				continue;
			}
			if (data.locked(player.getUUID(), day)) {
				player.stopSleepInBed(true, true);
				tell(player, LOCKED_RU, LOCKED_EN);
			}
		}
	}

	private static boolean hostileNearby(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockPos origin = player.blockPosition();
		int reach = (int) Math.max(HOSTILE_RANGE, CHASE_RANGE);
		Set<Long> room = sameFloor(level, origin, reach);
		AABB box = player.getBoundingBox().inflate(reach);
		for (Monster monster : level.getEntitiesOfClass(Monster.class, box, LivingEntity::isAlive)) {
			if (!standsIn(room, monster.blockPosition())) {
				continue;
			}
			double distance = monster.distanceTo(player);
			if (distance <= HOSTILE_RANGE) {
				return true;
			}
			if (CHASE_RANGE > 0.0 && distance <= CHASE_RANGE && monster.getTarget() == player) {
				return true;
			}
		}
		return false;
	}

	/** Empty blocks on this floor. Walls stop the walk. A ladder down to the next floor does not. */
	private static Set<Long> sameFloor(ServerLevel level, BlockPos origin, int reach) {
		Set<Long> room = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		if (!markFloor(level, queue, room, origin, origin, reach)) {
			markFloor(level, queue, room, origin.above(), origin, reach);
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				markFloor(level, queue, room, origin.offset(dx, 0, dz), origin, reach);
				markFloor(level, queue, room, origin.offset(dx, 1, dz), origin, reach);
			}
		}
		while (!queue.isEmpty()) {
			BlockPos cell = queue.removeFirst();
			markFloor(level, queue, room, cell.offset(1, 0, 0), origin, reach);
			markFloor(level, queue, room, cell.offset(-1, 0, 0), origin, reach);
			markFloor(level, queue, room, cell.offset(0, 0, 1), origin, reach);
			markFloor(level, queue, room, cell.offset(0, 0, -1), origin, reach);
			markFloor(level, queue, room, cell.above(), origin, reach);
		}
		return room;
	}

	private static boolean markFloor(ServerLevel level, ArrayDeque<BlockPos> queue, Set<Long> room,
			BlockPos pos, BlockPos origin, int reach) {
		if (pos.getY() < origin.getY() || pos.getY() > origin.getY() + 1) {
			return false;
		}
		if (Math.abs(pos.getX() - origin.getX()) > reach || Math.abs(pos.getZ() - origin.getZ()) > reach) {
			return false;
		}
		if (!passable(level, pos)) {
			return false;
		}
		if (!room.add(pos.asLong())) {
			return false;
		}
		queue.add(pos);
		return true;
	}

	private static boolean standsIn(Set<Long> room, BlockPos pos) {
		return room.contains(pos.asLong()) || room.contains(pos.above().asLong());
	}

	private static long dayIndex(ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime(), 24000L);
	}

	private static void tell(ServerPlayer player, String[] russian, String[] english) {
		String line = prefersRussian(player) ? russian[0] : english[0];
		player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}

	private static boolean prefersRussian(ServerPlayer player) {
		String language = player.clientInformation().language();
		return language != null && language.toLowerCase(Locale.ROOT).startsWith("ru");
	}

	private static final class Pending {
		final UUID player;
		final int insomnia;
		final BlockPos bed;
		final SleepShelterMath.Breach breach;
		final boolean alreadyHostile;
		int ticksLeft;

		Pending(UUID player, int insomnia, BlockPos bed, SleepShelterMath.Breach breach,
				boolean alreadyHostile, int ticksLeft) {
			this.player = player;
			this.insomnia = insomnia;
			this.bed = bed;
			this.breach = breach;
			this.alreadyHostile = alreadyHostile;
			this.ticksLeft = ticksLeft;
		}
	}

	/** One scan. Block kinds are remembered so each block is classified once. */
	private static final class WorldView implements SleepShelterMath.View {
		private final ServerLevel level;
		private final Map<Long, SleepShelterMath.Kind> kinds = new HashMap<>();
		private final Map<Long, Integer> lights = new HashMap<>();

		WorldView(ServerLevel level) {
			this.level = level;
		}

		@Override
		public SleepShelterMath.Kind kind(int x, int y, int z) {
			return kinds.computeIfAbsent(BlockPos.asLong(x, y, z), key -> classify(x, y, z));
		}

		@Override
		public int light(int x, int y, int z) {
			return lights.computeIfAbsent(BlockPos.asLong(x, y, z), key -> sampleLight(x, y, z));
		}

		@Override
		public boolean seesSky(int x, int y, int z) {
			BlockPos pos = new BlockPos(x, y, z);
			return loaded(pos) && level.canSeeSky(pos);
		}

		private boolean loaded(BlockPos pos) {
			return level.getChunk(
					SectionPos.blockToSectionCoord(pos.getX()),
					SectionPos.blockToSectionCoord(pos.getZ()),
					ChunkStatus.FULL,
					false
			) != null;
		}

		private int sampleLight(int x, int y, int z) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!loaded(pos)) {
				return 0;
			}
			// The raw sky value stays 15 at night, so open air looked like noon.
			// A high window then had no "dark" block, and a room with no ground outside
			// was treated as sealed. While you can sleep, open air is dark.
			// A torch is block light, and that still counts.
			if (level.isDarkOutside() && level.canSeeSky(pos)) {
				return level.getBrightness(LightLayer.BLOCK, pos);
			}
			return level.getMaxLocalRawBrightness(pos, level.getSkyDarken());
		}

		private SleepShelterMath.Kind classify(int x, int y, int z) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!loaded(pos)) {
				return SleepShelterMath.Kind.UNLOADED;
			}
			if (level.getFluidState(pos).is(FluidTags.LAVA)) {
				return SleepShelterMath.Kind.TOUGH;
			}
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof DoorBlock door) {
				if (door.isOpen(state)) {
					return SleepShelterMath.Kind.AIR;
				}
				return door.type().canOpenByHand()
						? SleepShelterMath.Kind.WOOD_DOOR
						: SleepShelterMath.Kind.IRON_DOOR;
			}
			if (state.getBlock() instanceof FenceGateBlock) {
				return state.getValue(FenceGateBlock.OPEN)
						? SleepShelterMath.Kind.AIR
						: SleepShelterMath.Kind.FENCE;
			}
			if (state.getBlock() instanceof FenceBlock
					|| state.getBlock() instanceof WallBlock
					|| state.getBlock() instanceof IronBarsBlock) {
				return SleepShelterMath.Kind.FENCE;
			}
			if (state.getBlock() instanceof LadderBlock || state.getBlock() instanceof StairBlock) {
				return SleepShelterMath.Kind.CLIMB;
			}
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (shape.isEmpty() || shape.max(Direction.Axis.Y) < 0.4) {
				return SleepShelterMath.Kind.AIR;
			}
			float speed = state.getDestroySpeed(level, pos);
			if (speed < 0.0f || speed >= 50.0f) {
				return SleepShelterMath.Kind.TOUGH;
			}
			if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
				return SleepShelterMath.Kind.STONE;
			}
			return SleepShelterMath.Kind.SOFT;
		}
	}
}
