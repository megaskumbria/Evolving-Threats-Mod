package com.example.siege;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.level.Level;

/**
 * Подключение осады к игре: тик мира, попытка лечь в кровать и выключение сервера.
 * Запрет порталов — в mixin/EntityPortalSiegeMixin, спавн — в NaturalMonsterSwap и mixin/SiegeMobMixin.
 * Вход в Ад запоминается здесь: без него скелеты-иссушители в осаду не попадают.
 */
public final class SiegeEvents {

	private SiegeEvents() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(SiegeNight::tick);
		EntitySleepEvents.ALLOW_SLEEPING.register(SiegeNight::onAllowSleeping);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SiegeNight.clearRuntime());
		// Уже был в Аду до этого обновления — достижение игры. Новый заход — смена мира.
		ServerPlayerEvents.JOIN.register(SiegeComposition::notePlayer);
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (destination.dimension() == Level.NETHER) {
				SiegeComposition.markNetherVisited(destination);
			}
		});
	}
}
