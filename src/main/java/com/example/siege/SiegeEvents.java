package com.example.siege;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Подключение осады к игре: тик мира, попытка лечь в кровать и выключение сервера.
 * Запрет порталов — в mixin/EntityPortalSiegeMixin, спавн — в NaturalMonsterSwap и mixin/SiegeMobMixin.
 */
public final class SiegeEvents {

	private SiegeEvents() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(SiegeNight::tick);
		EntitySleepEvents.ALLOW_SLEEPING.register(SiegeNight::onAllowSleeping);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SiegeNight.clearRuntime());
	}
}
