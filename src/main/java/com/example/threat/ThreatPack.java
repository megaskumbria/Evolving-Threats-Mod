package com.example.threat;

import com.example.siege.SiegeNight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Больше мобов по мере роста шкалы: к появившемуся ночью зомби или скелету иногда добавляются ещё несколько
 * таких же (с обычной бронёй, оружием и чарами по правилам мода). Метка в mobs.txt: РАСТЁТ.
 *
 * <p>Как это работает: вызывается из ZombieMixin и SkeletonMixin при естественном спавне. Игра сама спавнит
 * зомби группами, а мы с шансом добавляем к каждому такому мобу ещё 1–3 рядом. В начале игры шанс нулевой,
 * к 100-му дню (и особенно в полнолуние) он доходит до {@link #EXTRA_CHANCE_END}. Дополнительные мобы
 * сами новых не порождают, мини-боссов среди них не бывает (они не «естественные»).
 *
 * <p>Не работает: днём (если включено {@link #ONLY_AT_NIGHT}), не в верхнем мире, в ночь осады
 * (у неё свои волны), для хасков, утопленников, жителей-зомби, детёнышей, странников и иссушителей.
 */
public final class ThreatPack {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — дополнительные мобы не появляются совсем. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что к появившемуся зомби или скелету добавятся ещё мобы, на 100-й день и полнолуние.
	 * 0.40 = 40%. Игра спавнит зомби группами, и шанс считается для каждого из них отдельно, поэтому
	 * реально мобов становится заметно больше. В начале игры шанс 0 и растёт вместе со шкалой.
	 * 0 = дополнительных мобов нет.
	 */
	public static final float EXTRA_CHANCE_END = 0.40f;

	/**
	 * Сколько мобов максимум добавляется за один раз: в начале игры и на 100-й день.
	 * Берётся случайное число от 1 до этого максимума. 1 → 3 значит: сначала всегда по одному,
	 * к концу шкалы по одному-трём.
	 */
	public static final int EXTRA_COUNT_MAX_START = 1;
	public static final int EXTRA_COUNT_MAX_END = 3;

	/** true — только когда на улице темно (ночь, гроза). false — и днём в пещерах, и ночью везде. */
	public static final boolean ONLY_AT_NIGHT = true;

	/** Как далеко от основного моба (в блоках) появляются добавленные. */
	public static final double SPAWN_RADIUS_MIN = 1.5;
	public static final double SPAWN_RADIUS_MAX = 4.5;

	/** Сколько раз пробуем найти свободное место с землёй под ногами для каждого добавленного моба. */
	public static final int PLACEMENT_TRIES = 6;

	// ========================================================================

	private ThreatPack() {
	}

	/**
	 * Пробует добавить мобов к только что заспавненному зомби или скелету.
	 *
	 * @param mob    зомби или скелет, прошедший finalizeSpawn
	 * @param reason причина спавна: работает только NATURAL (так спавнит мобов сама игра ночью)
	 */
	public static void tryAddExtras(Mob mob, ServerLevel level, EntitySpawnReason reason) {
		if (!ENABLED || !ThreatScale.ENABLED || reason != EntitySpawnReason.NATURAL) {
			return;
		}
		if (level.dimension() != Level.OVERWORLD || (ONLY_AT_NIGHT && !level.isDarkOutside())) {
			return;
		}
		if (SiegeNight.isWindowOpen(level) || mob.isBaby()) {
			return;
		}
		EntityType<? extends Mob> type;
		if (mob.getType() == EntityTypes.ZOMBIE) {
			type = EntityTypes.ZOMBIE;
		} else if (mob.getType() == EntityTypes.SKELETON) {
			type = EntityTypes.SKELETON;
		} else {
			return;
		}

		// Шанс растёт от 0 (день 0) до EXTRA_CHANCE_END; число добавляемых — от 1..START до 1..END.
		if (mob.getRandom().nextFloat() >= ThreatScale.growingChance(level, EXTRA_CHANCE_END, 0.0f)) {
			return;
		}
		int maxExtras = Math.max(1, Math.round(ThreatScale.lerp(level, (float) EXTRA_COUNT_MAX_START, (float) EXTRA_COUNT_MAX_END)));
		int count = 1 + mob.getRandom().nextInt(maxExtras);
		for (int i = 0; i < count; i++) {
			spawnExtra(level, type, mob);
		}
	}

	private static void spawnExtra(ServerLevel level, EntityType<? extends Mob> type, Mob origin) {
		var random = origin.getRandom();
		for (int attempt = 0; attempt < PLACEMENT_TRIES; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = SPAWN_RADIUS_MIN + random.nextDouble() * (SPAWN_RADIUS_MAX - SPAWN_RADIUS_MIN);
			double x = origin.getX() + Math.cos(angle) * radius;
			double z = origin.getZ() + Math.sin(angle) * radius;
			double y = origin.getY();
			BlockPos feet = BlockPos.containing(x, y, z);
			// Под ногами должна быть твёрдая земля, иначе моб повиснет в воздухе.
			if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
				continue;
			}
			AABB box = type.getDimensions().makeBoundingBox(new Vec3(x, y, z));
			if (!level.noCollision(box) || level.containsAnyLiquid(box)) {
				continue;
			}
			Mob extra = type.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (extra == null) {
				return;
			}
			extra.snapTo(x, y, z, random.nextFloat() * 360.0f, 0.0f);
			// MOB_SUMMONED: снаряжение выдаётся как обычно, но мини-боссами такие мобы не становятся
			// и новых не порождают (ThreatPack работает только на NATURAL).
			extra.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.MOB_SUMMONED, null);
			level.addFreshEntity(extra);
			return;
		}
	}
}
