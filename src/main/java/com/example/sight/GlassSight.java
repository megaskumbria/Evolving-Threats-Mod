package com.example.sight;

import com.example.grudge.PlayerGrudge;
import com.example.whiteeyes.WhiteEyes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Any ordinary zombie, skeleton, creeper, or spider can notice the player through glass.
 * It is not a trait rolled at spawn. Every {@link #ROLL_EVERY_TICKS} that mob rolls 1–100.
 * A roll of 1 through {@link #NOTICE_ON_OR_BELOW} means he notices, if glass is all that blocks the view.
 * After that he keeps the player while they stay in his normal sight and the look is still only glass or air.
 * A stone wall, leaves, and water still hide the player. Chance is FIXED: no day and no moon.
 */
public final class GlassSight {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — nobody notices through glass, and a mob who already did forgets. */
	public static final boolean ENABLED = true;

	/**
	 * How often each mob rolls, in ticks. 20 ticks = 1 second. 600 = every 30 seconds.
	 * Lower = he tries more often, so a house with windows is riskier.
	 * Higher = a long quiet stretch between tries.
	 * Each mob rolls on his own beat, so a crowd does not all look on the same tick.
	 */
	public static final int ROLL_EVERY_TICKS = 30 * 20;

	/**
	 * The roll is 1 through this many. 100 = a percent roll.
	 */
	public static final int ROLL_OUT_OF = 100;

	/**
	 * He notices when the roll is this or lower. 5 = the numbers 1, 2, 3, 4, 5.
	 * That is 5% of rolls, about one in twenty. 10 = 10%. 100 = he always notices on the half-minute.
	 * 0 = he never does.
	 */
	public static final int NOTICE_ON_OR_BELOW = 5;

	/**
	 * How many glass blocks one look may pass. 8 covers a double window and a glass corridor.
	 * A solid block anywhere along the look still hides the player.
	 */
	public static final int MAX_GLASS_BLOCKS = 8;

	// ========================================================================

	/** Mob id → the player he has noticed through glass. Forgotten when he gives up. */
	private static final Map<UUID, UUID> NOTICED = new HashMap<>();

	private GlassSight() {
	}

	/** Called every server AI step. Most steps do nothing. */
	public static void tick(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		if (!ENABLED || !isKind(mob) || !mob.isAlive()) {
			NOTICED.remove(mob.getUUID());
			return;
		}
		UUID noticedId = NOTICED.get(mob.getUUID());
		if (noticedId != null) {
			keepOrForget(mob, level, noticedId);
			return;
		}
		if (NOTICE_ON_OR_BELOW <= 0 || ROLL_EVERY_TICKS <= 0 || ROLL_OUT_OF <= 0) {
			return;
		}
		long salt = mob.getUUID().getLeastSignificantBits() & Long.MAX_VALUE;
		if (Math.floorMod(level.getGameTime() + salt, ROLL_EVERY_TICKS) != 0) {
			return;
		}
		ServerPlayer player = nearestBehindGlass(mob, level);
		if (player == null) {
			return;
		}
		int roll = mob.getRandom().nextInt(ROLL_OUT_OF) + 1;
		if (roll > NOTICE_ON_OR_BELOW) {
			return;
		}
		NOTICED.put(mob.getUUID(), player.getUUID());
		mob.setTarget(player);
		if (mob instanceof Zombie zombie) {
			PlayerGrudge.rememberPlayer(zombie, player);
		}
	}

	/** True after a lucky roll, until he forgets that player. Line of sight uses this. */
	public static boolean hasNoticed(Mob mob, Entity target) {
		if (!ENABLED || !(target instanceof ServerPlayer player)) {
			return false;
		}
		return player.getUUID().equals(NOTICED.get(mob.getUUID()));
	}

	private static void keepOrForget(Mob mob, ServerLevel level, UUID noticedId) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(noticedId);
		if (!stillInSight(mob, player)) {
			NOTICED.remove(mob.getUUID());
			return;
		}
		if (mob.getTarget() != player) {
			mob.setTarget(player);
		}
	}

	/** Same level, alive, not creative, inside follow range, and only glass or air in the way. */
	private static boolean stillInSight(Mob mob, ServerPlayer player) {
		if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) {
			return false;
		}
		if (player.level() != mob.level()) {
			return false;
		}
		double range = followRange(mob);
		if (mob.distanceToSqr(player) > range * range) {
			return false;
		}
		return sees(mob, player);
	}

	/**
	 * Nearest player inside follow range who is hidden only by glass.
	 * An open view does not roll: vanilla already makes the mob notice.
	 */
	private static ServerPlayer nearestBehindGlass(Mob mob, ServerLevel level) {
		double range = followRange(mob);
		double best = range * range;
		ServerPlayer nearest = null;
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
				continue;
			}
			double distance = mob.distanceToSqr(player);
			if (distance > best) {
				continue;
			}
			if (mob.hasLineOfSight(player) || !sees(mob, player)) {
				continue;
			}
			best = distance;
			nearest = player;
		}
		return nearest;
	}

	private static double followRange(Mob mob) {
		var follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow == null) {
			return 16.0;
		}
		return Math.max(0.0, follow.getValue());
	}

	/** Zombies (not piglins, not White Eyes), overworld skeletons, creepers, spiders. */
	public static boolean isKind(Mob mob) {
		if (mob instanceof Zombie zombie) {
			return !(zombie instanceof ZombifiedPiglin) && !(zombie instanceof WhiteEyes);
		}
		if (mob instanceof AbstractSkeleton skeleton) {
			return !(skeleton instanceof WitherSkeleton);
		}
		return mob instanceof Creeper || mob instanceof Spider;
	}

	/**
	 * Can this mob see that entity, treating glass as air?
	 * Same end points as vanilla: the mob's eyes to the target's eyes, no farther than 128 blocks.
	 */
	public static boolean sees(Mob mob, Entity target) {
		if (target.level() != mob.level()) {
			return false;
		}
		Vec3 to = new Vec3(target.getX(), target.getEyeY(), target.getZ());
		Vec3 from = new Vec3(mob.getX(), mob.getEyeY(), mob.getZ());
		if (from.distanceToSqr(to) > 128.0 * 128.0) {
			return false;
		}
		Vec3 cursor = from;
		double previousLeft = Double.MAX_VALUE;
		for (int i = 0; i <= MAX_GLASS_BLOCKS; i++) {
			BlockHitResult hit = mob.level().clip(new ClipContext(
					cursor, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
			if (hit.getType() == HitResult.Type.MISS) {
				return true;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = mob.level().getBlockState(pos);
			if (!isGlass(state)) {
				return false;
			}
			Vec3 next = pastGlass(pos, hit.getLocation(), to);
			double left = next.distanceToSqr(to);
			if (left >= previousLeft - 1.0e-6) {
				return false;
			}
			previousLeft = left;
			if (left < 1.0e-4) {
				return true;
			}
			cursor = next;
		}
		return false;
	}

	/** Glass, stained glass, tinted glass, and their panes. The id contains "glass". */
	public static boolean isGlass(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("glass");
	}

	/** A short step past this glass so the next look does not hit the same block. */
	private static Vec3 pastGlass(BlockPos pos, Vec3 hit, Vec3 to) {
		double dx = to.x - hit.x;
		double dy = to.y - hit.y;
		double dz = to.z - hit.z;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0e-6) {
			return to;
		}
		double tx = dx > 0 ? (pos.getX() + 1.0 - hit.x) / dx : dx < 0 ? (pos.getX() - hit.x) / dx : Double.POSITIVE_INFINITY;
		double ty = dy > 0 ? (pos.getY() + 1.0 - hit.y) / dy : dy < 0 ? (pos.getY() - hit.y) / dy : Double.POSITIVE_INFINITY;
		double tz = dz > 0 ? (pos.getZ() + 1.0 - hit.z) / dz : dz < 0 ? (pos.getZ() - hit.z) / dz : Double.POSITIVE_INFINITY;
		double t = Math.min(tx, Math.min(ty, tz));
		double dist = (t > 0.0 && t != Double.POSITIVE_INFINITY ? t * len : 0.0) + 0.05;
		if (dist >= len) {
			return to;
		}
		double scale = dist / len;
		return new Vec3(hit.x + dx * scale, hit.y + dy * scale, hit.z + dz * scale);
	}
}
