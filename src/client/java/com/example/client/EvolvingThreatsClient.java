package com.example.client;

import com.example.client.spawnerhead.SpawnerHeadParticles;
import net.fabricmc.api.ClientModInitializer;

public class EvolvingThreatsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SpawnerHeadParticles.register();
	}
}