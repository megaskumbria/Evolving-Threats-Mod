package com.example.creeper;

import com.example.entity.DynamiteCreeperEntity;
import com.example.entity.SapperCreeperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Редкий крипер, который взрывает то, на чём стоит игрок.
 * Сила взрыва и шипение как у обычного крипера (1.5 сек).
 * Если до игрока нет пути и его не видно (стена, закрытый дом) — подходит как можно ближе,
 * ждёт {@link #WALL_DELAY_MIN_SECONDS}–{@link #WALL_DELAY_MAX_SECONDS} и взрывается у стены.
 * В чистом поле шипение гаснет, если отойти, как у обычного крипера.
 * Игрока помнит {@link #MEMORY_SECONDS} секунд, даже если тот скрылся из виду.
 */
public final class SapperCreeper {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — подрывники не появляются, команда тоже не делает подрывника. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0) при спавне обычного крипера стать подрывником. 0.05 = 5%.
	 * Как у скелета с тыквой-маяком. Метатель динамита на этого крипера уже не выпадет.
	 * Команда спавна шанс не использует.
	 */
	public static final float SPAWN_CHANCE = 0.05f;

	/**
	 * Горизонтально ближе этого (блоки) к колонне под игроком — сразу подрыв.
	 * 2.2 ≈ вплотную к башне или к клетке, на которой стоит игрок.
	 */
	public static final double DETONATE_HORIZONTAL = 2.2;

	/**
	 * Игрок выше крипера минимум на столько блоков — это башня/лестница/пол,
	 * подрыв у основания, даже если наверх пути нет.
	 */
	public static final int ELEVATED_BLOCKS = 2;

	/**
	 * Насколько близко (блоки, по клеткам) путь должен подойти к цели, чтобы считать «дойти можно».
	 * 0 = надо встать ровно в клетку игрока: в чистом поле это часто «пути нет», и после удара крипер взрывался.
	 * 2 = достаточно подойти вплотную, как в бою.
	 */
	public static final int PATH_REACH_BLOCKS = 2;

	/** Сколько ждать у стены перед взрывом, секунды (случайное число между ними). */
	public static final int WALL_DELAY_MIN_SECONDS = 8;
	public static final int WALL_DELAY_MAX_SECONDS = 10;

	/**
	 * Считать, что крипер упёрся в стену и стоит, если за {@link #STUCK_CHECK_TICKS} тиков
	 * он сдвинулся меньше, чем на столько блоков.
	 */
	public static final double STUCK_MOVE_BLOCKS = 0.6;

	/** Окно проверки «застрял», тики. 20 = 1 секунда. */
	public static final int STUCK_CHECK_TICKS = 20;

	/**
	 * Сколько секунд помнит игрока после того, как перестал его видеть (забежал в дом и закрылся).
	 * За это время успевает дойти до стены, выждать 8–10 секунд и взорваться.
	 */
	public static final int MEMORY_SECONDS = 90;

	/** Дальше этого (блоки) от запомненного игрока подрывник сдаётся. */
	public static final double CHASE_MAX_DISTANCE = 40.0;

	/** Как далеко видит игрока. */
	public static final double FOLLOW_RANGE = 32.0;

	/** Скорость бега к опоре (1.0 = обычный шаг крипера). */
	public static final double MOVE_SPEED = 1.1;

	/** Как часто заново проверять «есть ли путь», тики. 10 = 0.5 сек. */
	public static final int PATH_CHECK_TICKS = 10;

	// ========================================================================

	private SapperCreeper() {
	}

	public static boolean isSapper(Creeper creeper) {
		return ENABLED
				&& creeper instanceof SapperCreeperEntity data
				&& data.evolvingthreats$isSapperCreeper();
	}

	/** Вызвать из finalizeSpawn. Командный спавн ставит флаг заранее. */
	public static void rollOnSpawn(Creeper creeper, EntitySpawnReason reason) {
		if (!ENABLED || !(creeper instanceof SapperCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$isSapperCreeper()) {
			return;
		}
		if (creeper instanceof DynamiteCreeperEntity dynamite && dynamite.evolvingthreats$isDynamiteCreeper()) {
			return;
		}
		if (reason == EntitySpawnReason.COMMAND) {
			return;
		}
		if (creeper.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		apply(creeper);
	}

	/** Команда: подрывник перед игроком. */
	public static Creeper spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper == null) {
			throw new IllegalStateException("Failed to create sapper creeper");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		creeper.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (creeper instanceof SapperCreeperEntity data) {
			data.evolvingthreats$setSapperCreeper(true);
		}
		creeper.finalizeSpawn(level, level.getCurrentDifficultyAt(creeper.blockPosition()), EntitySpawnReason.COMMAND, null);
		apply(creeper);
		if (lookAt != null) {
			creeper.setTarget(lookAt);
		}
		level.addFreshEntity(creeper);
		return creeper;
	}

	private static void apply(Creeper creeper) {
		if (creeper instanceof SapperCreeperEntity data) {
			data.evolvingthreats$setSapperCreeper(true);
		}
		var follow = creeper.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(FOLLOW_RANGE);
		}
	}

	/** Клетка пола под ногами игрока — то, что нужно взорвать. */
	public static BlockPos supportUnder(LivingEntity target) {
		return target.blockPosition().below();
	}

	public static boolean canReach(Creeper creeper, LivingEntity target) {
		Path path = creeper.getNavigation().createPath(target, PATH_REACH_BLOCKS);
		return path != null && path.canReach();
	}

	public static boolean canReach(Creeper creeper, BlockPos pos) {
		Path path = creeper.getNavigation().createPath(pos, PATH_REACH_BLOCKS);
		return path != null && path.canReach();
	}

	/** Между крипером и игроком нет стены: удар и отход в чистом поле, а не дом. */
	public static boolean canSee(Creeper creeper, LivingEntity target) {
		return creeper.hasLineOfSight(target);
	}

	public static double horizontalDistance(Creeper creeper, LivingEntity target) {
		double dx = target.getX() - creeper.getX();
		double dz = target.getZ() - creeper.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	public static boolean isElevated(Creeper creeper, LivingEntity target) {
		return target.getY() >= creeper.getY() + ELEVATED_BLOCKS;
	}

	/** Случайная пауза у стены, тики. */
	public static int randomWallDelayTicks(Creeper creeper) {
		int min = WALL_DELAY_MIN_SECONDS * 20;
		int max = WALL_DELAY_MAX_SECONDS * 20;
		return max <= min ? min : creeper.getRandom().nextIntBetweenInclusive(min, max);
	}

	/** Поджечь запал: обычное шипение 1.5 сек и взрыв обычной силы. Повторный вызов ничего не делает. */
	public static void detonate(Creeper creeper) {
		if (!creeper.isIgnited()) {
			creeper.ignite();
		}
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
