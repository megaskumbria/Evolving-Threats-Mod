package com.example.sleep;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Wires the bed check into the game: the moment someone lies down, the short wait, and the wake.
 * Siege nights are refused earlier, in {@link com.example.siege.SiegeNight}, and never reach this.
 */
public final class SleepShelterEvents {

	private SleepShelterEvents() {
	}

	public static void register() {
		EntitySleepEvents.ALLOW_SLEEPING.register(SleepShelter::onAllowSleeping);
		EntitySleepEvents.START_SLEEPING.register(SleepShelter::onStartSleeping);
		EntitySleepEvents.STOP_SLEEPING.register(SleepShelter::onStopSleeping);
		EntitySleepEvents.ALLOW_RESETTING_TIME.register(SleepShelter::allowResettingTime);
		ServerTickEvents.START_LEVEL_TICK.register(SleepShelter::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SleepShelter.clearRuntime());
	}
}
