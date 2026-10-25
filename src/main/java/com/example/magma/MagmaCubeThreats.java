package com.example.magma;

import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Магмовый куб: больше здоровья и урона, поджог игрока, магма под ногами
 * и стадия перегрева — быстрее прыгает и бьёт гораздо сильнее.
 * Маленький куб (размер 1) по правилам игры ударом не бьёт, поэтому огонь и перегрев его не касаются.
 */
public final class MagmaCubeThreats {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — куб снова как в обычной игре: без огня, магмы и перегрева, со своими цифрами. */
	public static final boolean ENABLED = true;

	/**
	 * Во сколько раз больше здоровья, чем в обычной игре.
	 * 2 = вдвое. Большой куб: было 16 (8 сердец), станет 32 (16 сердец).
	 * Средний: было 4, станет 8. Маленький: было 1, станет 2.
	 * Свежий куб появляется с полной полоской. Уже живущий при загрузке чанка
	 * получает новый потолок, но текущие сердца сами не восполняются.
	 */
	public static final double HEALTH_MULTIPLIER = 2.0;

	/**
	 * Во сколько раз сильнее обычный удар (до перегрева).
	 * 1.5 = на половину больнее. Большой: было 6, станет 9. Средний: было 4, станет 6.
	 * 1 = урон как в игре. Маленький куб по-прежнему не бьёт.
	 */
	public static final double DAMAGE_MULTIPLIER = 1.5;

	/**
	 * Шанс (0.0–1.0), что прошедший удар подожжёт игрока.
	 * 1.0 = 100%, каждый удар. 0.5 = каждый второй. 0 = огня нет.
	 * Щит и короткое мигание после удара шанс не бросают.
	 */
	public static final float FIRE_CHANCE = 1.0f;

	/**
	 * Сколько секунд горит игрок. 4 = четыре секунды.
	 * Новый удар обновляет время. 0 = не поджигает.
	 */
	public static final int FIRE_SECONDS = 4;

	/** false — куб больше не ставит под собой магму. */
	public static final boolean PLACE_MAGMA = true;

	/**
	 * Шанс (0.0–1.0) поставить магму, когда подошла очередь.
	 * 0.45 = 45%. 0 = никогда. 1 = каждый раз, как пройдёт пауза.
	 */
	public static final float PLACE_CHANCE = 0.45f;

	/**
	 * Пауза между попытками поставить магму, секунды.
	 * 4 = не чаще раза в четыре секунды, и только если куб стоит на земле.
	 */
	public static final int PLACE_INTERVAL_SECONDS = 4;

	/**
	 * true — магма не ставится, если в мире выключено правило «мобы разрушают блоки».
	 * false — ставит даже при выключенном правиле.
	 */
	public static final boolean RESPECT_MOB_GRIEFING = true;

	/** false — стадия перегрева выключена. Здоровье, урон, огонь и магма остаются. */
	public static final boolean OVERHEAT_ENABLED = true;

	/**
	 * С этого размера куб может перегреться. 2 = средний и большой.
	 * 1 = ещё и маленький, но он ударом не бьёт, так что быстрее только прыгает.
	 */
	public static final int OVERHEAT_MIN_SIZE = 2;

	/**
	 * Сколько секунд куб должен преследовать цель, прежде чем бросить шанс перегрева.
	 * 6 = шесть секунд погони. Пока цели нет, время сбрасывается.
	 */
	public static final int OVERHEAT_WARMUP_SECONDS = 6;

	/**
	 * Шанс (0.0–1.0) войти в перегрев, когда прогрев набрался.
	 * 0.50 = 50%. Если не вышло, через прогрев бросает снова.
	 */
	public static final float OVERHEAT_CHANCE = 0.50f;

	/** Сколько секунд длится перегрев. 8 = восемь секунд. */
	public static final int OVERHEAT_SECONDS = 8;

	/**
	 * Пауза после перегрева, секунды. 16 = раньше чем через 16 секунд снова не начнёт.
	 * В эту паузу он обычный: прежний урон и прежняя скорость.
	 */
	public static final int OVERHEAT_COOLDOWN_SECONDS = 16;

