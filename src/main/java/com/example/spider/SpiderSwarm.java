package com.example.spider;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Редкий рой: обычный паук при появлении приводит с собой ещё нескольких таких же.
 * Не матка и не ткач. Пещерные пауки и наездники сюда не попадают — их отсекает
 * {@link SpiderThreats} до вызова.
 * Помощники создаются как «призванные», поэтому сами новый рой не открывают.
 */
public final class SpiderSwarm {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — роёв нет, пауки появляются как раньше. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный паук окажется началом роя.
	 * 0.02 = 2%. Реже матки (4%): рой сразу добавляет несколько пауков.
	 * Бросок общий: сначала матка, потом ткач, потом рой.
	 */
	public static final float SPAWN_CHANCE = 0.02f;

	/**
	 * Сколько пауков в рое всего, включая того, кто его начал.
	 * 6 и 10 = от шести до десяти.
	 */
	public static final int PACK_MIN = 6;
	public static final int PACK_MAX = 10;

	/**
	 * Как далеко от первого паука искать место, в блоках.
	 * 2.0 и 6.0 = от двух до шести блоков в сторону, чтобы не встали друг в друга.
	 */
	public static final double RADIUS_MIN = 2.0;
	public static final double RADIUS_MAX = 6.0;

	/**
	 * Сколько случайных точек перебрать для одного паука.
	 * Не нашёл свободный пол — этот паук просто не появляется, рой будет меньше.
	 */
	public static final int SPOT_TRIES = 16;

	/**
	 * На сколько блоков вверх и вниз от ног первого паука ещё смотреть пол.
	 * 3 = неровная пещера. 0 = только тот же уровень.
	 */
	public static final int VERTICAL_STEPS = 3;

	// ========================================================================

	private SpiderSwarm() {
	}

	/**
	 * Добирает пауков до размера пачки. Сам первый паук уже стоит в мире.
	 * Вызывается один раз при его появлении, и только если это настоящий мир, не генерация чанка.
	 */
	public static void spawnPack(Spider leader, ServerLevel level) {
		if (!ENABLED) {
			return;
		}
		int min = Math.max(1, PACK_MIN);
		int max = Math.max(min, PACK_MAX);
		int total = leader.getRandom().nextIntBetweenInclusive(min, max);
		int extras = total - 1;
		for (int i = 0; i < extras; i++) {
			spawnOne(leader, level);
		}
	}

	private static void spawnOne(Spider leader, ServerLevel level) {
		Vec3 pos = findOpenSpot(level, leader);
		if (pos == null) {
			return;
		}
		Spider spider = EntityTypes.SPIDER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (spider == null) {
			return;
		}
		spider.snapTo(pos.x, pos.y, pos.z, leader.getRandom().nextFloat() * 360.0f, 0.0f);
		DifficultyInstance difficulty = level.getCurrentDifficultyAt(spider.blockPosition());
		// MOB_SUMMONED: в SpiderThreats такой паук остаётся обычным и новый рой не зовёт.
		spider.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, (SpawnGroupData) null);
		level.addFreshEntity(spider);
	}

	/** Точка рядом: твёрдый пол и тело паука не внутри блока. Иначе он задохнётся в стене. */
	private static Vec3 findOpenSpot(ServerLevel level, Spider leader) {
		var random = leader.getRandom();
		double min = RADIUS_MIN;
		double max = Math.max(min, RADIUS_MAX);
		int baseY = leader.blockPosition().getY();
		for (int attempt = 0; attempt < SPOT_TRIES; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double radius = min + random.nextDouble() * (max - min);
			double x = leader.getX() + Math.cos(angle) * radius;
			double z = leader.getZ() + Math.sin(angle) * radius;
			for (int dy = VERTICAL_STEPS; dy >= -VERTICAL_STEPS; dy--) {
				int y = baseY + dy;
				if (canStand(level, x, y, z)) {
					return new Vec3(x, y, z);
				}
			}
		}
		return null;
	}

	/**
	 * Паук низкий (ниже одного блока), но широкий.
	 * Поэтому не требуем два блока воздуха, как зомби: подходит ли место, решает его настоящая коробка.
	 */
	private static boolean canStand(ServerLevel level, double x, int y, double z) {
		BlockPos feet = BlockPos.containing(x, y, z);
		if (!level.getWorldBorder().isWithinBounds(feet)) {
			return false;
		}
		if (!hasCollision(level, feet.below())) {
			return false;
		}
		if (level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(feet).is(FluidTags.WATER)) {
			return false;
		}
		AABB body = EntityTypes.SPIDER.getDimensions().makeBoundingBox(x, y, z);
		return level.noCollision(body);
	}

	private static boolean hasCollision(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.getCollisionShape(level, pos).isEmpty();
	}
}
