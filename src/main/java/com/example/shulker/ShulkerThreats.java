package com.example.shulker;

import com.example.entity.ShulkerBulletTraits;
import com.example.entity.ShulkerVariant;
import com.example.mixin.ShulkerTeleportAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Шалкеры стреляют не только обычной левитацией и в бою пересаживаются на соседний блок.
 * Редкие свойства решаются один раз при появлении. Длинная левитация — отдельно у каждого снаряда.
 * С виду шалкеры одинаковые: кто редкий, видно только по попаданию.
 */
public final class ShulkerThreats {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — шалкеры как в обычной игре: один снаряд, левитация 10 секунд, телепорт только когда ранен. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что шалкер при появлении стреляет замедлением.
	 * 0.12 = 12%, примерно каждый восьмой. Левитация при этом остаётся.
	 * 0 = замедления нет. 1 = замедление у каждого шалкера.
	 */
	public static final float SLOWNESS_CHANCE = 0.12f;

	/**
	 * Сколько секунд длится замедление. 20 = двадцать секунд.
	 * Обычная левитация держится 10 секунд, поэтому после приземления ещё есть замедление.
	 * 0 = эффект не появится.
	 */
	public static final int SLOWNESS_SECONDS = 20;

	/**
	 * Сила замедления. 0 = Замедление I, шаг примерно на 15% короче.
	 * 1 = Замедление II, примерно на 30% короче.
	 */
	public static final int SLOWNESS_AMPLIFIER = 0;

	/**
	 * Шанс (0.0–1.0), что шалкер выпускает два снаряда.
	 * 0.30 = 30%, примерно каждый третий. Второй вылетает в ту же цель.
	 * 0 = всегда один снаряд. 1 = два снаряда у каждого.
	 */
	public static final float DOUBLE_SHOT_CHANCE = 0.30f;

	/**
	 * Пауза между двумя снарядами, в тиках. 10 = полсекунды. 20 = одна секунда.
	 * 0 = второго снаряда нет, даже если шалкер умеет стрелять дважды.
	 */
	public static final int SECOND_SHOT_TICKS = 10;

	/**
	 * Как часто в бою шалкер пересаживается на соседний блок, в секундах.
	 * 8 = раз в восемь секунд, пока есть цель. В обычной игре он прыгает далеко
	 * только когда его сильно ранили, и не по таймеру.
	 * 0 = такой пересадки нет, остаётся только обычный прыжок игры.
	 */
	public static final int NEIGHBOR_HOP_SECONDS = 8;

	/**
	 * На сколько блоков в сторону он пересаживается. 1 = только соседние клетки,
	 * включая диагональ. 2 = уже на две клетки.
	 */
	public static final int NEIGHBOR_RANGE = 1;

	/**
	 * Шанс (0.0–1.0), что конкретный снаряд даст длинную левитацию.
	 * 0.25 = 25%, примерно каждый четвёртый снаряд. Бросок заново на каждый выстрел.
	 * 0 = всегда обычные 10 секунд. 1 = каждый снаряд длинный.
	 */
	public static final float LONG_LEVITATION_CHANCE = 0.25f;

	/**
	 * Сколько секунд длится длинная левитация. 18 = восемнадцать секунд.
	 * Обычный снаряд игры держит 10. Молоко снимает эффект сразу.
	 */
	public static final int LONG_LEVITATION_SECONDS = 18;

	/**
	 * Шанс (0.0–1.0), что шалкер при появлении телепортирует игрока попаданием.
	 * 0.08 = 8%, примерно каждый двенадцатый. Другие мобы не телепортируются.
	 * 0 = телепорта нет. 1 = телепортирует каждый шалкер.
	 */
	public static final float TELEPORT_CHANCE = 0.08f;

	/**
	 * Минимальная дальность телепорта игрока, в блоках. 3 = три блока в сторону.
	 * Игрок остаётся на той же высоте или на блок выше/ниже, если там есть пол.
	 */
	public static final int TELEPORT_MIN_BLOCKS = 3;

	/** Максимальная дальность телепорта игрока, в блоках. 5 = пять блоков. */
	public static final int TELEPORT_MAX_BLOCKS = 5;

	/** Сколько раз искать свободное место. Не нашёл — игрок остаётся, эффект снаряда всё равно есть. */
	public static final int TELEPORT_ATTEMPTS = 8;

	// ========================================================================

	/** Обычная левитация снаряда шалкера: 10 секунд. */
	private static final int VANILLA_LEVITATION_TICKS = 200;

	/** В одной секунде 20 тиков. */
	private static final int TICKS_PER_SECOND = 20;

	/** Радиус, с которым игра пакует короткий телепорт шалкера для картинки. Менять не нужно. */
	private static final int VANILLA_TELEPORT_PACK_RADIUS = 8;

	private ShulkerThreats() {
	}

