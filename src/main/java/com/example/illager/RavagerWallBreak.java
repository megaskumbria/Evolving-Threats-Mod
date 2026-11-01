package com.example.illager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Разоритель пробивает стену ударом головы, если из-за неё нет пути к цели.
 * Один удар сразу сносит мягкие блоки в проёме. Камень и всё такое же твёрдое
 * трескается и падает со второго удара. Обсидиан и бедрок не берёт.
 */
public final class RavagerWallBreak {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — стены не пробивает, ходит как в обычной игре. */
	public static final boolean ENABLED = true;

	/**
	 * Минимальная пауза между ударами головы, в секундах.
	 * 20 = двадцать секунд. После удара пауза случайная, от этого числа до {@link #COOLDOWN_MAX_SECONDS}.
	 */
	public static final int COOLDOWN_MIN_SECONDS = 20;

	/**
	 * Максимальная пауза между ударами головы, в секундах.
	 * 25 = двадцать пять секунд. Если равно минимуму, пауза всегда одна и та же.
	 */
	public static final int COOLDOWN_MAX_SECONDS = 25;

	/**
	 * Сколько ударов нужно камню. 2 = первый удар только трещины, второй ломает.
	 * 1 = камень падает сразу, как земля. Сюда же попадают булыжник, глубинный сланец,
	 * кирпичи, руды и металлические блоки: их ломают киркой и они не мягче камня.
	 */
	public static final int STONE_HITS = 2;

	/**
	 * Ширина проёма в блоках. 3 = блок прямо перед мордой, один слева и один справа.
	 * Туша разорителя шире одного блока, в дыру из одного блока он не пролезает.
	 * 1 = только центральный столбец.
	 */
	public static final int HOLE_WIDTH = 3;

	/**
	 * Высота проёма в блоках, снизу от ног. 3 = ноги, тело и голова.
	 * Рост разорителя больше двух блоков, проём в два блока ему низок.
	 */
	public static final int HOLE_HEIGHT = 3;

	/**
	 * На сколько блоков вперёд смотрит удар. 2 = стена вплотную или на блок дальше.
	 * Ломается только ближайший слой, не вся толща сразу. Дальше этого стену не бьёт.
	 */
	public static final int REACH = 2;

	/** Дальше этого (блоки) от цели стену не пробивает. 32 = как далеко он вообще видит цель. */
	public static final double MAX_DISTANCE_TO_TARGET = 32.0;

	/** Как часто проверять, открылся ли путь. 10 тиков = полсекунды. */
	public static final int CHECK_INTERVAL_TICKS = 10;

	/**
	 * Выпадает ли сам блок предметом. false — стена исчезает, доски и камень на землю не падают.
	 * Вещи из сундука, бочки и печи всё равно высыпаются: блок ломается, содержимое остаётся подобрать.
	 */
	public static final boolean DROP_ITEMS = false;

	/**
	 * true — команда /gamerule mobGriefing false запрещает пробивать стены.
	 * false — пробивает даже при выключенном правиле.
	 */
	public static final boolean RESPECT_MOB_GRIEFING = true;

	// ========================================================================

	/** Твёрдость обычного камня. Блок кирки не мягче этого считается камнем. */
	private static final float STONE_HARDNESS = 1.5f;

	/** В одной секунде 20 тиков. */
	private static final int TICKS_PER_SECOND = 20;

	private RavagerWallBreak() {
	}

	public static boolean canBreak(Ravager ravager) {
		if (!ENABLED) {
			return false;
		}
		if (RESPECT_MOB_GRIEFING
				&& !(ravager.level() instanceof ServerLevel level && level.getGameRules().get(GameRules.MOB_GRIEFING))) {
			return false;
		}
		return true;
	}

	/** Случайная пауза после удара, в тиках. */
	public static int cooldownTicks(Ravager ravager) {
		int min = Math.max(0, COOLDOWN_MIN_SECONDS);
		int max = Math.max(min, COOLDOWN_MAX_SECONDS);
		int seconds = min + ravager.getRandom().nextInt(max - min + 1);
		return seconds * TICKS_PER_SECOND;
	}

