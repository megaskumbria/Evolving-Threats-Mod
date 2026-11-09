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
 * Old clump: when a zombie or skeleton spawned at night, sometimes add 1–3 more right beside it.
 * Turned off. A bigger night is {@link HostileSpawnCap} instead: the game places mobs in its own
 * dark spots across the whole spawn area.
 *
 * <p>Set {@link #ENABLED} back to true only if the clump should return. Do not run both at once.
 * Extras never spawn extras, and mini-bosses never appear among them.
 *
 * <p>Skipped when on: daytime (if {@link #ONLY_AT_NIGHT}), non-Overworld, siege night,
 * husk, drowned, zombie villager, babies, stray, and wither skeleton.
 */
public final class ThreatPack {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — no extra mobs beside a spawn. The night population is {@link HostileSpawnCap}. */
	public static final boolean ENABLED = false;

	/**
	 * Chance (0.0–1.0) to add extras when a zombie or skeleton spawns, on day 100 at full moon.
	 * 0.40 = 40%. Vanilla spawns zombies in groups, and the roll is per mob, so
	 * packs feel noticeably larger. Starts at 0 and grows with the scale.
	 * 0 = no extras.
	 */
	public static final float EXTRA_CHANCE_END = 0.40f;

	/**
	 * Max extras added at once: at game start vs day 100.
	 * Random from 1 to this max. 1 → 3 means: always 1 early, up to 3 late.
	 */
	public static final int EXTRA_COUNT_MAX_START = 1;
	public static final int EXTRA_COUNT_MAX_END = 3;

	/** true — only when it is dark outside (night, storm). false — caves by day and everywhere at night. */
	public static final boolean ONLY_AT_NIGHT = true;

	/** How far from the origin mob (blocks) extras may appear. */
	public static final double SPAWN_RADIUS_MIN = 1.5;
	public static final double SPAWN_RADIUS_MAX = 4.5;

	/** Placement attempts per extra to find solid ground underfoot. */
	public static final int PLACEMENT_TRIES = 6;

	// ========================================================================

	private ThreatPack() {
	}

	/**
	 * Try to add mobs around a freshly spawned zombie or skeleton.
	 *
	 * @param mob    zombie or skeleton after finalizeSpawn
	 * @param reason spawn reason: only NATURAL (vanilla night spawns)
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

		// Chance grows from 0 (day 0) to EXTRA_CHANCE_END; extra count from 1..START to 1..END.
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
			// Need solid ground below or the mob floats.
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
			// MOB_SUMMONED: gear rolls normally, but these never become mini-bosses
			// and never trigger ThreatPack again (ThreatPack only hooks NATURAL).
			extra.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.MOB_SUMMONED, null);
			level.addFreshEntity(extra);
			return;
		}
	}
}
