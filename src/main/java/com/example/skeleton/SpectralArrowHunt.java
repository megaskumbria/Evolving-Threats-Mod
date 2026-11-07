package com.example.skeleton;

import com.example.advancement.ModAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * When a skeleton's spectral arrow hits a player, nearby hostile mobs drop what they were doing and chase that player.
 * Peaceful animals ignore it. Target is refreshed each tick so mobs do not forget the player after losing line of sight.
 */
public final class SpectralArrowHunt {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * Radius (blocks) around the hit player. Mobs farther away at the moment of the hit are not alerted.
	 * Mobs that were inside still chase even if they later leave the radius.
	 */
	public static final double AGGRO_RADIUS = 80.0;

	/**
	 * How long (seconds) mobs remember the player after the hit. 30 = half a minute.
	 * Until this expires they will not switch to another target.
	 */
	public static final int HUNT_SECONDS = 30;

	// ========================================================================

	private static final Map<UUID, Hunt> HUNTS = new HashMap<>();

	private record Hunt(UUID playerId, long untilGameTime) {
	}

	private SpectralArrowHunt() {
	}

	/**
	 * Call when a skeleton spectral arrow hits a player.
	 * {@code shooter} is not counted: the card needs someone else to turn.
	 */
	public static void alert(Player player, Entity shooter) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		long until = now + HUNT_SECONDS * 20L;
		HUNTS.entrySet().removeIf(entry -> entry.getValue().untilGameTime < now);

		int others = 0;
		AABB area = player.getBoundingBox().inflate(AGGRO_RADIUS);
		for (Monster monster : level.getEntitiesOfClass(Monster.class, area, mob -> mob.distanceTo(player) <= AGGRO_RADIUS)) {
			HUNTS.put(monster.getUUID(), new Hunt(player.getUUID(), until));
			monster.setTarget(player);
			if (monster != shooter) {
				others++;
			}
		}
		if (others > 0 && player instanceof ServerPlayer serverPlayer) {
			ModAdvancements.onAllTurned(serverPlayer);
		}
	}

	/** Before navigation step: restore target while the hunt is still active. */
	public static void keepChasing(Mob mob) {
		Hunt hunt = HUNTS.get(mob.getUUID());
		if (hunt == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		if (!mob.isAlive() || level.getGameTime() > hunt.untilGameTime) {
			HUNTS.remove(mob.getUUID());
			return;
		}
		Player player = level.getPlayerByUUID(hunt.playerId);
		if (player == null || !player.isAlive() || player.level() != level) {
			HUNTS.remove(mob.getUUID());
			return;
		}
		if (mob.getTarget() != player) {
			mob.setTarget(player);
		}
	}

	/** Mob despawned or died — drop from hunt list. */
	public static void forget(Mob mob) {
		HUNTS.remove(mob.getUUID());
	}
}
