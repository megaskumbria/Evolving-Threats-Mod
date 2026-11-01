package com.example.item;

import com.example.EvolvingThreats;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ModItems {

	public static Item ROTTEN_OVERSEER_SPAWN_EGG;

	private ModItems() {
	}

	public static void register() {
		// In 26.3, the object id is required in Properties until new Item(...), otherwise item id not set.
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, EvolvingThreats.id("rotten_overseer_spawn_egg"));
		ROTTEN_OVERSEER_SPAWN_EGG = Registry.register(
				BuiltInRegistries.ITEM,
				key,
				new RottenOverseerSpawnEggItem(new Item.Properties().setId(key))
		);

		// The Invocation Eggs tab in the creative is right after the vanilla zombie egg.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output ->
				output.insertAfter(Items.ZOMBIE_SPAWN_EGG, ROTTEN_OVERSEER_SPAWN_EGG)
		);
	}
}