	/** Один раз при появлении. Повторный вызов и загрузка из сохранения шанс не перебрасывают. */
	public static void rollOnSpawn(Shulker shulker) {
		if (!ENABLED || !(shulker instanceof ShulkerVariant data) || data.evolvingthreats$rolled()) {
			return;
		}
		data.evolvingthreats$setRolled(true);
		RandomSource random = shulker.getRandom();
		if (SLOWNESS_CHANCE > 0.0f && random.nextFloat() < SLOWNESS_CHANCE) {
			data.evolvingthreats$setSlows(true);
		}
		if (DOUBLE_SHOT_CHANCE > 0.0f && random.nextFloat() < DOUBLE_SHOT_CHANCE) {
			data.evolvingthreats$setDoubleShot(true);
		}
		if (TELEPORT_CHANCE > 0.0f && random.nextFloat() < TELEPORT_CHANCE) {
			data.evolvingthreats$setTeleportsPlayer(true);
		}
		data.evolvingthreats$setHopTicks(hopIntervalTicks());
	}

	/** Каждый тик шалкера на сервере: второй снаряд и пересадка на соседний блок. */
	public static void tick(Shulker shulker) {
		if (!ENABLED || shulker.level().isClientSide() || !(shulker instanceof ShulkerVariant data)) {
			return;
		}
		int second = data.evolvingthreats$secondShotTicks();
		if (second > 0) {
			second--;
			data.evolvingthreats$setSecondShotTicks(second);
			if (second == 0) {
				fireSecondShot(shulker);
			}
		}
		if (NEIGHBOR_HOP_SECONDS <= 0 || NEIGHBOR_RANGE <= 0) {
			return;
		}
		LivingEntity target = shulker.getTarget();
		if (target == null || !target.isAlive() || shulker.isPassenger() || shulker.isNoAi()) {
			data.evolvingthreats$setHopTicks(hopIntervalTicks());
			return;
		}
		int hop = data.evolvingthreats$hopTicks() - 1;
		if (hop > 0) {
			data.evolvingthreats$setHopTicks(hop);
			return;
		}
		data.evolvingthreats$setHopTicks(hopIntervalTicks());
		hopToNeighbor(shulker);
	}

	/**
	 * Обычный выстрел шалкера уже создал снаряд. Помечает его и, если шалкер умеет,
	 * ставит второй снаряд в очередь.
	 */
	public static boolean launch(Level level, Entity entity) {
		if (entity instanceof ShulkerBullet bullet) {
			mark(bullet);
		}
		boolean added = level.addFreshEntity(entity);
		if (!ENABLED || !added || !(entity instanceof ShulkerBullet bullet)) {
			return added;
		}
		if (SECOND_SHOT_TICKS <= 0) {
			return true;
		}
		if (bullet.getOwner() instanceof Shulker shulker
				&& shulker instanceof ShulkerVariant data
				&& data.evolvingthreats$doubleShot()
				&& data.evolvingthreats$secondShotTicks() <= 0) {
			data.evolvingthreats$setSecondShotTicks(SECOND_SHOT_TICKS);
		}
		return true;
	}

