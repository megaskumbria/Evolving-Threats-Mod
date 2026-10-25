package com.example.digging;

import com.example.zombie.TntZombie;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Зомби ломают блоки: (1) чтобы добраться до игрока, (2) чтобы уничтожить найденный свет.
 * Здесь — все настройки и общие правила («можно ли ломать», «как быстро»).
 * Само поведение: {@link com.example.entity.ai.BreakToTargetGoal} (к игроку)
 * и {@link com.example.entity.ai.LightSeekGoal} (свет).
 *
 * Скорость ломания считается как у игрока (твёрдость блока и инструмент в руке зомби),
 * но умножается на {@link #SPEED_MULTIPLIER}, чтобы зомби не были бульдозерами.
 * Инструмент подходит по типу: кирка — камень, лопата — земля/песок, топор — дерево.
 * Материал важен: железная кирка быстрее деревянной (как у игрока).
 */
public final class BlockBreaking {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить ломание блоков вообще (и к игроку, и свет). false — зомби блоки не ломают. */
	public static final boolean ENABLED = true;

	/**
	 * Доля зомби, которые умеют ломать блоки (0.0–1.0): 1.0 = все (режим теста), 0.30 = 30%.
	 * Решается один раз для каждого зомби (по его уникальному номеру), поэтому «ломающие» не меняются.
	 */
	public static final double BREAKER_FRACTION = 1.0;

	/**
	 * Множитель скорости относительно игрока: 0.3 = 30% от скорости игрока.
	 * Больше — быстрее ломают (1.0 = как игрок). Меньше — медленнее.
	 * Примеры при 0.3: земля голой рукой ~2.5 сек; камень голой рукой ~25 сек;
	 * камень железной киркой ~1.5 сек.
	 */
	public static final float SPEED_MULTIPLIER = 0.3f;

	/** Выпадают ли предметы из сломанных зомби блоков. false — блок просто исчезает. */
	public static final boolean DROP_ITEMS = false;

	/**
	 * Блоки, которые зомби НЕ могут ломать никогда.
	 * Блоки с «неломаемой» твёрдостью (барьер, командный блок и т.п.) исключаются автоматически.
	 * Чтобы запретить ещё что-то, добавь Blocks.НАЗВАНИЕ (например Blocks.CRYING_OBSIDIAN).
	 */
	public static final Set<Block> UNBREAKABLE_BLOCKS = Set.of(Blocks.BEDROCK, Blocks.OBSIDIAN);

	/** Как далеко (блоки) от цели зомби ещё пытается пробиться к игроку. Дальше — не копает. */
	public static final double DIG_MAX_DISTANCE_TO_TARGET = 32.0;

	/** Как часто (тики) зомби проверяет «я застрял?» при поиске цели копания (10 тиков = 0.5 сек). */
	public static final int STUCK_CHECK_INTERVAL_TICKS = 10;

	/**
	 * Сколько тиков трещина на блоке не сбрасывается, если зомби отвлёкся и вернулся (20 тиков = 1 сек).
	 * 100 = 5 секунд: ударили, отошёл, вернулся — ломает дальше с того же места.
	 * 0 = как раньше, после любой паузы блок ломается заново.
	 */
	public static final int KEEP_PROGRESS_TICKS = 100;

	/**
	 * Копать вниз под ногами только если игрок минимум на столько блоков ниже зомби.
	 * 3 = не копают яму на ровной земле из‑за небольшого перепада высоты.
	 */
	public static final int DIG_DOWN_MIN_BLOCKS_BELOW = 3;

	/**
	 * Копать вниз только если игрок не дальше этого по горизонтали (блоки).
	 * Иначе зомби копает вниз, хотя игрок сбоку на дороге.
	 */
	public static final double DIG_DOWN_MAX_HORIZONTAL = 8.0;

	/**
	 * Сколько блоков ВЫШЕ ног зомби (в клетке «перед ним» к игроку) можно ломать,
	 * чтобы добраться до игрока на возвышении. 2 = ноги+1 и ноги+2 (подставка из 2 блоков).
	 */
	public static final int DIG_UP_MAX_BLOCKS_ABOVE_SELF = 2;

	/**
	 * Если игрок выше зомби — разрешить ломать блок прямо перед ногами (ступенька),
	 * иначе зомби бьёт только «головой в стену» и не снимает подставку под игроком.
	 */
	public static final boolean DIG_UP_ALLOW_FRONT_AT_FEET = true;

	/**
	 * Горизонталь (блоки): ближе этого к игроку — не копать, если можно ударить
	 * (см. {@link #shouldPreferMeleeOverDig}). 2.0 ≈ соседняя клетка + диагональ.
	 */
	public static final double DIG_MELEE_HORIZONTAL_SNAP = 2.0;

	/**
	 * Игрок не выше зомби больше чем на столько блоков — при близкой дистанции приоритет удара, не копания.
	 */
	public static final double DIG_MELEE_VERTICAL_REACH = 3.0;

	/** Дальность руки зомби (блоки): дальше этого блок ломать нельзя. */
	public static final double REACH = 3.5;

	/**
	 * Уважать игровое правило mobGriefing (команда /gamerule mobGriefing false).
	 * true — если правило выключено, зомби блоки не ломают (как ванильные двери).
	 * false — ломают всегда.
	 */
	public static final boolean RESPECT_MOB_GRIEFING = true;

	// ========================================================================

	private BlockBreaking() {
	}

	/** Умеет ли этот зомби ломать блоки (общее правило для обоих поведений). */
	public static boolean isBreaker(Zombie zombie) {
		if (!ENABLED) {
			return false;
		}
		if (RESPECT_MOB_GRIEFING
				&& !(zombie.level() instanceof ServerLevel serverLevel && serverLevel.getGameRules().get(GameRules.MOB_GRIEFING))) {
			return false;
		}
		// Утопленники — в воде, зомби-пиглины — не верхний мир.
		// Зомби с динамитом стену не копает: если пути нет, он роняет TNT.
		if (zombie instanceof Drowned || zombie instanceof ZombifiedPiglin || TntZombie.isTntZombie(zombie)) {
			return false;
		}
		if (BREAKER_FRACTION >= 1.0) {
			return true;
		}
		// Стабильный «жребий» по UUID: один и тот же зомби всегда либо ломает, либо нет.
		long bits = zombie.getUUID().getLeastSignificantBits() ^ zombie.getUUID().getMostSignificantBits();
		double roll = ((bits >>> 1) % 10000) / 10000.0;
		return roll < BREAKER_FRACTION;
	}

	/** Можно ли сломать этот блок (не воздух, не жидкость, не бедрок/обсидиан, не «вечный»). */
	public static boolean canBreak(BlockGetter level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
			return false;
		}
		if (UNBREAKABLE_BLOCKS.contains(state.getBlock())) {
			return false;
		}
		return state.getDestroySpeed(level, pos) >= 0.0f;
	}

	/**
	 * Сколько «прогресса» (0.0–1.0 = блок сломан) зомби набирает за 1 тик на этом блоке.
	 * Формула как у игрока: скорость инструмента / твёрдость / (30 или 100), умноженная на SPEED_MULTIPLIER.
	 * 100 вместо 30 — если для блока нужен подходящий инструмент, а у зомби его нет (медленнее).
	 */
	/**
	 * Игрок достаточно близко для удара — копание к нему не нужно (даже если pathfinder «застрял»).
	 * Учитывает ванильный радиус и пересечение хитбоксов (игрок на соседнем блоке сверху).
	 */
	public static boolean shouldPreferMeleeOverDig(Zombie zombie, LivingEntity target) {
		if (zombie.isWithinMeleeAttackRange(target)) {
			return true;
		}
		double dx = target.getX() - zombie.getX();
		double dz = target.getZ() - zombie.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal > DIG_MELEE_HORIZONTAL_SNAP) {
			return false;
		}
		double dy = target.getY() - zombie.getY();
		if (dy < -1.0 || dy > DIG_MELEE_VERTICAL_REACH) {
			return false;
		}
		AABB reach = zombie.getBoundingBox().inflate(0.85, 0.35, 0.85);
		return reach.intersects(target.getBoundingBox());
	}

	public static float progressPerTick(Zombie zombie, BlockGetter level, BlockPos pos, BlockState state) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness <= 0.0f) {
			return 1.0f; // мгновенно ломаемые блоки (факел, трава)
		}
		ItemStack tool = zombie.getMainHandItem();
		float toolSpeed = tool.getDestroySpeed(state);
		boolean canHarvest = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
		float perTick = toolSpeed / hardness / (canHarvest ? 30.0f : 100.0f);
		return perTick * SPEED_MULTIPLIER;
	}
}
