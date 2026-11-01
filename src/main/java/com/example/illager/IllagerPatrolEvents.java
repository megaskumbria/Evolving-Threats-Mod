package com.example.illager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 The squad is tested at the end of each world tick.
 */
public final class IllagerPatrolEvents {

	private IllagerPatrolEvents() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(IllagerPatrol::tick);
	}
}
