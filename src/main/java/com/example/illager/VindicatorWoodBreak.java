package com.example.illager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Поборник рубит дерево, если оно стоит между ним и целью.
 * Забор, калитка, дверь, люк, доски, брёвна, ступеньки, плиты, таблички.
 * Камень, незерский забор и земля не трогает.
 * Сундук и бочка тоже нет: иначе пропадали бы вещи внутри.
 */
public final class VindicatorWoodBreak {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — поборник дерево не рубит. Двери в рейде игра ломает по-своему. */
	public static final boolean ENABLED = true;

	/**
	 * Доля скорости игрока с таким же топором. 0.2 = 20%.
	 * Материал топора уже внутри этой скорости: железо быстрее руки, алмаз быстрее железа.
	 * Эффективность на топоре тоже ускоряет, острота — нет, она только про удар.
	 * Дубовый забор (твёрдость 2): железный топор около 2.5 сек, алмазный около 1.9 сек.
	 * 1.0 = как игрок. 0 = дерево не ломается.
	 */
	public static final float SPEED_MULTIPLIER = 0.2f;

	/** Выпадает ли дерево предметом. false — блок просто исчезает. */
	public static final boolean DROP_ITEMS = false;

	/** Дальше этого (блоки) от цели забор не рубят. */
	public static final double MAX_DISTANCE_TO_TARGET = 32.0;

	/** Как часто проверять, открылся ли путь. 10 тиков = полсекунды. */
	public static final int CHECK_INTERVAL_TICKS = 10;

	/**
	 * Сколько тиков трещина не сбрасывается, если поборник отвлёкся и вернулся.
	 * 100 = 5 секунд.
	 */
	public static final int KEEP_PROGRESS_TICKS = 100;

	/** Дальность руки (блоки). Дальше этого блок не рубит. */
	public static final double REACH = 3.5;

	/**
	 * true — команда /gamerule mobGriefing false запрещает рубку.
	 * false — рубят даже при выключенном правиле.
	 */
	public static final boolean RESPECT_MOB_GRIEFING = true;

	// ========================================================================

	private VindicatorWoodBreak() {
	}

	public static boolean canChop(Vindicator vindicator) {
		if (!ENABLED || SPEED_MULTIPLIER <= 0.0f) {
			return false;
		}
		if (RESPECT_MOB_GRIEFING
				&& !(vindicator.level() instanceof ServerLevel level && level.getGameRules().get(GameRules.MOB_GRIEFING))) {
			return false;
		}
		return true;
	}

	/** Дерево, которое мешает пройти: есть коллизия и это деревянный блок. */
	public static boolean isWoodenObstacle(BlockGetter level, BlockPos pos, BlockState state) {
		if (!isWood(state) || state.getCollisionShape(level, pos).isEmpty()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0f;
	}

	/**
	 * Доски, брёвна, деревянный забор и калитка, дверь, люк, ступеньки, плиты,
	 * кнопка, плита, полка, табличка, бамбук.
	 */
	public static boolean isWood(BlockState state) {
		return state.is(BlockTags.PLANKS)
				|| state.is(BlockTags.LOGS)
				|| state.is(BlockTags.WOODEN_FENCES)
				|| state.is(BlockTags.FENCE_GATES)
				|| state.is(BlockTags.WOODEN_DOORS)
				|| state.is(BlockTags.WOODEN_TRAPDOORS)
				|| state.is(BlockTags.WOODEN_STAIRS)
				|| state.is(BlockTags.WOODEN_SLABS)
				|| state.is(BlockTags.WOODEN_BUTTONS)
				|| state.is(BlockTags.WOODEN_PRESSURE_PLATES)
				|| state.is(BlockTags.WOODEN_SHELVES)
				|| state.is(BlockTags.ALL_SIGNS)
				|| state.is(BlockTags.BAMBOO_BLOCKS);
	}

	/**
	 * Прогресс за один тик, 1.0 = блок сломан.
	 * Формула как у игрока: скорость топора / твёрдость / 30, потом {@link #SPEED_MULTIPLIER}.
	 */
	public static float progressPerTick(Vindicator vindicator, BlockGetter level, BlockPos pos, BlockState state) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness <= 0.0f) {
			return 1.0f;
		}
		ItemStack axe = vindicator.getMainHandItem();
		float toolSpeed = Math.max(1.0f, axe.getDestroySpeed(state));
		boolean canHarvest = !state.requiresCorrectToolForDrops() || axe.isCorrectToolForDrops(state);
		float perTick = toolSpeed / hardness / (canHarvest ? 30.0f : 100.0f);
		return perTick * SPEED_MULTIPLIER;
	}
}
