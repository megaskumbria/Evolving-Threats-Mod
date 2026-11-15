package com.example.spawn;

import com.example.golem.DamagedGolem;
import com.example.piglin.HellBerserk;
import com.example.siege.SiegeNight;
import com.example.tool.FloatingTool;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * One roll per natural hostile spawn.
 * Overworld: floating tool chance first, then damaged iron golem chance.
 * Nether: netherite floating tool first, then the Hell Berserk. No golem.
 * Commands, spawn eggs, and spawners skip this — natural spawn only.
 */
public final class NaturalMonsterSwap {

	private NaturalMonsterSwap() {
	}

	public static EntityType<?> pick(Level level, EntityType<?> original, @Nullable BlockPos feet) {
		if (!(level instanceof ServerLevel server) || original.getCategory() != MobCategory.MONSTER) {
			return original;
		}
		if (server.dimension() == Level.NETHER) {
			return pickNether(server, original, feet);
		}
		if (server.dimension() != Level.OVERWORLD) {
			return original;
		}
		// Siege: in the overworld any night monster is replaced by the current siege roster (by weight).
		// No floating tools or golems during siege night.
		if (SiegeNight.isWindowOpen(server)) {
			return SiegeNight.swapForSiege(server, feet);
		}
		if (original == FloatingTool.TYPE || original == DamagedGolem.TYPE) {
			return original;
		}

		float toolChance = Math.max(0.0f, FloatingTool.NATURAL_SPAWN_CHANCE);
		float golemChance = Math.max(0.0f, DamagedGolem.NATURAL_SPAWN_CHANCE);
		if (toolChance <= 0.0f && golemChance <= 0.0f) {
			return original;
		}

		float roll = server.getRandom().nextFloat();
		if (roll < toolChance && FloatingTool.TYPE != null) {
			return FloatingTool.TYPE;
		}
		if (roll < toolChance + golemChance
				&& DamagedGolem.TYPE != null
				&& feet != null
				&& DamagedGolem.hasRoom(server, feet)) {
			return DamagedGolem.TYPE;
		}
		return original;
	}

	/**
	 * Nether: one roll for the floating netherite tool, then the Hell Berserk.
	 * A berserk only replaces a monster that has room to stand. A ghast in the air stays a ghast.
	 */
	private static EntityType<?> pickNether(ServerLevel server, EntityType<?> original, @Nullable BlockPos feet) {
		if (original == FloatingTool.TYPE || original == HellBerserk.TYPE) {
			return original;
		}
		float toolChance = FloatingTool.TYPE == null ? 0.0f : Math.max(0.0f, FloatingTool.NETHER_SPAWN_CHANCE);
		float berserkChance = Math.max(0.0f, HellBerserk.NATURAL_SPAWN_CHANCE);
		if (toolChance <= 0.0f && berserkChance <= 0.0f) {
			return original;
		}
		float roll = server.getRandom().nextFloat();
		if (roll < toolChance && FloatingTool.TYPE != null) {
			return FloatingTool.TYPE;
		}
		if (roll < toolChance + berserkChance
				&& HellBerserk.TYPE != null
				&& feet != null
				&& HellBerserk.hasRoom(server, feet)) {
			return HellBerserk.TYPE;
		}
		return original;
	}
}