	/**
	 * Во сколько раз быстрее бег во время перегрева.
	 * 1.7 = на 70% быстрее. Прыжки отдельно, см. {@link #OVERHEAT_JUMP_DIVISOR}.
	 */
	public static final double OVERHEAT_SPEED_MULTIPLIER = 1.7;

	/**
	 * На сколько делится пауза между прыжками в перегреве.
	 * 2 = прыгает вдвое чаще. Игра и так укорачивает паузу, когда куб уже дерётся.
	 */
	public static final int OVERHEAT_JUMP_DIVISOR = 2;

	/**
	 * Во сколько раз больнее удар именно в перегреве, сверх обычного усиления.
	 * 2 = вдвое больнее уже усиленного удара. Большой: 9 становится 18. Средний: 6 становится 12.
	 */
	public static final double OVERHEAT_DAMAGE_MULTIPLIER = 2.0;

	// ========================================================================

	private static final WeakHashMap<MagmaCube, State> STATES = new WeakHashMap<>();

	private MagmaCubeThreats() {
	}

	/** Здоровье и скорость после того, как игра выставила размер. */
	public static void onSize(MagmaCube cube, boolean fillHealth) {
		if (!ENABLED || HEALTH_MULTIPLIER <= 0.0) {
			return;
		}
		int size = cube.getSize();
		var maxHealth = cube.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(size * size * HEALTH_MULTIPLIER);
			if (fillHealth) {
				cube.setHealth(cube.getMaxHealth());
			}
		}
		applySpeed(cube);
	}

	/** Урон удара, который уже дошёл до расчёта. Маленький куб сюда не попадает. */
	public static float scaleHit(MagmaCube cube, float damage) {
		if (!ENABLED) {
			return damage;
		}
		double scaled = damage * DAMAGE_MULTIPLIER;
		if (isOverheated(cube)) {
			scaled *= OVERHEAT_DAMAGE_MULTIPLIER;
		}
		return (float) scaled;
	}

	/** Удар снял здоровье. Поджёг только игрока. */
	public static void onHit(MagmaCube cube, LivingEntity target) {
		if (!ENABLED || FIRE_SECONDS <= 0 || FIRE_CHANCE <= 0.0f || !(target instanceof Player)) {
			return;
		}
		if (cube.getRandom().nextFloat() >= FIRE_CHANCE) {
			return;
		}
		target.igniteForSeconds(FIRE_SECONDS);
	}

	/** Пауза между прыжками. Вне перегрева число игры не трогаем. */
	public static int jumpDelay(MagmaCube cube, int vanillaDelay) {
		if (!ENABLED || !OVERHEAT_ENABLED || OVERHEAT_JUMP_DIVISOR <= 1 || !isOverheated(cube)) {
			return vanillaDelay;
		}
		return Math.max(4, vanillaDelay / OVERHEAT_JUMP_DIVISOR);
	}

	/** Каждый тик на сервере: перегрев и магма под ногами. */
	public static void tick(MagmaCube cube) {
		if (!ENABLED || cube.level().isClientSide()) {
			return;
		}
		State state = STATES.computeIfAbsent(cube, ignored -> new State());
		tickOverheat(cube, state);
		tryPlaceMagma(cube, state);
	}

	private static void tickOverheat(MagmaCube cube, State state) {
		if (!OVERHEAT_ENABLED) {
			return;
		}
		if (state.overheatTicks > 0) {
			state.overheatTicks--;
			if (state.overheatTicks == 0) {
				state.cooldownTicks = OVERHEAT_COOLDOWN_SECONDS * 20;
				applySpeed(cube);
				return;
			}
			if (cube.level() instanceof ServerLevel level && cube.tickCount % 5 == 0) {
				level.sendParticles(ParticleTypes.LAVA, cube.getX(), cube.getY() + cube.getBbHeight() * 0.5, cube.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
				level.sendParticles(ParticleTypes.FLAME, cube.getX(), cube.getY() + cube.getBbHeight() * 0.4, cube.getZ(), 4, 0.35, 0.25, 0.35, 0.01);
			}
			return;
		}
		if (state.cooldownTicks > 0) {
			state.cooldownTicks--;
			return;
		}
		LivingEntity target = cube.getTarget();
		if (target == null || !target.isAlive() || cube.getSize() < OVERHEAT_MIN_SIZE) {
			state.chaseTicks = 0;
			return;
		}
		state.chaseTicks++;
		int warmup = OVERHEAT_WARMUP_SECONDS * 20;
		if (warmup <= 0 || state.chaseTicks < warmup) {
			return;
		}
		state.chaseTicks = 0;
		if (OVERHEAT_CHANCE <= 0.0f || cube.getRandom().nextFloat() >= OVERHEAT_CHANCE) {
			return;
		}
		state.overheatTicks = Math.max(1, OVERHEAT_SECONDS * 20);
		applySpeed(cube);
		if (cube.level() instanceof ServerLevel level) {
			level.playSound(null, cube.getX(), cube.getY(), cube.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.8f, 0.6f);
			level.sendParticles(ParticleTypes.LAVA, cube.getX(), cube.getY() + cube.getBbHeight() * 0.5, cube.getZ(), 12, 0.4, 0.3, 0.4, 0.0);
		}
	}

	private static void tryPlaceMagma(MagmaCube cube, State state) {
		if (!PLACE_MAGMA || PLACE_CHANCE <= 0.0f) {
			return;
		}
		if (state.placeCooldown > 0) {
			state.placeCooldown--;
			return;
		}
		if (!cube.onGround() || !(cube.level() instanceof ServerLevel level)) {
			return;
		}
		state.placeCooldown = Math.max(1, PLACE_INTERVAL_SECONDS * 20);
		if (RESPECT_MOB_GRIEFING && !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		if (cube.getRandom().nextFloat() >= PLACE_CHANCE) {
			return;
		}
		BlockPos pos = BlockPos.containing(cube.getX(), cube.getY() - 0.05, cube.getZ());
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState ground = level.getBlockState(pos);
		if (!canBecomeMagma(ground)) {
			return;
		}
		level.setBlockAndUpdate(pos, Blocks.MAGMA_BLOCK.defaultBlockState());
		level.playSound(null, pos, Blocks.MAGMA_BLOCK.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.HOSTILE, 0.6f, 0.8f);
	}

	/**
	 * Только обычная земля, которую не жалко.
	 * Сундуки, обсидиан, руды, дерево и уже стоящая магма не заменяются.
	 */
	private static boolean canBecomeMagma(BlockState state) {
		if (state.hasBlockEntity() || state.is(Blocks.MAGMA_BLOCK)) {
			return false;
		}
		return state.is(Blocks.NETHERRACK)
				|| state.is(Blocks.CRIMSON_NYLIUM)
				|| state.is(Blocks.WARPED_NYLIUM)
				|| state.is(Blocks.SOUL_SAND)
				|| state.is(Blocks.SOUL_SOIL)
				|| state.is(Blocks.BLACKSTONE)
				|| state.is(Blocks.BASALT)
				|| state.is(Blocks.SMOOTH_BASALT)
				|| state.is(Blocks.DIRT)
				|| state.is(Blocks.GRASS_BLOCK)
				|| state.is(Blocks.COARSE_DIRT)
				|| state.is(Blocks.SAND)
				|| state.is(Blocks.RED_SAND)
				|| state.is(Blocks.GRAVEL)
				|| state.is(Blocks.STONE)
				|| state.is(Blocks.DEEPSLATE)
				|| state.is(Blocks.SNOW_BLOCK);
	}

	private static void applySpeed(MagmaCube cube) {
		var speed = cube.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		double blocks = 0.2 + 0.1 * cube.getSize();
		if (isOverheated(cube)) {
			blocks *= OVERHEAT_SPEED_MULTIPLIER;
		}
		speed.setBaseValue(blocks);
	}

	private static boolean isOverheated(MagmaCube cube) {
		State state = STATES.get(cube);
		return state != null && state.overheatTicks > 0;
	}

	private static final class State {
		private int overheatTicks;
		private int cooldownTicks;
		private int chaseTicks;
		private int placeCooldown;
	}
}
