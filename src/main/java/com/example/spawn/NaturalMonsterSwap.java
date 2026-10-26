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
 * Один бросок на появление враждебного моба в обычном мире.
 * Сначала шанс парящего инструмента, сразу за ним — шанс повреждённого голема.
 * Команда, яйцо и спавнер сюда не попадают: это только ночной спавн.
 */
public final class NaturalMonsterSwap {

	private NaturalMonsterSwap() {
	}

	public static EntityType<?> pick(Level level, EntityType<?> original, @Nullable BlockPos feet) {
		if (!(level instanceof ServerLevel server) || server.dimension() != Level.OVERWORLD) {
			return original;
		}
		if (original.getCategory() != MobCategory.MONSTER) {
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
}
