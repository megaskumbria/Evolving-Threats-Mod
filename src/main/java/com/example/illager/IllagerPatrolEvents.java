package com.example.illager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Отряд проверяется в конце тика каждого мира.
 */
public final class IllagerPatrolEvents {

	private IllagerPatrolEvents() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(IllagerPatrol::tick);
	}
}
