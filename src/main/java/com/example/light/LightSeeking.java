package com.example.light;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;

import com.example.beacon.JackLanternBeacon;
import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieOverseerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Зомби идут на яркий свет (факел, светокамень, фонарь…), если не видят игрока.
 * Логика поиска — здесь; поведение «идти / стоять / идти дальше» — в {@link com.example.entity.ai.LightSeekGoal}.
 *
 * Кто участвует: обычный зомби, хаск, зомби-житель.
 * Исключены: утопленник (в воде), зомби-пиглин (не верхний мир),
 * надсмотрщик и прислуга рейда (у них своя охота).
 */
public final class LightSeeking {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** Включить поведение вообще. false — зомби игнорируют свет. */
	public static final boolean ENABLED = true;

	/**
	 * Минимальная яркость источника (0–15), чтобы зомби его заметил.
	 * 12 = факел (14), светокамень (15), фонарь (15), костёр (15), морской фонарь (15).
	 * Светящийся лишайник (7) и свечи (до 12 при 4 свечах) — ниже/на границе.
	 * Поставь 15, если нужны только самые яркие блоки.
	 */
	public static final int MIN_LIGHT_LEVEL = 12;

	/**
	 * Как далеко зомби «ищет» свет по горизонтали (блоки от своей позиции).
	 * 60 = квадрат 121×121; по прямой до ~60 блоков, по диагонали до ~85.
	 * Ближайший подходящий блок в этом объёме побеждает.
	 * Больше радиус = тяжелее для сервера (много зомби без цели — не раздувай без нужды).
	 */
	public static final int SEARCH_RADIUS_HORIZONTAL = 60;

	/** Вверх и вниз от ног зомби (блоки): ±16, итого 33 этажа по высоте. */
	public static final int SEARCH_RADIUS_VERTICAL = 16;

	/**
	 * Требовать прямую видимость до света (не через стены).
	 * true — свет за стеной зомби не заметит (естественнее, меньше ложных путей).
	 * false — идут на любой свет в радиусе, даже за стеной.
	 */
	public static final boolean REQUIRE_LINE_OF_SIGHT = true;

	/** Пауза между поисками света, когда ничего не нашли (тики; 20 тиков = 1 сек). */
	public static final int SCAN_INTERVAL_TICKS = 40;

	/** Скорость движения к свету (1.0 = как обычный шаг зомби). */
	public static final double MOVE_SPEED = 1.0;

	/** Считать «пришёл», когда ближе этого расстояния до света (блоки). */
	public static final double ARRIVE_DISTANCE = 2.5;

	/** Сколько стоять у света (тики): 80 = 4 сек. */
	public static final int WAIT_AT_LIGHT_TICKS = 4 * 20;

	/**
	 * Ломают ли зомби свет, к которому пришли (если не видят игрока).
	 * Ломается любой свет, кроме лавы и светоягод; блоки из UNBREAKABLE_BLOCKS (бедрок, обсидиан) — нет.
	 * Работает только если зомби вообще умеет ломать блоки (см. BlockBreaking.isBreaker).
	 */
	public static final boolean BREAK_LIGHT = true;

	// Скорость ломания света отдельной константы не имеет: она такая же, как у обычных блоков
	// (BlockBreaking.SPEED_MULTIPLIER + твёрдость блока + инструмент в руке зомби).
	// Факел/лампа с твёрдостью 0 ломаются сразу, светокамень — за пару секунд.
	// Если зомби заметит игрока или жителя, пока ломает — бросит свет и пойдёт на цель.

	/** Сколько не возвращаться к уже посещённому свету (тики): 2400 = 2 минуты. */
	public static final int IGNORE_VISITED_LIGHT_TICKS = 2 * 60 * 20;

	/** Макс. время одного похода к свету (тики), чтобы не застрять навсегда: 600 = 30 сек. */
	public static final int MAX_WALK_TICKS = 30 * 20;

	/**
	 * Жители важнее света: если зомби «чувствует» жителя рядом — он бросает свет
	 * (не идёт к нему и не ломает) и идёт убивать/заражать жителя.
	 * false — как раньше: зомби без цели в первую очередь идёт на свет.
	 */
	public static final boolean VILLAGER_BEATS_LIGHT = true;

	/**
	 * Как далеко (блоки по горизонтали) зомби чувствует жителя. Сквозь стены тоже.
	 * 40 = чуть дальше ванильных 35. Не ставь больше SEARCH_RADIUS_HORIZONTAL — смысла нет.
	 * Больше число = зомби «видят» деревню издалека и чаще бросают свет ради неё.
	 */
	public static final double VILLAGER_SENSE_RADIUS = 40.0;

	/**
	 * Как далеко вверх/вниз (блоки) зомби чувствует жителя.
	 * В ваниле только ±4 — поэтому житель на втором этаже или в подвале зомби «не замечал» и он шёл ломать факел.
	 */
	public static final double VILLAGER_SENSE_VERTICAL = 16.0;

