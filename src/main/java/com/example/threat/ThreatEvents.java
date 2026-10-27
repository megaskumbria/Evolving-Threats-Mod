package com.example.threat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Связка шкалы угрозы с игрой: запоминает момент установки мода в мир и сообщает его игрокам.
 *
 * <p>Когда мод видит мир впервые (новый мир или старый, куда мод только что добавили), он записывает
 * текущее время мира как «день 0». Дальше шкала считает дни от этого момента, поэтому в мире,
 * где уже прошло 24 дня, шкала всё равно начинается с нуля.
 */
public final class ThreatEvents {

	private ThreatEvents() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ThreatStartPayload.TYPE, ThreatStartPayload.CODEC);

		// Мир загружен: читаем сохранённый старт или ставим его впервые.
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
	 * Меняет момент старта (команда {@code /evolvingthreats day set}) и сразу сообщает всем игрокам.
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
