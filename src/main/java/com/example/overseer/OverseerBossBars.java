package com.example.overseer;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boss bar надсмотрщика: виден только игрокам в радиусе {@link ZombieOverseer#BOSS_BAR_SHOW_DISTANCE}.
 */
public final class OverseerBossBars {

	private static final Map<UUID, ServerBossEvent> BARS = new ConcurrentHashMap<>();

	private OverseerBossBars() {
	}

	public static void createFor(Zombie overseer) {
		remove(overseer.getUUID());
		Component title = Component.literal(ZombieOverseer.DISPLAY_NAME);

		ServerBossEvent event = new ServerBossEvent(
				overseer.getUUID(),
				title,
				ZombieOverseer.bossBarColor(),
				ZombieOverseer.bossBarOverlay()
		);
		event.setDarkenScreen(false);
		event.setPlayBossMusic(false);
		event.setCreateWorldFog(false);
		BARS.put(overseer.getUUID(), event);
	}

	public static void tick(Zombie overseer, ServerLevel level) {
		ServerBossEvent bar = BARS.get(overseer.getUUID());
		if (bar == null) {
			return;
		}

		float progress = overseer.getHealth() / overseer.getMaxHealth();
		bar.setProgress(Math.max(0.0f, Math.min(1.0f, progress)));

		double showDistSqr = ZombieOverseer.BOSS_BAR_SHOW_DISTANCE * ZombieOverseer.BOSS_BAR_SHOW_DISTANCE;
		for (ServerPlayer player : level.players()) {
			boolean inRange = overseer.isAlive() && player.level() == level
					&& overseer.distanceToSqr(player) <= showDistSqr;
			if (inRange) {
				bar.addPlayer(player);
			} else {
				bar.removePlayer(player);
			}
		}
	}

	public static void remove(UUID overseerId) {
		ServerBossEvent bar = BARS.remove(overseerId);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}
}
