package com.example.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Свет от светящейся тыквы на голове. Сам предмет в слоте шлема мир не освещает,
 * поэтому рядом с головой ставится невидимый блок света и едет вместе с мобом.
 * Тыква снята, моб умер или ушёл из мира — свет гаснет.
 */
public final class JackLanternLight {

	// ===================== SETTINGS (меняй эти числа) =====================

	/**
	 * Насколько ярко светит тыква на голове. 0–15.
	 * 15 = как блок светильника Джека, пещера вокруг моба освещена.
	 * 7 = примерно как факел поменьше. 0 = не светить.
	 */
	public static final int LIGHT_LEVEL = 15;

	// ========================================================================

	/** Где сейчас стоит свет этого моба. После выгрузки мира карта пустая: свет к тому моменту уже убран. */
	private static final Map<Mob, BlockPos> PLACED = new WeakHashMap<>();

	private static final int UPDATE = Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE;

	private JackLanternLight() {
	}

	/** Каждый ход моба. Свет переставляется, только когда голова перешла в другой блок. */
	public static void tick(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		if (LIGHT_LEVEL <= 0 || !mob.isAlive() || !JackLanternBeacon.hasJackOnHead(mob)) {
			clear(mob);
			return;
		}
		BlockPos head = BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ());
		BlockPos previous = PLACED.get(mob);
		if (head.equals(previous) && level.getBlockState(head).is(Blocks.LIGHT)) {
			return;
		}
		if (previous != null && !previous.equals(head)) {
			removeLight(level, previous);
		}
		BlockState at = level.getBlockState(head);
		if (at.isAir()) {
			level.setBlock(head, lightState(), UPDATE);
			PLACED.put(mob, head);
		} else if (at.is(Blocks.LIGHT)) {
			PLACED.put(mob, head);
		} else {
			PLACED.remove(mob);
		}
	}

	/** Моб исчез из мира: убрать его свет, чтобы в пещере не осталась вечная лампа. */
	public static void clear(Mob mob) {
		BlockPos previous = PLACED.remove(mob);
		if (previous != null && mob.level() instanceof ServerLevel level) {
			removeLight(level, previous);
		}
	}

	private static void removeLight(ServerLevel level, BlockPos pos) {
		if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE);
		}
	}

	private static BlockState lightState() {
		int level = Math.min(LightBlock.MAX_LEVEL, LIGHT_LEVEL);
		return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
	}
}
