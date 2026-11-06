package com.example.magma;

import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import com.example.advancement.ModAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 Magma cube: more health and damage, arson player, magma underfoot
 * and the overheating stage - jumps faster and hits much harder.
 A small cube (size 1) does not hit with a blow according to the rules of the game, so fire and overheating do not touch it.
 */
public final class MagmaCubeThreats {

	// ===================== SETTINGS (change these numbers) =====================

	/*False - the cube again as in a normal game: without fire, magma and overheating, with its own numbers. */
	public static final boolean ENABLED = true;

	/**
	 How many times more health than in a normal game.
	 * 2 = double. Big cube: was 16 (8 hearts), will be 32 (16 hearts).
	 * Medium: was 4, will be 8. Small: was 1, will be 2.
	 A fresh cube appears with a full strip. Already alive at loading the chunk
	 Gets a new ceiling, but current hearts are not replenished.
	 */
	public static final double HEALTH_MULTIPLIER = 2.0;

	/**
	 How many times stronger is the usual shock (before overheating).
	 1.5 = half as painful. Big: was 6, will be 9. Medium: was 4, will be 6.
	 * 1 = game damage. The little cube still doesn't hit.
	 */
	public static final double DAMAGE_MULTIPLIER = 1.5;

	/**
	 * Chance (0.0–1.0) that a hit will set the player on fire.
	 * 1.0 = 100%, each stroke. 0.5 = every other. 0 = no fire.
	 Shield and short blinking after impact do not throw a chance.
	 */
	public static final float FIRE_CHANCE = 1.0f;

	/**
	 How many seconds does the player burn? 4 = four seconds.
	 A new strike updates time. 0 = doesn't burn.
	 */
	public static final int FIRE_SECONDS = 4;

	/*False: The cube no longer puts magma under it. */
	public static final boolean PLACE_MAGMA = true;

	/**
	 Chance (0.0-1.0) to put the magma when the turn comes.
	 * 0.45 = 45%. 0 = never. 1 = every time a pause passes.
	 */
	public static final float PLACE_CHANCE = 0.45f;

	/**
	 A pause between trying to deliver magma, seconds.
	 4 = no more than once every four seconds, and only if the cube is on the ground.
	 */
	public static final int PLACE_INTERVAL_SECONDS = 4;

	/**
	 True - magma is not placed if the rule "mobs break blocks" is turned off in the world.
	 * False - even when the rule is turned off.
	 */
	public static final boolean RESPECT_MOB_GRIEFING = true;

	/** False - the overheating stage is turned off. Health, damage, fire and magma remain. */
	public static final boolean OVERHEAT_ENABLED = true;

	/**
	 From this size, the cube can overheat. 2 = medium and large.
	 1 = also small, but he does not hit, so he only jumps faster.
	 */
	public static final int OVERHEAT_MIN_SIZE = 2;

	/**
	 How many seconds should the cube chase the target before throwing the chance of overheating?
	 6 = six seconds chase. While there is no goal, time is reset.
	 */
	public static final int OVERHEAT_WARMUP_SECONDS = 6;

	/**
	 * Chance (0.0-1.0) to get into overheating when the warming has gained.
	 * 0.50 = 50%. If it doesn't work out, he throws it again.
	 */
	public static final float OVERHEAT_CHANCE = 0.50f;

	/** How many seconds does the overheating last? 8 = eight seconds. */
	public static final int OVERHEAT_SECONDS = 8;

	/**
	 Pause after overheating, seconds. 16 = does not start again after 16 seconds.
	 During this pause, it is normal: the previous damage and the previous speed.
	 */
	public static final int OVERHEAT_COOLDOWN_SECONDS = 16;

	/**
	 How much faster is running during overheating?
	 1.7 = 70% faster. Jump separately, see {@link #OVERHEAT JUMP DIVISOR}.
	 */
	public static final double OVERHEAT_SPEED_MULTIPLIER = 1.7;

	/**
	 How much is the pause between jumps in overheating divided.
	 2 = jumps twice as often. The game already shortens the pause when the cube is already fighting.
	 */
	public static final int OVERHEAT_JUMP_DIVISOR = 2;

	/**
	 How many times more painful is the blow in overheating, in excess of the usual amplification.
	 2 = twice as painful as an already intensified stroke. Big: 9 becomes 18. Mean: 6 becomes 12.
	 */
	public static final double OVERHEAT_DAMAGE_MULTIPLIER = 2.0;

	// ========================================================================

	private static final WeakHashMap<MagmaCube, State> STATES = new WeakHashMap<>();

	private MagmaCubeThreats() {
	}

	/*Health and speed after the game has set the size. */
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

	/** Impact damage that has already reached the calculation. The little cube doesn't get in here. */
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

	/*The impact took health away. Only set fire to the player. */
	public static void onHit(MagmaCube cube, LivingEntity target) {
		if (!ENABLED || FIRE_SECONDS <= 0 || FIRE_CHANCE <= 0.0f || !(target instanceof Player)) {
			return;
		}
		if (cube.getRandom().nextFloat() >= FIRE_CHANCE) {
			return;
		}
		target.igniteForSeconds(FIRE_SECONDS);
	}

	/*Pause between jumps. Outside of overheating, we do not touch the number of games. */
	public static int jumpDelay(MagmaCube cube, int vanillaDelay) {
		if (!ENABLED || !OVERHEAT_ENABLED || OVERHEAT_JUMP_DIVISOR <= 1 || !isOverheated(cube)) {
			return vanillaDelay;
		}
		return Math.max(4, vanillaDelay / OVERHEAT_JUMP_DIVISOR);
	}

	/*Every tick on the server: overheating and magma underfoot. */
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
		if (target instanceof ServerPlayer serverPlayer) {
			ModAdvancements.onOverheated(serverPlayer);
		}
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
	 Only ordinary land, which is not sorry.
	 Chests, obsidian, ores, wood and already standing magma are not replaced.
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
