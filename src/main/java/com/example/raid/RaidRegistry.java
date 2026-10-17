package com.example.raid;

import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Связь raidId → entity id якоря (для проверки «якорь ещё жив»). */
public final class RaidRegistry {

	private static final Map<UUID, Integer> ANCHOR_ENTITY_BY_RAID = new ConcurrentHashMap<>();

	private RaidRegistry() {
	}

	public static void registerAnchor(UUID raidId, int anchorEntityId) {
		ANCHOR_ENTITY_BY_RAID.put(raidId, anchorEntityId);
	}

	public static void unregister(UUID raidId) {
		ANCHOR_ENTITY_BY_RAID.remove(raidId);
	}

	public static boolean isAnchorAlive(ServerLevel level, UUID raidId) {
		Integer id = ANCHOR_ENTITY_BY_RAID.get(raidId);
		if (id == null) {
			return false;
		}
		var entity = level.getEntity(id);
		return entity != null && entity.isAlive();
	}
}
