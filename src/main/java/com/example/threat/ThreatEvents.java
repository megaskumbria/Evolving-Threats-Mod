package com.example.threat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hooks the threat scale into the game: remembers when the mod was first applied to a world
 * and tells clients that start time.
 *
 * <p>When the mod sees a world for the first time (new world or old world with the mod newly added),
 * it stores the current world time as "day 0". The scale then counts days from that moment, so
 * a world that is already on day 24 in vanilla still starts the scale at zero.
 */
public final class ThreatEvents {

	private ThreatEvents() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ThreatStartPayload.TYPE, ThreatStartPayload.CODEC);

		// World loaded: read saved start time or set it the first time.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel overworld = server.overworld();
			ThreatData data = ThreatData.get(overworld);
			if (!data.initialized) {
				data.initialized = true;
				data.startClock = overworld.getOverworldClockTime();
				data.setDirty();
			}
			ThreatScale.setStartClock(data.startClock);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				ServerPlayNetworking.send(handler.getPlayer(), new ThreatStartPayload(ThreatScale.getStartClock())));
	}

	/**
	 * Changes the start time ({@code /evolvingthreats day set}) and notifies all online players.
	 */
	public static void setStartClock(MinecraftServer server, long startClock) {
		ThreatData data = ThreatData.get(server.overworld());
		data.initialized = true;
		data.startClock = startClock;
		data.setDirty();
		ThreatScale.setStartClock(startClock);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, new ThreatStartPayload(startClock));
		}
	}
}
