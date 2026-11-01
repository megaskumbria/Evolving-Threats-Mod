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
 Shalkers shoot not only conventional levitation and in battle transplanted to a neighboring block.
 Rare properties are solved once upon appearance. Long levitation - separately for each projectile.
 In appearance, shalkers are the same: who is rare, you can see only by hitting.
 */
public final class ShulkerThreats {

	// ===================== SETTINGS (change these numbers) =====================

	/*False - shalkers as in a normal game: one projectile, levitation 10 seconds, teleport only when wounded. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that the shalker when it appears shoots slowdown and weakness.
	 * 0.12 = 12%, approximately one in eight. The levitation remains.
	 * 0 = no deceleration or weakness. 1 = both effects of each shalker.
	 */
	public static final float SLOWNESS_CHANCE = 0.12f;

	/**
	 * How many seconds does the deceleration last? 20 = twenty seconds.
	 Normal levitation lasts 10 seconds, so there is still a slowdown after landing.
	 * 0 = no effect.
	 */
	public static final int SLOWNESS_SECONDS = 20;

	/**
	 Deceleration force. 0 = Slowdown I, the step is about 15% shorter.
	 1 = Deceleration II, approximately 30% shorter.
	 */
	public static final int SLOWNESS_AMPLIFIER = 0;

	/**
	 How many seconds of weakness hangs the same rare shalker as the slowdown.
	 * 20 = twenty seconds. The levitation remains.
	 0 = no weakness, the deceleration remains.
	 */
	public static final int WEAKNESS_SECONDS = 20;

	/**
	 The power of weakness. 0 = Weakness I: The player's close beats are weaker by 4 health (2 hearts).
	 1 = Weakness II, the sword hardly strikes.
	 */
	public static final int WEAKNESS_AMPLIFIER = 0;

	/**
	 * Chance (0.0-1.0) that the shalker fires two shells.
	 * 0.30 = 30%, approximately one in three. The second one goes to the same target.
	 * 0 = always one projectile. 1 = two rounds each.
	 */
	public static final float DOUBLE_SHOT_CHANCE = 0.30f;

	/**
	 A pause between two shells, in ticks. 10 = half a second. 20 = one second.
	 0 = no second shell, even if the shalker can shoot twice.
	 */
	public static final int SECOND_SHOT_TICKS = 10;

	/**
	 How often in battle the shalker is transferred to the neighboring block, in seconds.
	 8 = once every eight seconds as long as there is a target. In a normal game, he jumps far.
	 Only when he was severely injured, and not by the timer.
	 0 = there is no such transfer, only the usual jump of the game remains.
	 */
	public static final int NEIGHBOR_HOP_SECONDS = 8;

	/**
	 How many blocks to the side it is transplanted. 1 = only neighboring cells,
	 * including the diagonal. 2 = already two cells.
	 */
	public static final int NEIGHBOR_RANGE = 1;

	/**
	 Chance (0.0-1.0) that a particular projectile will produce long levitation.
	 0.25 = 25%, approximately one in four shells. Throw again for each shot.
	 * 0 = always the usual 10 seconds. 1 = each projectile is long.
	 */
	public static final float LONG_LEVITATION_CHANCE = 0.25f;

	/**
	 How many seconds does a long levitation last? 18 = eighteen seconds.
	 * The usual projectile of the game holds 10. Milk takes effect immediately.
	 */
	public static final int LONG_LEVITATION_SECONDS = 18;

	/**
	 * Chance (0.0–1.0) that the shalker teleports the player with a hit.
	 * 0.08 = 8%, approximately one in twelve. Other mobs don't teleport.
	 * 0 = no teleport. 1 = teleports each shalker.
	 */
	public static final float TELEPORT_CHANCE = 0.08f;

	/**
	 Minimum range of the player's teleport, in blocks. 3 = three blocks to the side.
	 The player stays at the same height or a block above/below if there is a floor.
	 */
	public static final int TELEPORT_MIN_BLOCKS = 3;

	/*Maximum range of the player's teleport, in blocks. 5 = five blocks. */
	public static final int TELEPORT_MAX_BLOCKS = 5;

	/*How many times to look for a free seat? Not found - the player remains, the effect of the projectile is still there. */
	public static final int TELEPORT_ATTEMPTS = 8;

	// ========================================================================

	/*Normal levitation of a shalker projectile: 10 seconds. */
	private static final int VANILLA_LEVITATION_TICKS = 200;

	/** 20 ticks in one second. */
	private static final int TICKS_PER_SECOND = 20;

	/*The radius with which the game packs a short shalker teleport for the picture. No need to change. */
	private static final int VANILLA_TELEPORT_PACK_RADIUS = 8;

	private ShulkerThreats() {
	}

	/** Once upon appearance. Re-calling and downloading from saving the chance does not recast. */
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

	/*Each tick shalker on the server: a second projectile and transfer to a neighboring unit. */
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
	 A conventional shalker shot has already created a projectile. Marks it and, if the shalker can,
	 * Puts the second projectile in line.
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
	 The projectile has already hit and the game is about to hang levitation.
	 There is levitation length, slowdown, weakness and teleport of the player.
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
		if (traits.evolvingthreats$slowness() && WEAKNESS_SECONDS > 0) {
			target.addEffect(new MobEffectInstance(
					MobEffects.WEAKNESS,
					WEAKNESS_SECONDS * TICKS_PER_SECOND,
					Math.max(0, WEAKNESS_AMPLIFIER),
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
