package com.example.enderman;

import com.example.advancement.ModAdvancements;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 An evil enderman can switch places with the player.
 If the player is in the water, the exchange is not made: the Enderman will drag him to land.
 Where there is air for growth and earth underfoot. No blocks inside.
 The usual teleport of the enderman (from the impact, from the look, from the water) remains.
 */
public final class EndermanSwap {

	// ===================== SETTINGS (change these numbers) =====================

	/*False – there is no exchanging or pulling out of the water. The Enderman's usual teleporter remains. */
	public static final boolean ENABLED = true;

	/**
	 How many seconds after the beginning of anger to wait before the first attempt?
	 8 = eight seconds of fighting, then it can change.
	 */
	public static final int FIRST_WAIT_SECONDS = 8;

	/**
	 Pause after a successful exchange or pull out of the water, seconds.
	 * 14 = next time not earlier than 14 seconds.
	 */
	public static final int COOLDOWN_SECONDS = 14;

	/**
	 Pause if the attempt did not work: the chance did not work or there is no dry place.
	 4 = four seconds, then try again.
	 */
	public static final int RETRY_SECONDS = 4;

	/**
	 Chance (0.0-1.0) to work when the pause is over.
	 * 0.45 = 45%. 1 = every time. 0 = never.
	 */
	public static final float SWAP_CHANCE = 0.45f;

	/**
	 * Beyond that (blocks) does not touch the player.
	 * 24 = about 24 blocks. Then the enderman just walks or teleports as usual.
	 */
	public static final double MAX_RANGE_BLOCKS = 24.0;

	/**
	 Closer than this (blocks) from the water does not pull: too close to the same shore.
	 8 = a minimum of eight blocks from where the player was in the water.
	 */
	public static final double DRY_MIN_BLOCKS = 8.0;

	/**
	 Next to this (blocks) dry place is not looking.
	 24 = if there is no sushi in this circle, the player remains in the water. It's better than sticking it in a rock.
	 */
	public static final double DRY_MAX_BLOCKS = 24.0;

	/**
	 How many blocks around the landing point should there be no water?
	 3 = water no closer than three blocks, so as not to put on the edge.
	 0 = enough for the player not to stand in the water.
	 */
	public static final int DRY_PADDING = 3;

	/*How many blocks up from the player look looking for the surface above the water. 32 grabs a deep lake. */
	public static final int SEARCH_UP = 32;

	/*How many blocks down from the player are there to watch? 6 = just below the feet, not into a cave under the ocean. */
	public static final int SEARCH_DOWN = 6;

	/*How many different points to try when a player is in the water. 24 = twenty-four attempts at a time. */
	public static final int RESCUE_TRIES = 24;

	// ========================================================================

	private static final WeakHashMap<Enderman, State> STATES = new WeakHashMap<>();

	private EndermanSwap() {
	}

	/*Every Enderman server tick while he thinks. */
	public static void tick(Enderman enderman, ServerLevel level) {
		if (!ENABLED) {
			return;
		}
		State state = STATES.computeIfAbsent(enderman, ignored -> new State());
		if (!(enderman.getTarget() instanceof Player player) || !player.isAlive() || player.isSpectator() || !enderman.isAlive()) {
			state.fighting = false;
			return;
		}
		if (!state.fighting) {
			state.fighting = true;
			state.cooldown = FIRST_WAIT_SECONDS * 20;
		}
		if (state.cooldown > 0) {
			state.cooldown--;
			return;
		}
		if (enderman.distanceTo(player) > MAX_RANGE_BLOCKS) {
			state.cooldown = RETRY_SECONDS * 20;
			return;
		}
		if (SWAP_CHANCE <= 0.0f || enderman.getRandom().nextFloat() >= SWAP_CHANCE) {
			state.cooldown = RETRY_SECONDS * 20;
			return;
		}
		boolean moved = inWater(player) ? pullOutOfWater(level, enderman, player) : swap(level, enderman, player);
		state.cooldown = (moved ? COOLDOWN_SECONDS : RETRY_SECONDS) * 20;
	}

