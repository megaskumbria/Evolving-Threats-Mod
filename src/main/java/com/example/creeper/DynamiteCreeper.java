package com.example.creeper;

import com.example.entity.DynamiteCreeperEntity;
import com.example.entity.SapperCreeperEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Обычный на вид крипер, который не подбегает взрываться, а кидает зажжённый динамит и отступает.
 * Выглядит как ванильный крипер — игрок узнаёт его только когда прилетает TNT.
 */
public final class DynamiteCreeper {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — все криперы обычные, команда спавна тоже не делает метателя. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный крипер при появлении в мире окажется метателем.
	 * 0.04 = 4%. Команда спавна этот шанс не использует — она всегда создаёт метателя.
	 */
	public static final float SPAWN_CHANCE = 0.04f;

	/**
	 * Ближе этого (блоки) крипер пятится от игрока и не кидает динамит.
	 * 8 = держит дистанцию примерно в 8 блоков.
	 */
	public static final double RETREAT_DISTANCE = 8.0;

	/**
	 * Дальше этого (блоки) крипер подходит ближе, чтобы кинуть.
	 * Между {@link #RETREAT_DISTANCE} и этим числом он кружит боком и бросает.
	 */
	public static final double THROW_MAX_DISTANCE = 16.0;

	/**
	 * На какой дистанции (блоки) крипер старается держаться, пока кидает динамит.
	 * Меньше {@link #RETREAT_DISTANCE} он пятится, больше {@link #THROW_MAX_DISTANCE} — подходит.
	 */
	public static final double HOLD_DISTANCE = 12.0;

	/**
	 * На сколько блоков в сторону от игрока смещается точка, к которой он идёт.
	 * Больше — шире кружит. 6 = заметно бегает, но остаётся в зоне броска.
	 */
	public static final double STRAFE_SIDE_BLOCKS = 6.0;

	/** Скорость бега боком (1.0 = обычный шаг крипера). */
	public static final double STRAFE_SPEED = 1.1;

	/** Как часто меняет сторону кружения, секунды (случайное число между ними). */
	public static final int STRAFE_SWITCH_MIN_SECONDS = 2;
	public static final int STRAFE_SWITCH_MAX_SECONDS = 4;

	/**
	 * Дальше этого (блоки) метатель бросает погоню.
	 * Чуть больше ванильной дальности зрения крипера, чтобы он успевал выйти на бросок.
	 */
	public static final double CHASE_MAX_DISTANCE = 28.0;

	/** Как далеко (блоки) крипер «видит» игрока. У обычного крипера около 16. */
	public static final double FOLLOW_RANGE = 32.0;

	/** Пауза между бросками, секунды. 8 = один динамит раз в 8 секунд. */
	public static final int THROW_COOLDOWN_SECONDS = 8;

	/** Запал динамита: минимум и максимум, секунды (случайное число между ними). 2 и 3 = взрыв через 2–3 сек. */
	public static final int FUSE_MIN_SECONDS = 2;
	public static final int FUSE_MAX_SECONDS = 3;

	/** Скорость отступления (1.0 = обычный шаг крипера, 1.2 = чуть быстрее убегает). */
	public static final double RETREAT_SPEED = 1.2;

	/** Скорость, с которой подходит на дистанцию броска. */
	public static final double APPROACH_SPEED = 1.0;

	/**
	 * Горизонтальная скорость динамита, блоки за тик (20 тиков = 1 сек).
	 * 0.70 = долетает до игрока в 12 блоках примерно за секунду и падает к ногам, а не над головой.
	 * Больше — быстрее и более настильно. Меньше — выше дуга.
	 */
	public static final double THROW_BLOCKS_PER_TICK = 0.70;

	/**
	 * За сколько тиков до взрыва динамит должен уже быть у ног.
	 * 8 = 0.4 сек лежит на земле рядом с игроком, потом взрывается.
	 * Меньше — взрыв ещё в полёте. Больше — дольше лежит, игрок успеет отойти.
	 */
	public static final int LAND_BEFORE_EXPLODE_TICKS = 8;

	/**
	 * Случайный сдвиг точки падения, блоки. 0.4 = почти в игрока, но не идеальный снайпер.
	 * Больше — чаще промах мимо ног.
	 */
	public static final double AIM_SPREAD_BLOCKS = 0.4;

	/** Гравитация ванильного TNT за тик. Не меняй, если не менялась сама игра. */
	private static final double TNT_GRAVITY = 0.04;

	/** Замедление TNT в воздухе за тик (ваниль). Не меняй, если не менялась сама игра. */
	private static final double TNT_AIR_DRAG = 0.98;

	// ========================================================================

	private DynamiteCreeper() {
	}

	public static boolean isDynamite(Creeper creeper) {
		return ENABLED
				&& creeper instanceof DynamiteCreeperEntity data
				&& data.evolvingthreats$isDynamiteCreeper();
	}

