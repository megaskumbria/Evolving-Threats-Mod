package com.example.enderman;

import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 * Злой эндермен может поменяться с игроком местами.
 * Если игрок в воде, обмен не делается: эндермен утащит его на сушу,
 * где есть воздух на рост и земля под ногами. Внутрь блоков не ставит.
 * Обычный телепорт эндермена (от удара, от взгляда, от воды) остаётся.
 */
public final class EndermanSwap {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — обмена и вытаскивания из воды нет. Обычный телепорт эндермена остаётся. */
	public static final boolean ENABLED = true;

	/**
	 * Сколько секунд после начала злости ждать перед первой попыткой.
	 * 8 = восемь секунд боя, потом уже может поменяться.
	 */
	public static final int FIRST_WAIT_SECONDS = 8;

	/**
	 * Пауза после удачного обмена или вытаскивания из воды, секунды.
	 * 14 = следующий раз не раньше чем через 14 секунд.
	 */
	public static final int COOLDOWN_SECONDS = 14;

	/**
	 * Пауза, если попытка не вышла: шанс не сработал или сухого места нет.
	 * 4 = четыре секунды, потом пробует снова.
	 */
	public static final int RETRY_SECONDS = 4;

	/**
	 * Шанс (0.0–1.0) сработать, когда пауза кончилась.
	 * 0.45 = 45%. 1 = каждый раз. 0 = никогда.
	 */
	public static final float SWAP_CHANCE = 0.45f;

	/**
	 * Дальше этого (блоки) не трогает игрока.
	 * 24 = около 24 блоков. Дальше эндермен просто идёт или телепортируется как обычно.
	 */
	public static final double MAX_RANGE_BLOCKS = 24.0;

	/**
	 * Ближе этого (блоки) из воды не вытаскивает: слишком рядом с тем же берегом.
	 * 8 = минимум восемь блоков от места, где игрок был в воде.
	 */
	public static final double DRY_MIN_BLOCKS = 8.0;

	/**
	 * Дальше этого (блоки) сухое место не ищет.
	 * 24 = если суши нет в этом круге, игрок остаётся в воде. Лучше так, чем засунуть в камень.
	 */
	public static final double DRY_MAX_BLOCKS = 24.0;

	/**
	 * Сколько блоков вокруг точки посадки не должно быть воды.
	 * 3 = вода не ближе трёх блоков, чтобы не поставить на самую кромку.
	 * 0 = достаточно, чтобы сам игрок не стоял в воде.
	 */
	public static final int DRY_PADDING = 3;

	/** Сколько блоков вверх от игрока смотреть, ища поверхность над водой. 32 хватает глубокого озера. */
	public static final int SEARCH_UP = 32;

	/** Сколько блоков вниз от игрока ещё смотреть. 6 = чуть ниже ног, не в пещеру под океаном. */
	public static final int SEARCH_DOWN = 6;

	/** Сколько разных точек пробовать, когда игрок в воде. 24 = двадцать четыре попытки за один раз. */
	public static final int RESCUE_TRIES = 24;

	// ========================================================================

	private static final WeakHashMap<Enderman, State> STATES = new WeakHashMap<>();

	private EndermanSwap() {
	}

	/** Каждый серверный тик эндермена, пока он думает. */
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
		return true;
	}

	/** Игрок в воде: ищем сушу подальше, эндермена в воду не ставим. */
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

	/** Воздух на весь рост, опора под ногами, не вода и не лава. Чужое тело не мешает: его место как раз и занимаем. */
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
