package com.example.siege;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.level.Level;

/**
 * Wires siege into the game: world tick, sleep attempts, and server shutdown.
 * Portal block — mixin/EntityPortalSiegeMixin; spawn swap — NaturalMonsterSwap and mixin/SiegeMobMixin.
 * Nether visits are tracked here: without that, wither skeletons are excluded from siege composition.
 */
public final class SiegeEvents {

	private SiegeEvents() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(SiegeNight::tick);
		EntitySleepEvents.ALLOW_SLEEPING.register(SiegeNight::onAllowSleeping);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SiegeNight.clearRuntime());
		// Already visited the Nether before this update — vanilla advancement. Fresh visit — dimension change.
		ServerPlayerEvents.JOIN.register(SiegeComposition::notePlayer);
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (destination.dimension() == Level.NETHER) {
				SiegeComposition.markNetherVisited(destination);
			}
		});
	}
}