	/**
	 * Снаряд уже попал и игра собралась повесить левитацию.
	 * Здесь длина левитации, замедление и телепорт игрока.
	 */
	public static boolean onHit(ShulkerBullet bullet, LivingEntity target, MobEffectInstance vanilla, Entity source) {
		int duration = vanilla.getDuration();
		if (ENABLED
				&& bullet instanceof ShulkerBulletTraits traits
				&& traits.evolvingthreats$longLevitation()
				&& LONG_LEVITATION_SECONDS > 0) {
			duration = LONG_LEVITATION_SECONDS * TICKS_PER_SECOND;
		}
		if (duration <= 0) {
			duration = VANILLA_LEVITATION_TICKS;
		}
		boolean applied = target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, duration), source);
		if (!ENABLED || !(bullet instanceof ShulkerBulletTraits traits)) {
			return applied;
		}
		if (traits.evolvingthreats$slowness() && SLOWNESS_SECONDS > 0) {
			target.addEffect(new MobEffectInstance(
					MobEffects.SLOWNESS,
					SLOWNESS_SECONDS * TICKS_PER_SECOND,
					Math.max(0, SLOWNESS_AMPLIFIER),
					false,
					true,
					true), source);
		}
		if (traits.evolvingthreats$teleportsPlayer() && target instanceof Player player) {
			teleportPlayer(player);
		}
		return applied;
	}

	private static void mark(ShulkerBullet bullet) {
		if (!ENABLED || !(bullet instanceof ShulkerBulletTraits traits)) {
			return;
		}
		if (bullet.getOwner() instanceof ShulkerVariant data) {
			traits.evolvingthreats$setSlowness(data.evolvingthreats$slows());
			traits.evolvingthreats$setTeleportsPlayer(data.evolvingthreats$teleportsPlayer());
		}
		if (LONG_LEVITATION_CHANCE > 0.0f && bullet.getRandom().nextFloat() < LONG_LEVITATION_CHANCE) {
			traits.evolvingthreats$setLongLevitation(true);
		}
	}

	private static void fireSecondShot(Shulker shulker) {
		if (shulker.level().getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		LivingEntity target = shulker.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		ShulkerBullet bullet = new ShulkerBullet(shulker.level(), shulker, target, shulker.getAttachFace().getAxis());
		mark(bullet);
		shulker.level().addFreshEntity(bullet);
		shulker.playSound(
				SoundEvents.SHULKER_SHOOT,
				2.0f,
				(shulker.getRandom().nextFloat() - shulker.getRandom().nextFloat()) * 0.2f + 1.0f);
	}

	private static void hopToNeighbor(Shulker shulker) {
		if (!(shulker instanceof ShulkerTeleportAccessor access)) {
			return;
		}
		Level level = shulker.level();
		BlockPos oldPos = shulker.blockPosition();
		List<BlockPos> options = neighbors(oldPos);
		shuffle(options, shulker.getRandom());
		for (BlockPos target : options) {
			if (target.getY() <= level.getMinY() || !level.isEmptyBlock(target)) {
				continue;
			}
			if (!level.getWorldBorder().isWithinBounds(target)) {
				continue;
			}
			if (!level.noCollision(shulker, new AABB(target).deflate(1.0E-6))) {
				continue;
			}
			Direction face = access.evolvingthreats$findAttachableSurface(target);
			if (face == null) {
				continue;
			}
			if (level.getBlockState(target.relative(face)).is(BlockTags.SHULKER_DOES_NOT_TELEPORT_TO)) {
				continue;
			}
			access.evolvingthreats$setAttachFace(face);
			shulker.playSound(SoundEvents.SHULKER_TELEPORT, 1.0f, 1.0f);
			shulker.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
			level.gameEvent(GameEvent.TELEPORT, oldPos, GameEvent.Context.of(shulker));
			int packed = BlockUtil.packDifferenceInPosition(
					oldPos, shulker.blockPosition(), VANILLA_TELEPORT_PACK_RADIUS, VANILLA_TELEPORT_PACK_RADIUS, VANILLA_TELEPORT_PACK_RADIUS);
			level.levelEvent(2016, oldPos, packed);
			return;
		}
	}

	private static void teleportPlayer(Player player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		int min = Math.max(1, TELEPORT_MIN_BLOCKS);
		int max = Math.max(min, TELEPORT_MAX_BLOCKS);
		RandomSource random = player.getRandom();
		int attempts = Math.max(1, TELEPORT_ATTEMPTS);
		for (int attempt = 0; attempt < attempts; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = min + random.nextDouble() * (max - min);
			double x = player.getX() + Math.cos(angle) * distance;
			double z = player.getZ() + Math.sin(angle) * distance;
			double[] heights = { player.getY(), player.getY() + 1.0, player.getY() - 1.0 };
			for (double y : heights) {
				if (!isSafeLanding(level, player, x, y, z)) {
					continue;
				}
				player.teleportTo(level, x, y, z, Set.of(), player.getYRot(), player.getXRot(), false);
				player.resetFallDistance();
				player.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0f, 1.0f);
				return;
			}
		}
	}

	private static boolean isSafeLanding(ServerLevel level, Player player, double x, double y, double z) {
		if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(x, y, z))) {
			return false;
		}
		AABB box = player.getDimensions(player.getPose()).makeBoundingBox(new Vec3(x, y, z));
		if (!level.noCollision(player, box) || level.containsAnyLiquid(box)) {
			return false;
		}
		BlockPos below = BlockPos.containing(x, y - 0.05, z);
		BlockState ground = level.getBlockState(below);
		return !ground.getCollisionShape(level, below).isEmpty();
	}

	private static List<BlockPos> neighbors(BlockPos origin) {
		int range = Math.max(1, NEIGHBOR_RANGE);
		List<BlockPos> options = new ArrayList<>();
		for (int dx = -range; dx <= range; dx++) {
			for (int dy = -range; dy <= range; dy++) {
				for (int dz = -range; dz <= range; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					options.add(origin.offset(dx, dy, dz));
				}
			}
		}
		return options;
	}

	private static void shuffle(List<BlockPos> options, RandomSource random) {
		for (int i = options.size() - 1; i > 0; i--) {
			int swap = random.nextInt(i + 1);
			BlockPos left = options.get(i);
			options.set(i, options.get(swap));
			options.set(swap, left);
		}
	}

	private static int hopIntervalTicks() {
		return Math.max(1, NEIGHBOR_HOP_SECONDS) * TICKS_PER_SECOND;
	}
}