	/**
	 * Ближайший слой стены перед разорителем. Пустой список — впереди ломать нечего
	 * (воздух, обсидиан или бедрок). Сундук, бочка и печь ломаются как обычная стена.
	 */
	public static List<BlockPos> nearestWall(Level level, BlockPos feet, Direction forward) {
		if (forward.getAxis().isVertical()) {
			return List.of();
		}
		for (int depth = 1; depth <= Math.max(1, REACH); depth++) {
			BlockPos center = feet.relative(forward, depth);
			if (!layerBlocksMovement(level, center, forward)) {
				continue;
			}
			// Первый сплошной слой. Обсидиан в нём остаётся, ломается только остальное.
			// Сквозь обсидиан к блокам дальше удар не проходит.
			return layer(level, center, forward);
		}
		return List.of();
	}

	/**
	 * Сколько ударов нужно этому блоку.
	 * 0 = не трогать: воздух, обсидиан, бедрок и неломаемое.
	 * Сундук и бочка — один удар. Печь твёрдая, как камень: ей нужно {@link #STONE_HITS}.
	 */
	public static int hitsNeeded(Level level, BlockPos pos, BlockState state) {
		if (!isObstacle(level, pos, state) || isNeverBroken(state)) {
			return 0;
		}
		if (STONE_HITS <= 1) {
			return 1;
		}
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.getDestroySpeed(level, pos) >= STONE_HARDNESS) {
			return STONE_HITS;
		}
		return 1;
	}

	/** Блок мешает пройти: есть коллизия. Бедрок и барьер тоже, хотя сломать их нельзя. */
	private static boolean blocksMovement(Level level, BlockPos pos, BlockState state) {
		return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
	}

	/** Стена, забор, земля, дерево: мешает пройти и блок вообще можно сломать. */
	private static boolean isObstacle(Level level, BlockPos pos, BlockState state) {
		if (!blocksMovement(level, pos, state)) {
			return false;
		}
		return state.getDestroySpeed(level, pos) >= 0.0f;
	}

	/** Обсидиан, плачущий обсидиан и бедрок. Барьер и усиленный глубинный сланец отсекаются твёрдостью ниже нуля. */
	private static boolean isNeverBroken(BlockState state) {
		return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.BEDROCK);
	}

	/**
	 * Ломает блок. Сам блок предметом не падает, если {@link #DROP_ITEMS} выключен.
	 * Сундук, бочка и печь при этом высыпают вещи на землю, иначе содержимое пропало бы вместе с блоком.
	 */
	public static void breakBlock(Level level, BlockPos pos, Ravager ravager) {
		if (!DROP_ITEMS && level.getBlockEntity(pos) instanceof Container container) {
			Containers.dropContents(level, pos, container);
		}
		level.destroyBlock(pos, DROP_ITEMS, ravager);
	}

	/** Есть ли в этом слое хоть один блок, который мешает пройти, даже обсидиан. */
	private static boolean layerBlocksMovement(Level level, BlockPos center, Direction forward) {
		Direction left = forward.getCounterClockWise();
		int half = Math.max(0, HOLE_WIDTH - 1) / 2;
		int extra = Math.max(0, HOLE_WIDTH - 1 - half);
		int height = Math.max(1, HOLE_HEIGHT);
		for (int side = -half; side <= extra; side++) {
			BlockPos column = center.relative(left, side);
			for (int dy = 0; dy < height; dy++) {
				BlockPos pos = column.above(dy);
				if (blocksMovement(level, pos, level.getBlockState(pos))) {
					return true;
				}
			}
		}
		return false;
	}

	private static List<BlockPos> layer(Level level, BlockPos center, Direction forward) {
		Direction left = forward.getCounterClockWise();
		int half = Math.max(0, HOLE_WIDTH - 1) / 2;
		int extra = Math.max(0, HOLE_WIDTH - 1 - half);
		int height = Math.max(1, HOLE_HEIGHT);
		List<BlockPos> blocks = new ArrayList<>();
		for (int side = -half; side <= extra; side++) {
			BlockPos column = center.relative(left, side);
			for (int dy = 0; dy < height; dy++) {
				BlockPos pos = column.above(dy);
				BlockState state = level.getBlockState(pos);
				if (hitsNeeded(level, pos, state) > 0) {
					blocks.add(pos);
				}
			}
		}
		return blocks;
	}
}
