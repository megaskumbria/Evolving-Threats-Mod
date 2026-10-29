package com.example;

import com.example.command.EvolvingThreatsCommands;
import com.example.golem.DamagedGolem;
import com.example.illager.IllagerPatrolEvents;
import com.example.item.ModItems;
import com.example.raid.RaidAnchorEvents;
import com.example.siege.SiegeEvents;
import com.example.threat.ThreatEvents;
import com.example.tool.FloatingTool;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvolvingThreats implements ModInitializer {
	public static final String MOD_ID = "evolving-threats";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
		ModItems.register();
		FloatingTool.register();
		DamagedGolem.register();
		EvolvingThreatsCommands.register();
		RaidAnchorEvents.register();
		IllagerPatrolEvents.register();
		SiegeEvents.register();
		ThreatEvents.register();
	}

	public static @NotNull Identifier id(@NotNull String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
