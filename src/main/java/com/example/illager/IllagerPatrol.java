package com.example.illager;

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
 * Отряд у игрока: разбойники, поборники и один разоритель.
 * Появляется днём в стороне от игрока и идёт в точку, где он стоял, в том числе если дом в деревне.
 * Обычный патруль игры (только разбойники и только вдали от деревни) при этом не отключается.
 */
public final class IllagerPatrol {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — такие отряды не появляются. Уже вышедшие доходят до игрока как раньше. */
	public static final boolean ENABLED = true;

	/**
	 * Самая короткая пауза между отрядами, в игровых днях.
	 * 8 = восемь дней. Один день длится 20 минут. Меньше — отряды чаще.
	 */
	public static final int DAYS_MIN = 8;

	/**
	 * Самая длинная пауза между отрядами, в игровых днях.
	 * 14 = две недели. Каждый раз берётся случайное число от {@link #DAYS_MIN} до этого.
	 * Если поставить меньше минимума, игра возьмёт минимум.
	 */
	public static final int DAYS_MAX = 14;

	/**
	 * Проверка без ожидания дней. 0 = обычная случайная пауза {@link #DAYS_MIN}–{@link #DAYS_MAX}.
	 * 200 = следующий отряд через 10 секунд. После проверки верни 0.
	 */
	public static final int TEST_ATTEMPT_TICKS = 0;

	/**
	 * Как часто снова пробовать, если время вышло, но сейчас ночь, нет места или игрок в креативе.
	 * В тиках. 1200 = раз в минуту, пока не получится. Это не новая пауза на дни.
	 */
	public static final int RETRY_TICKS = 1200;

	/**
	 * true — только днём, как старые патрули. false — и ночью тоже.
	 */
	public static final boolean DAYTIME_ONLY = true;

	/**
	 * Как далеко от игрока искать место, в блоках.
	 * 64 и 96 = четыре–шесть чанков: отряд видно издалека, он не появляется во дворе.
	 * Если поставить дальше дистанции прогрузки, они появятся и будут стоять, пока чанк не оживёт.
	 */
	public static final int SPAWN_DISTANCE_MIN = 64;
	public static final int SPAWN_DISTANCE_MAX = 96;

	/**
	 * Сколько поборников в отряде. 2 и 3 = два или три.
	 */
	public static final int VINDICATOR_MIN = 2;
	public static final int VINDICATOR_MAX = 3;

	/**
	 * Сколько разбойников в отряде. 2 и 3 = два или три.
	 * Один из них — капитан со знаменем: с него по-прежнему падает зловещая бутылочка.
	 */
	public static final int PILLAGER_MIN = 2;
	public static final int PILLAGER_MAX = 3;

	/**
	 * Сколько разорителей. 1 = в каждом отряде один. 0 = без разорителя.
	 */
	public static final int RAVAGER_COUNT = 1;

	/**
	 * На сколько блоков нужно подойти к запомненной точке, чтобы остановиться.
	 * 12 = двор у дома, а не один блок под ногами. Меньше — толпятся в той самой клетке.
	 */
	public static final int ARRIVE_BLOCKS = 12;

	/**
	 * Скорость шага к точке. 0.6 = обычный шаг. Больше — почти бегут.
	 */
	public static final double MARCH_SPEED = 0.6;

	/**
	 * Если в этом радиусе вокруг игрока уже есть свой отряд, новый не выходит.
	 * 140 = чуть дальше, чем {@link #SPAWN_DISTANCE_MAX}, чтобы идущая группа ещё считалась «рядом».
	 */
	public static final double NEARBY_BLOCKS = 140.0;

	/** Сколько точек перебрать, ища поляну под отряд. */
	public static final int SPOT_TRIES = 24;

	/** Сколько точек перебрать для одного моба рядом с центром отряда. */
	public static final int MEMBER_TRIES = 12;

	/** На сколько блоков члены отряда могут встать в сторону от разорителя. */
	public static final int CLUSTER_RADIUS = 4;

	// ========================================================================

	/** Один игровой день. 24000 тиков = 20 минут. */
	private static final int TICKS_PER_DAY = 24000;

	private static final WeakHashMap<ServerLevel, Integer> TICKS_LEFT = new WeakHashMap<>();

	private IllagerPatrol() {
	}

	/** Конец тика мира. В аду и в крае отряд не выходит. */
	public static void tick(ServerLevel level) {
		if (!ENABLED || level.dimension() != Level.OVERWORLD) {
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

	/** Случайная пауза: от DAYS_MIN до DAYS_MAX игровых дней. В тесте — фиксированные тики. */
	private static int rollDelay(RandomSource random) {
		if (TEST_ATTEMPT_TICKS > 0) {
			return TEST_ATTEMPT_TICKS;
		}
		int min = Math.max(1, DAYS_MIN);
		int max = Math.max(min, DAYS_MAX);
		return random.nextIntBetweenInclusive(min, max) * TICKS_PER_DAY;
	}

	/**
	 * Вызвать отряд прямо сейчас, для команды теста.
	 * День, креатив и пауза в несколько дней здесь не мешают.
	 * Отряд всё равно появляется в {@link #SPAWN_DISTANCE_MIN}–{@link #SPAWN_DISTANCE_MAX}
	 * блоках и идёт в точку, где игрок стоит в этот момент.
	 *
	 * @return false, если рядом не нашлось свободной земли
	 */
	public static boolean summonFor(ServerLevel level, ServerPlayer player) {
		BlockPos center = findClusterCenter(level, player);
		if (center == null) {
			return false;
		}
		return spawnSquad(level, player, center);
	}

	/** true — отряд вышел, и следующая пауза снова случайные дни. */
	private static boolean trySpawn(ServerLevel level) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		// В осаду отряды не выходят. Попробуем снова через RETRY_TICKS, после рассвета получится.
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
			// До finalizeSpawn: игра вешает знамя капитана только если флаг уже стоит.
			raider.setPatrolLeader(true);
		}
		if (raider instanceof IllagerPatrolMember patrol) {
			// Точка, где игрок стоит прямо сейчас. Дальше она не следует за ним.
			patrol.evolvingthreats$setPatrolDestination(player.blockPosition());
		}
		raider.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.PATROL, null);
		raider.setCanJoinRaid(false);
		level.addFreshEntity(raider);
		return true;
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