	/** Вызвать из finalizeSpawn. Командный спавн ставит флаг заранее и сюда не попадает повторно. */
	public static void rollOnSpawn(Creeper creeper, ServerLevelAccessor level, EntitySpawnReason reason) {
		if (!ENABLED || !(creeper instanceof DynamiteCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$isDynamiteCreeper()) {
			return;
		}
		if (creeper instanceof SapperCreeperEntity sapper && sapper.evolvingthreats$isSapperCreeper()) {
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

	/** Команда: крипер-метатель перед игроком (или в точке источника команды). */
	public static Creeper spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper == null) {
			throw new IllegalStateException("Failed to create dynamite creeper");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		creeper.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (creeper instanceof DynamiteCreeperEntity data) {
			data.evolvingthreats$setDynamiteCreeper(true);
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
		if (creeper instanceof DynamiteCreeperEntity data) {
			data.evolvingthreats$setDynamiteCreeper(true);
		}
		var follow = creeper.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(FOLLOW_RANGE);
		}
	}

	public static int cooldownTicks() {
		return THROW_COOLDOWN_SECONDS * 20;
	}

	/** Кинуть зажжённый динамит в цель. Динамит — обычный ванильный TNT, падает к ногам. */
	public static void throwTnt(Creeper creeper, LivingEntity target) {
		if (!(creeper.level() instanceof ServerLevel level)) {
			return;
		}
		double spawnX = creeper.getX();
		double spawnY = creeper.getY() + creeper.getBbHeight() * 0.8;
		double spawnZ = creeper.getZ();

		PrimedTnt tnt = new PrimedTnt(level, spawnX, spawnY, spawnZ, creeper);
		int fuseMin = FUSE_MIN_SECONDS * 20;
		int fuseMax = FUSE_MAX_SECONDS * 20;
		int fuse = fuseMax <= fuseMin ? fuseMin : creeper.getRandom().nextIntBetweenInclusive(fuseMin, fuseMax);
		tnt.setFuse(fuse);

		double spread = (creeper.getRandom().nextDouble() - 0.5) * AIM_SPREAD_BLOCKS;
		double spreadZ = (creeper.getRandom().nextDouble() - 0.5) * AIM_SPREAD_BLOCKS;
		// Небольшой упреждение, если игрок бежит. Стоящий игрок — почти без сдвига.
		Vec3 motion = target.getDeltaMovement();
		double landX = target.getX() + motion.x * 8.0 + spread;
		double landZ = target.getZ() + motion.z * 8.0 + spreadZ;
		double landY = target.getY();

		double dx = landX - spawnX;
		double dy = landY - spawnY;
		double dz = landZ - spawnZ;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		int travelTicks = (int) Math.ceil(horizontal / THROW_BLOCKS_PER_TICK);
		int latestLanding = Math.max(6, fuse - LAND_BEFORE_EXPLODE_TICKS);
		travelTicks = Mth.clamp(travelTicks, 6, latestLanding);

		double dragSum = horizontalDragSum(travelTicks);
		double vx = dragSum < 1.0E-4 ? 0.0 : dx / dragSum;
		double vz = dragSum < 1.0E-4 ? 0.0 : dz / dragSum;
		double vy = verticalSpeedToLand(dy, travelTicks);
		tnt.setDeltaMovement(vx, vy, vz);

		level.addFreshEntity(tnt);
		level.playSound(null, creeper.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0f, 1.0f);
	}

	/** Сколько блоков по горизонтали пролетит TNT за {@code ticks}, если начальная скорость 1. */
	private static double horizontalDragSum(int ticks) {
		double sum = 0.0;
		double drag = 1.0;
		for (int i = 0; i < ticks; i++) {
			sum += drag;
			drag *= TNT_AIR_DRAG;
		}
		return sum;
	}

	/**
	 * Начальная скорость вверх, чтобы через {@code ticks} тиков TNT оказался на нужной высоте.
	 * Каждый тик игра сначала прибавляет гравитацию, потом двигает, потом замедляет (как у ванильного TNT).
	 */
	private static double verticalSpeedToLand(double dy, int ticks) {
		double atZero = simulateVertical(0.0, ticks);
		double atOne = simulateVertical(1.0, ticks);
		double perSpeed = atOne - atZero;
		if (Math.abs(perSpeed) < 1.0E-4) {
			return 0.2;
		}
		return (dy - atZero) / perSpeed;
	}

	private static double simulateVertical(double initialSpeed, int ticks) {
		double y = 0.0;
		double vy = initialSpeed;
		for (int i = 0; i < ticks; i++) {
			vy -= TNT_GRAVITY;
			y += vy;
			vy *= TNT_AIR_DRAG;
		}
		return y;
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
