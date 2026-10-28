package com.example.spawn;

import com.example.golem.DamagedGolem;
import com.example.siege.SiegeNight;
import com.example.tool.FloatingTool;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Один бросок на появление враждебного моба.
 * В обычном мире: сначала шанс парящего инструмента, сразу за ним — шанс повреждённого голема.
 * В аду: отдельный шанс незеритового инструмента, голема нет.
 * Команда, яйцо и спавнер сюда не попадают: это только естественный спавн.
 */
public final class NaturalMonsterSwap {

	private NaturalMonsterSwap() {
	}

	public static EntityType<?> pick(Level level, EntityType<?> original, @Nullable BlockPos feet) {
		if (!(level instanceof ServerLevel server) || original.getCategory() != MobCategory.MONSTER) {
			return original;
		}
		if (server.dimension() == Level.NETHER) {
			return pickNether(server, original);
		}
		if (server.dimension() != Level.OVERWORLD) {
			return original;
		}
		// Осада: в верхнем мире остаются только зомби и скелеты. Остальные превращаются в них,
		// а парящих инструментов и големов в эту ночь нет совсем.
		if (SiegeNight.isWindowOpen(server)) {
			return SiegeNight.swapForSiege(server.getRandom(), original);
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

	/** Ад: иногда парящий незерит вместо гаста, магмового куба и прочих монстров. Голема тут нет. */
	private static EntityType<?> pickNether(ServerLevel server, EntityType<?> original) {
		if (original == FloatingTool.TYPE || FloatingTool.TYPE == null) {
			return original;
		}
		float chance = Math.max(0.0f, FloatingTool.NETHER_SPAWN_CHANCE);
		if (chance > 0.0f && server.getRandom().nextFloat() < chance) {
			return FloatingTool.TYPE;
		}
		return original;
	}
}
