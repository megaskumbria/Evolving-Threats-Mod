package com.example.raid;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RaidBossBars {

	private static final Map<UUID, ServerBossEvent> BARS = new ConcurrentHashMap<>();

	private RaidBossBars() {
	}

	public static void createFor(Zombie anchor) {
		remove(anchor.getUUID());
		Component title = Component.literal(RaidAnchor.DISPLAY_NAME)
				.append("\n")
				.append(Component.literal(RaidAnchor.BOSS_BAR_SUBTITLE).withStyle(s -> s.withColor(0xAAAAAA)));

		ServerBossEvent event = new ServerBossEvent(
				anchor.getUUID(),
				title,
				BossEvent.BossBarColor.PURPLE,
				BossEvent.BossBarOverlay.NOTCHED_12
		);
		event.setDarkenScreen(false);
		event.setPlayBossMusic(false);
		event.setCreateWorldFog(false);
		BARS.put(anchor.getUUID(), event);
	}

	public static void tick(Zombie anchor, ServerLevel level) {
		ServerBossEvent bar = BARS.get(anchor.getUUID());
		if (bar == null) {
			return;
		}
		float progress = anchor.getHealth() / anchor.getMaxHealth();
		bar.setProgress(Math.max(0.0f, Math.min(1.0f, progress)));

		double showDistSqr = RaidAnchor.BOSS_BAR_SHOW_DISTANCE * RaidAnchor.BOSS_BAR_SHOW_DISTANCE;
		for (ServerPlayer player : level.players()) {
			boolean inRange = anchor.isAlive() && player.level() == level
					&& anchor.distanceToSqr(player) <= showDistSqr;
			if (inRange) {
				bar.addPlayer(player);
			} else {
				bar.removePlayer(player);
			}
		}
	}

	public static void remove(UUID anchorEntityUuid) {
		ServerBossEvent bar = BARS.remove(anchorEntityUuid);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}
}
