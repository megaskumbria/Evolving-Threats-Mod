package com.example.raid;

import com.example.siege.SiegeNight;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * Natural raid anchor spawn (no /etraid command).
 *
 * <p>When a normal zombie spawns on the surface at night in the Overworld,
 * it has a tiny chance to become an anchor instead. Minions appear around it immediately:
 * 12–15 servants (count and gear in {@link RaidAnchor}).
 * Called from ZombieMixin on zombie spawn — same pattern as the zombie overseer.
 *
 * <p>Chance does not grow by day (FIXED in mobs.txt): from day 80 it simply turns on.
 */
public final class RaidAnchorNatural {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — anchors no longer spawn naturally (/etraid still works). */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a surface-night zombie becomes an anchor.
	 * 0.001 = 0.1% (~one anchor per 1000 zombies).
	 * Overseer is 0.002 (0.2%). Raising this spawns anchors more often;
	 * {@link #MIN_DAYS_BETWEEN} still prevents back-to-back anchors.
	 */
	public static final float SPAWN_CHANCE = 0.001f;

	/**
	 * Minimum game days between two natural anchors for the whole world.
	 * 5 = next anchor not before 5 days later. 0 = no cooldown.
	 */
	public static final int MIN_DAYS_BETWEEN = 5;

	/**
	 * First day anchors can appear — from ThreatTable.RAID_ANCHOR_DAY (80).
	 * Change that table entry to move the threshold with other features.
	 */
	public static final int FROM_DAY = ThreatTable.RAID_ANCHOR_DAY;

	// ========================================================================

	private RaidAnchorNatural() {
	}

	/**
	 * Try to convert a freshly spawned zombie into an anchor.
	 *
	 * <p>All required: enabled; NATURAL spawn (not spawner, egg, or ritual);
	 * Overworld; dark outside; open sky (no caves or dungeons);
	 * plain zombie (not villager, husk, drowned, or baby); day ≥ {@link #FROM_DAY};
	 * not siege night; cooldown elapsed; chance roll succeeded.
	 *
	 * @return true — zombie became an anchor; skip normal equipment rolls
	 */
	public static boolean tryConvertOnSpawn(Zombie zombie, ServerLevel level, EntitySpawnReason reason) {
		if (!ENABLED || reason != EntitySpawnReason.NATURAL) {
			return false;
		}
		if (level.dimension() != Level.OVERWORLD || !level.isDarkOutside()) {
			return false;
		}
		if (zombie.getType() != EntityTypes.ZOMBIE || zombie.isBaby() || zombie.isPassenger()) {
			return false;
		}
		if (!ThreatScale.reached(level, FROM_DAY) || SiegeNight.isWindowOpen(level)) {
			return false;
		}
		BlockPos pos = zombie.blockPosition();
		if (!level.canSeeSky(pos)) {
			return false;
		}

		RaidAnchorData data = RaidAnchorData.get(level);
		int today = ThreatScale.day(level);
		// last > today when day was rewound by command — ignore cooldown then.
		boolean pauseOver = data.lastNaturalAnchorDay < 0
				|| data.lastNaturalAnchorDay > today
				|| today - data.lastNaturalAnchorDay >= MIN_DAYS_BETWEEN;
		if (!pauseOver) {
			return false;
		}
		if (zombie.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}

		ServerPlayer hunter = level.getNearestPlayer(zombie, RaidAnchor.HUNT_MAX_DISTANCE) instanceof ServerPlayer sp ? sp : null;
		RaidAnchor.convertToAnchor(level, zombie, hunter);

		data.lastNaturalAnchorDay = today;
		data.setDirty();
		return true;
	}
}
