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
 * Волны осады. Группа мобов появляется на поверхности далеко от игрока
 * (как налётчики перед рейдом на деревню), и идёт к нему. Звуков при появлении нет.
 * Кто именно в группе — решает состав осады {@link SiegeComposition} (нежить и твари или иллагеры, в случайных долях).
 *
 * Как мобы находят игрока — отдельные цели, не этот класс:
 *   {@link com.example.entity.ai.SiegeTargetGoal} держит игрока целью,
 *   {@link com.example.entity.ai.SiegeMarchGoal} ведёт к нему отрезками, пока он далеко,
 *   и по дороге держит боевую стойку (смотрит на игрока, у зомби подняты руки).
 * Вблизи включается обычная драка или стрельба.
 *
 * Метка мобов осады — обычный тег сущности {@link #TAG}. Он сохраняется вместе с мобом сам.
 * Мобы с тегом слушаются осаду только пока она идёт.
 *
 * Не ставит парящие инструменты, големов, мини-боссов и события мода — только мобов из состава осады.
 */
public final class SiegeWaves {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Через сколько тиков после начала ночи приходит первая волна. 600 = 30 секунд. */
	public static final int FIRST_WAVE_DELAY_TICKS = 600;

	/**
	 * Пауза между волнами: случайное число тиков от минимума до максимума.
	 * 1200 и 1800 = от 60 до 90 секунд. Ночь длится около 8 минут, так что выходит 6-8 волн.
	 */
	public static final int WAVE_PAUSE_MIN_TICKS = 1200;
	public static final int WAVE_PAUSE_MAX_TICKS = 1800;

	/** Если место под волну не нашли или рядом слишком много мобов, пробуем снова через столько тиков. */
	public static final int RETRY_TICKS = 100;

	/**
	 * Размер обычной волны, от и до. Считается в «цене» мобов (см. SiegeComposition.Kind):
	 * обычный моб стоит 1, вызыватель 2, разоритель 3. Так в волну из 12 влезет 12 зомби или 4 разорителя.
	 */
	public static final int WAVE_SIZE_MIN = 8;
	public static final int WAVE_SIZE_MAX = 15;

	/**
	 * Ночь делится на доли от 0.0 (начало) до 1.0 (рассвет).
	 * До этой доли волны слабее: размер умножается на EARLY_SIZE_FACTOR. 0.30 = первая треть ночи.
	 */
	public static final float EARLY_UNTIL = 0.30f;

	/** Во сколько раз меньше волны в начале ночи. 0.6 = 60% от обычного размера. 1.0 = без разницы. */
	public static final float EARLY_SIZE_FACTOR = 0.6f;

	/** С какой доли ночи волны идут с двух сторон сразу (клещи). 0.50 = с полуночи. 1.1 = никогда. */
	public static final float TWO_SIDES_FROM = 0.50f;

	/** С какой доли ночи приходит одна последняя большая волна (с двух сторон). 0.85 = перед рассветом. */
	public static final float FINAL_WAVE_FROM = 0.85f;

	/** Во сколько раз последняя волна больше обычной. 1.5 = на половину больше. */
	public static final float FINAL_WAVE_SIZE_FACTOR = 1.5f;

	/**
	 * Сколько мобов осады может быть живо вокруг одного игрока (в радиусе CONTROL_RADIUS).
	 * Больше — страшнее, но сильнее нагрузка на сервер: у каждого моба поиск пути.
	 * Если мобов уже столько, новая волна ждёт.
	 */
	public static final int MAX_ALIVE_PER_PLAYER = 80;

	/** Если свободных мест меньше, волну не зовём, ждём (чтобы не приходили по одному-двое). */
	public static final int MIN_FREE_SLOTS = 4;

	/**
	 * true — игроки в творческом режиме тоже считаются целью осады: волны приходят к ним, мобы идут к ним.
	 * Удобно для проверки в творческом режиме. Мобы творческого игрока всё равно не бьют (так устроена игра).
	 * false — как в обычной игре: творческого режима для осады «не существует», волн рядом с ним нет
	 * и мобы осады стоят на месте. Наблюдатели (spectator) не считаются никогда.
	 */
	public static final boolean INCLUDE_CREATIVE_PLAYERS = true;

	/** На каком расстоянии от игрока появляется волна, в блоках. Дальше дистанции прогрузки нельзя. */
	public static final int SPAWN_DISTANCE_MIN = 50;
	public static final int SPAWN_DISTANCE_MAX = 70;

	/** Разброс направления одной группы, в радианах. 0.35 = примерно ±20 градусов. */
	public static final double SIDE_SPREAD_RADIANS = 0.35;

	/** Ближе этого к любому игроку волна не появляется, в блоках. 24 = как у обычного спавна игры. */
	public static final int MIN_PLAYER_DISTANCE = 24;

	/** В каком радиусе от центра волны встают мобы, в блоках. */
	public static final int CLUSTER_RADIUS = 5;

	/** Сколько точек перебрать для центра волны и для каждого моба. */
	public static final int SPOT_TRIES = 24;
	public static final int MEMBER_TRIES = 12;

	/** Как часто можно заново строить путь к игроку, в тиках. 10 = не чаще двух раз в секунду.
	 *  Каждый пересчёт на миг останавливает моба. 1 = рывки и лаг, стрелы тоже летят скачками. */
	public static final int REPATH_COOLDOWN_TICKS = 10;

	/** В каком радиусе от игрока искать и вести мобов осады, в блоках. */
	public static final double CONTROL_RADIUS = 110.0;

	/** Радиус обзора мобов осады (атрибут игры). Обычный зомби — 35. Больше — дальше строится путь, но тяжелее. */
	public static final double FOLLOW_RANGE = 96.0;

	/** Ближе этого моб перестаёт идти отрезками и начинает драться или стрелять, в блоках. */
	public static final double MARCH_STOP_DISTANCE = 16.0;

	/**
	 * Дальше этого он снова идёт к игроку, если тот убежал.
	 * Должно быть больше MARCH_STOP_DISTANCE. Если числа равны, моб дёргается на границе:
	 * то идёт, то стреляет, и стрела срывается.
	 */
	public static final double MARCH_RESUME_DISTANCE = 28.0;

	/** Длина одного отрезка пути, когда цель далеко, в блоках. */
	public static final double MARCH_STEP_BLOCKS = 24.0;

	/** Скорость отрезка. 1.0 = обычный шаг моба. Больше — быстрее. */
	public static final double MARCH_SPEED = 1.0;

	// ========================================================================

	/** Метка мобов осады. Сохраняется вместе с мобом. */
	public static final String TAG = "evolvingthreats_siege";

	private static int waveCountdown = -1;
	private static boolean finalWaveDone;

	private SiegeWaves() {
	}

	/** Новая ночь осады: первая волна через FIRST_WAVE_DELAY_TICKS, последняя ещё не была. */
	public static void reset() {
		waveCountdown = Math.max(1, FIRST_WAVE_DELAY_TICKS);
		finalWaveDone = false;
	}

	/** Выключение сервера. */
	public static void clearRuntime() {
		waveCountdown = -1;
		finalWaveDone = false;
	}

	/** true — моб из осады (волна или обычный ночной спавн во время осады). */
	public static boolean isSiegeMob(Entity entity) {
		return entity.isAlive() && entity.entityTags().contains(TAG);
	}

	/**
	 * Помечает моба как мобов осады и даёт ему большой радиус обзора.
	 * Вызывается и для волны, и для зомби и скелетов, что появились сами во время осады.
	 */
	public static void enlist(Mob mob) {
		mob.addTag(TAG);
		AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (range != null) {
			range.setBaseValue(FOLLOW_RANGE);
		}
	}

	/**
	 * Каждый тик ночи осады. progress — доля ночи от 0.0 до 1.0.
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

	/** Правила игры «спавн мобов» и «спавн монстров» выключены — волн нет (как и обычного ночного спавна). */
	private static boolean canSpawnWaves(ServerLevel level) {
		return Boolean.TRUE.equals(level.getGameRules().get(GameRules.SPAWN_MOBS))
				&& Boolean.TRUE.equals(level.getGameRules().get(GameRules.SPAWN_MONSTERS));
	}

	// ------------------------------------------------------------------
	// Появление волны
	// ------------------------------------------------------------------

	/** true — хотя бы одна группа появилась. */
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
	 * Ставит одну группу в направлении angle от игрока. budget — размер группы в «цене» мобов
	 * (обычный моб стоит 1, разоритель 3). Возвращает, сколько мобов реально появилось.
	 */
	private static int spawnGroup(ServerLevel level, ServerPlayer player, double angle, int budget, float[] shares) {
		BlockPos center = findCenter(level, player, angle);
		if (center == null) {
			return 0;
		}
		RandomSource random = level.getRandom();
		int spawned = 0;
		int remaining = budget;
		// Не все точки подходят (особенно крупным мобам), поэтому попыток вдвое больше размера.
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
			// Не трогаем чужие и незагруженные куски мира: иначе игра начнёт их генерировать.
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
		// Крупные мобы (разоритель, паук) не влезают в тесные места: такой точке пробуем другого моба.
		if (!level.noCollision(mob)) {
			return false;
		}

		// NATURAL = как ночной спавн игры: броня, оружие, чары и особые роли мода выдаются как обычно.
		// Мини-боссы в это время не выпадают, их выключает SiegeNight.isWindowOpen.
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
		enlist(mob);
		mob.setTarget(player);
		level.addFreshEntity(mob);
		return true;
	}

	// ------------------------------------------------------------------
	// Кого вести к игроку (используют SiegeTargetGoal и SiegeMarchGoal)
	// ------------------------------------------------------------------

	/**
	 * Ближайший живой игрок в выживании или приключении, не дальше {@link #CONTROL_RADIUS}.
	 * Наблюдатели не считаются, творческий режим — по INCLUDE_CREATIVE_PLAYERS. null — рядом никого нет.
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
	// Помощники
	// ------------------------------------------------------------------

	/** Считает ли осада этого игрока целью: жив, не наблюдатель, и (если INCLUDE_CREATIVE_PLAYERS = false) не в творческом. */
	private static boolean isHuntable(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && (INCLUDE_CREATIVE_PLAYERS || !player.isCreative());
	}

	/** Кого осада считает целью, см. {@link #isHuntable}. */
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

	/** Можно ли поставить моба на эту клетку: твёрдый пол, нет воды, тело ни во что не упирается. */
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