	/** Как часто (тики) зомби по пути к свету/у света проверяет, не появился ли рядом житель: 10 = полсекунды. */
	public static final int VILLAGER_RECHECK_TICKS = 10;

	// ========================================================================

	private LightSeeking() {
	}

	/** Может ли этот зомби вообще искать свет. */
	public static boolean isEligible(Zombie zombie) {
		if (!ENABLED) {
			return false;
		}
		// С какого дня зомби вообще бегут на свет (ThreatTable.LIGHT_SEEK_DAY).
		if (!ThreatScale.reached(zombie.level(), ThreatTable.LIGHT_SEEK_DAY)) {
			return false;
		}
		if (zombie instanceof Drowned || zombie instanceof ZombifiedPiglin) {
			return false;
		}
		if (zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		if (zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return false;
		}
		// Маяк с jack o'lantern сам светит — не идёт гасить факелы.
		if (JackLanternBeacon.skipsLightSeeking(zombie)) {
			return false;
		}
		return true;
	}

	/**
	 * Если рядом есть живой житель (или странствующий торговец — как в ваниле) — сделать его целью зомби.
	 * Возвращает true, если цель назначена (значит, свет сейчас не интересен).
	 * Дальше зомби сам идёт к жителю обычной атакой (а двери/блоки на пути ломает BreakToTargetGoal).
	 */
	public static boolean targetNearbyVillager(Zombie zombie) {
		if (!VILLAGER_BEATS_LIGHT) {
			return false;
		}
		AABB box = zombie.getBoundingBox().inflate(VILLAGER_SENSE_RADIUS, VILLAGER_SENSE_VERTICAL, VILLAGER_SENSE_RADIUS);
		AbstractVillager best = null;
		double bestDistSqr = Double.MAX_VALUE;
		for (AbstractVillager villager : zombie.level().getEntitiesOfClass(AbstractVillager.class, box, v -> v.isAlive())) {
			double distSqr = zombie.distanceToSqr(villager);
			if (distSqr < bestDistSqr) {
				best = villager;
				bestDistSqr = distSqr;
			}
		}
		if (best == null) {
			return false;
		}
		zombie.setTarget(best);
		return true;
	}

	/** Блок — подходящий источник света? Лава, светоягоды (пещерные лозы) — всегда нет. */
	public static boolean isAttractiveLight(BlockState state) {
		if (state.getLightEmission() < MIN_LIGHT_LEVEL) {
			return false;
		}
		if (state.getFluidState().is(FluidTags.LAVA)) {
			return false;
		}
		if (state.is(Blocks.LAVA_CAULDRON)) {
			return false;
		}
		// Светоягоды на лозах (яркость 14) — природный свет пещер, не «факел игрока».
		return !state.is(Blocks.CAVE_VINES) && !state.is(Blocks.CAVE_VINES_PLANT);
	}

	/** Ближайший подходящий свет вокруг зомби или null. {@code isIgnored} — уже посещённые позиции. */
	@Nullable
	public static BlockPos findNearestLight(Zombie zombie, java.util.function.Predicate<BlockPos> isIgnored) {
		Level level = zombie.level();
		BlockPos origin = zombie.blockPosition();
		int rh = SEARCH_RADIUS_HORIZONTAL;
		int rv = SEARCH_RADIUS_VERTICAL;

		BlockPos best = null;
		double bestDistSqr = Double.MAX_VALUE;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int dx = -rh; dx <= rh; dx++) {
			for (int dz = -rh; dz <= rh; dz++) {
				cursor.set(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
				// Не трогаем незагруженные чанки (иначе сервер начнёт их грузить).
				if (!level.hasChunk(SectionPos.blockToSectionCoord(cursor.getX()), SectionPos.blockToSectionCoord(cursor.getZ()))) {
					continue;
				}
				for (int dy = -rv; dy <= rv; dy++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					BlockState state = level.getBlockState(cursor);
					if (!isAttractiveLight(state)) {
						continue;
					}
					double distSqr = zombie.distanceToSqr(cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5);
					if (distSqr >= bestDistSqr) {
						continue;
					}
					BlockPos pos = cursor.immutable();
					if (isIgnored.test(pos)) {
						continue;
					}
					if (REQUIRE_LINE_OF_SIGHT && !canSee(zombie, pos)) {
						continue;
					}
					best = pos;
					bestDistSqr = distSqr;
				}
			}
		}
		return best;
	}

	/** Луч от глаз зомби до центра блока света: не перекрыт другим блоком. */
	private static boolean canSee(Zombie zombie, BlockPos lightPos) {
		Vec3 from = zombie.getEyePosition();
		Vec3 to = Vec3.atCenterOf(lightPos);
		BlockHitResult hit = zombie.level().clip(new ClipContext(
				from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, zombie));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(lightPos);
	}
}
