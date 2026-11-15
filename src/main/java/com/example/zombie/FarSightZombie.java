package com.example.zombie;

import com.example.grudge.PlayerGrudge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Any ordinary zombie can notice the player from much farther than the usual 35 blocks.
 * It is not a trait rolled at spawn. Every {@link #ROLL_EVERY_TICKS} he rolls 1–100.
 * A roll of 1 through {@link #NOTICE_ON_OR_BELOW} means he notices, if the view is clear
 * and the player is inside {@link #SEE_BLOCKS} but outside his normal sight.
 * A wall still blocks that first look. After it, {@link PlayerGrudge} keeps him walking
 * even behind walls, out to {@link #SEE_BLOCKS}, until the player dies, leaves that
 * distance, or changes dimension. The 5-minute memory limit does not apply during this hunt.
 * Chance is FIXED: it does not grow with the day or the moon.
 */
public final class FarSightZombie {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — nobody notices from the long distance, and a zombie who already did forgets. */
	public static final boolean ENABLED = true;

	/**
	 * How often each zombie rolls, in ticks. 20 ticks = 1 second. 600 = every 30 seconds.
	 * Same beat as glass sight. Lower = he tries more often. Higher = a longer quiet stretch.
	 * Each zombie rolls on his own beat, so a crowd does not all look on the same tick.
	 */
	public static final int ROLL_EVERY_TICKS = 30 * 20;

	/** The roll is 1 through this many. 100 = a percent roll. */
	public static final int ROLL_OUT_OF = 100;

	/**
	 * He notices when the roll is this or lower. 5 = the numbers 1, 2, 3, 4, 5.
	 * That is 5% of rolls, about one in twenty. 10 = 10%. 100 = he always notices on the half-minute.
	 * 0 = he never does.
	 */
	public static final int NOTICE_ON_OR_BELOW = 5;

	/**
	 * How far the lucky look reaches, and how far he still chases after that.
	 * 112 is a bit past 100, the same as the Rotten Overseer. 64 is an enderman. 35 is a normal zombie.
	 * Farther than this, he forgets the player.
	 */
	public static final double SEE_BLOCKS = 112.0;

	/**
	 * Vanilla zombie sight. Used only to put back a zombie who used to be born far-sighted,
	 * and as the near edge of the roll: inside this distance he already notices without a roll.
	 */
	public static final double NORMAL_SIGHT = 35.0;

	// ========================================================================

	/** Zombie id → the player he noticed from far away. */
	private static final Map<UUID, UUID> NOTICED = new HashMap<>();

	/** Follow range to restore when the far hunt ends. */
	private static final Map<UUID, Double> PREVIOUS_RANGE = new HashMap<>();

	private FarSightZombie() {
	}

	/** Called every server AI step for a zombie. Most steps do nothing. */
	public static void tick(Zombie zombie) {
		if (!(zombie.level() instanceof ServerLevel level)) {
			return;
		}
		if (!ENABLED || !PlayerGrudge.isEligible(zombie) || !zombie.isAlive()) {
			forget(zombie);
			return;
		}
		UUID noticedId = NOTICED.get(zombie.getUUID());
		if (noticedId != null) {
			keepOrForget(zombie, level, noticedId);
			return;
		}
		undoLeftoverRange(zombie);
		if (NOTICE_ON_OR_BELOW <= 0 || ROLL_EVERY_TICKS <= 0 || ROLL_OUT_OF <= 0) {
			return;
		}
		long salt = zombie.getUUID().getMostSignificantBits() & Long.MAX_VALUE;
		if (Math.floorMod(level.getGameTime() + salt, ROLL_EVERY_TICKS) != 0) {
			return;
		}
		ServerPlayer player = nearestInTheFarBand(zombie, level);
		if (player == null) {
			return;
		}
		int roll = zombie.getRandom().nextInt(ROLL_OUT_OF) + 1;
		if (roll > NOTICE_ON_OR_BELOW) {
			return;
		}
		raiseRange(zombie);
		NOTICED.put(zombie.getUUID(), player.getUUID());
		zombie.setTarget(player);
		PlayerGrudge.rememberPlayer(zombie, player);
	}

	/**
	 * True while a far hunt is going on. Memory then reaches {@link #SEE_BLOCKS}
	 * and the 5-minute limit does not apply. A drowned or an overseer is never in the map.
	 */
	public static boolean isMarked(Zombie zombie) {
		return ENABLED && NOTICED.containsKey(zombie.getUUID());
	}

	private static void keepOrForget(Zombie zombie, ServerLevel level, UUID noticedId) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(noticedId);
		if (!stillChasing(zombie, player)) {
			forget(zombie);
			return;
		}
		if (zombie.getTarget() != player) {
			zombie.setTarget(player);
		}
	}

	/** Same level, alive, not creative, and still inside the long distance. Walls do not matter anymore. */
	private static boolean stillChasing(Zombie zombie, ServerPlayer player) {
		if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) {
			return false;
		}
		if (player.level() != zombie.level()) {
			return false;
		}
		return zombie.distanceToSqr(player) <= SEE_BLOCKS * SEE_BLOCKS;
	}

	/**
	 * Nearest player outside normal sight and inside {@link #SEE_BLOCKS}, with a clear view.
	 * Closer than normal sight does not roll: he already notices the usual way.
	 */
	private static ServerPlayer nearestInTheFarBand(Zombie zombie, ServerLevel level) {
		double near = followRange(zombie);
		double nearSq = near * near;
		double farSq = SEE_BLOCKS * SEE_BLOCKS;
		double best = farSq;
		ServerPlayer nearest = null;
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
				continue;
			}
			double distance = zombie.distanceToSqr(player);
			if (distance <= nearSq || distance > best) {
				continue;
			}
			if (!zombie.hasLineOfSight(player)) {
				continue;
			}
			best = distance;
			nearest = player;
		}
		return nearest;
	}

	private static void raiseRange(Zombie zombie) {
		var follow = zombie.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow == null || follow.getBaseValue() >= SEE_BLOCKS) {
			return;
		}
		PREVIOUS_RANGE.put(zombie.getUUID(), follow.getBaseValue());
		follow.setBaseValue(SEE_BLOCKS);
	}

	/** A zombie saved while the old "born far-sighted" rule was on still has range 112. Put 35 back. */
	private static void undoLeftoverRange(Zombie zombie) {
		var follow = zombie.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null && Math.abs(follow.getBaseValue() - SEE_BLOCKS) < 0.01) {
			follow.setBaseValue(NORMAL_SIGHT);
		}
	}

	private static void forget(Zombie zombie) {
		UUID id = zombie.getUUID();
		boolean wasHunting = NOTICED.remove(id) != null;
		Double previous = PREVIOUS_RANGE.remove(id);
		var follow = zombie.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null && previous != null) {
			follow.setBaseValue(previous);
		} else if (follow != null && Math.abs(follow.getBaseValue() - SEE_BLOCKS) < 0.01) {
			follow.setBaseValue(NORMAL_SIGHT);
		}
		if (wasHunting) {
			PlayerGrudge.clear(zombie);
			zombie.setTarget(null);
		}
	}

	private static double followRange(Zombie zombie) {
		var follow = zombie.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow == null) {
			return NORMAL_SIGHT;
		}
		return Math.max(0.0, follow.getValue());
	}
}