	private static boolean swap(ServerLevel level, Enderman enderman, Player player) {
		double endermanX = enderman.getX();
		double endermanY = enderman.getY();
		double endermanZ = enderman.getZ();
		double playerX = player.getX();
		double playerY = player.getY();
		double playerZ = player.getZ();
		if (!canStand(level, player, endermanX, endermanY, endermanZ)) {
			return false;
		}
		if (!canStand(level, enderman, playerX, playerY, playerZ)) {
			return false;
		}
		if (player.isPassenger()) {
			player.stopRiding();
		}
		float yRot = player.getYRot();
		float xRot = player.getXRot();
		if (!player.teleportTo(level, endermanX, endermanY, endermanZ, Set.of(), yRot, xRot, false)) {
			return false;
		}
		settle(player);
		if (!enderman.teleport(playerX, playerY, playerZ)) {
			player.teleportTo(level, playerX, playerY, playerZ, Set.of(), yRot, xRot, false);
			settle(player);
			return false;
		}
		playTeleport(level, player);
		if (player instanceof ServerPlayer serverPlayer) {
			ModAdvancements.onSwapped(serverPlayer);
		}
		return true;
	}

	/*Player in the water: looking for land away, enderman not put in the water. */
	private static boolean pullOutOfWater(ServerLevel level, Enderman enderman, Player player) {
		if (DRY_MAX_BLOCKS < DRY_MIN_BLOCKS) {
			return false;
		}
		var random = enderman.getRandom();
		int top = Mth.floor(player.getY()) + SEARCH_UP;
		int bottom = Math.max(level.getMinY() + 1, Mth.floor(player.getY()) - SEARCH_DOWN);
		for (int attempt = 0; attempt < RESCUE_TRIES; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = DRY_MIN_BLOCKS + random.nextDouble() * (DRY_MAX_BLOCKS - DRY_MIN_BLOCKS);
			double x = player.getX() + Math.cos(angle) * distance;
			double z = player.getZ() + Math.sin(angle) * distance;
			for (int y = top; y >= bottom; y--) {
				double feetX = Mth.floor(x) + 0.5;
				double feetZ = Mth.floor(z) + 0.5;
				if (!canStand(level, player, feetX, y, feetZ) || waterNear(level, BlockPos.containing(feetX, y, feetZ))) {
					continue;
				}
				if (player.isPassenger()) {
					player.stopRiding();
				}
				float yRot = player.getYRot();
				float xRot = player.getXRot();
				if (!player.teleportTo(level, feetX, y, feetZ, Set.of(), yRot, xRot, false)) {
					continue;
				}
				settle(player);
				playTeleport(level, enderman);
				playTeleport(level, player);
				return true;
			}
		}
		return false;
	}

	/*Air to the full height, support under the feet, not water and not lava. A foreign body does not interfere: we occupy its place. */
	private static boolean canStand(ServerLevel level, Entity body, double x, double y, double z) {
		BlockPos feet = BlockPos.containing(x, y, z);
		if (!level.isLoaded(feet) || !level.getWorldBorder().isWithinBounds(feet)) {
			return false;
		}
		AABB box = body.getDimensions(body.getPose()).makeBoundingBox(new Vec3(x, y, z));
		if (!level.noBlockCollision(body, box) || level.containsAnyLiquid(box)) {
			return false;
		}
		BlockPos below = BlockPos.containing(x, y - 0.05, z);
		BlockState ground = level.getBlockState(below);
		if (ground.getFluidState().is(FluidTags.WATER) || ground.getFluidState().is(FluidTags.LAVA)) {
			return false;
		}
		return !ground.getCollisionShape(level, below).isEmpty();
	}

	private static boolean waterNear(ServerLevel level, BlockPos feet) {
		int pad = Math.max(0, DRY_PADDING);
		for (int dx = -pad; dx <= pad; dx++) {
			for (int dz = -pad; dz <= pad; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					if (level.getFluidState(feet.offset(dx, dy, dz)).is(FluidTags.WATER)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean inWater(Player player) {
		if (player.isInWater() || player.isUnderWater()) {
			return true;
		}
		BlockPos feet = player.blockPosition();
		var level = player.level();
		return level.getFluidState(feet).is(FluidTags.WATER) || level.getFluidState(feet.below()).is(FluidTags.WATER);
	}

	private static void settle(Player player) {
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		player.needsSync = true;
	}

	private static void playTeleport(ServerLevel level, Entity entity) {
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f);
		level.broadcastEntityEvent(entity, (byte) 46);
	}

	private static final class State {
		private int cooldown;
		private boolean fighting;
	}
}
