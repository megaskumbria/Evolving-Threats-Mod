package com.example.client;

import com.example.client.golem.DamagedGolemRenderer;
import com.example.client.spawnerhead.SpawnerHeadParticles;
import com.example.client.threat.ThreatHud;
import com.example.client.tool.FloatingToolRenderer;
import com.example.golem.DamagedGolem;
import com.example.tool.FloatingTool;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class EvolvingThreatsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SpawnerHeadParticles.register();
		// Threat scale in the upper left corner of the screen.
		ThreatHud.register();
		// A picture of a floating instrument. Without this line, the mob is invisible.
		EntityRenderers.register(FloatingTool.TYPE, FloatingToolRenderer::new);
		EntityRenderers.register(DamagedGolem.TYPE, DamagedGolemRenderer::new);
	}
}