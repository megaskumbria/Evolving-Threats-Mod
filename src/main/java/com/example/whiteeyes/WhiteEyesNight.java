package com.example.whiteeyes;

import com.example.siege.SiegeNight;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The night White Eyes comes, and the bed that will not take you.
 * No bell and no "someone is coming" line. The only text is his own reason, when he tries the bed.
 */
public final class WhiteEyesNight {

	/**
	 * First tick of the night he can appear, counted inside the day (0–23999).
	 * 13000 is the same start sieges use. Dawn is {@link #DAWN_TICK}.
	 */
	public static final int NIGHT_START_TICK = 13000;

	/** He will not appear after this tick. 23000 is dawn, same as a siege. */
	public static final int DAWN_TICK = 23000;

	/**
	 * What the player tells himself when the bed will not take him. Not a warning that something is walking over.
	 * Russian client language gets the first list. Everyone else gets the second.
	 */
	private static final String[] SLEEP_RU = {
			"Не могу уснуть. Не сегодня.",
			"Глаза не закрываются.",
			"Нет. Сегодня я не лягу.",
	};
	private static final String[] SLEEP_EN = {
			"I can't sleep. Not tonight.",
			"My eyes won't close.",
			"No. I won't lie down tonight.",
	};

	/** Already heard the line this night, so a stuck bed does not fill the chat. */
	private static final Set<UUID> TOLD = new HashSet<>();
	private static int toldOnDay = Integer.MIN_VALUE;

	private WhiteEyesNight() {
	}

	public static void tick(ServerLevel level) {
		if (!WhiteEyes.ENABLED || level.dimension() != Level.OVERWORLD) {
			return;
		}
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		if (!isVisitDay(level)) {
			TOLD.clear();
			return;
		}
		int day = ThreatScale.day(level);
		if (toldOnDay != day) {
			TOLD.clear();
			toldOnDay = day;
		}
		WhiteEyesData data = WhiteEyesData.get(level);
		if (data.yieldedForDay != day) {
			SiegeNight.yieldTonight(level);
			data.yieldedForDay = day;
			data.setDirty();
		}
		long time = Math.floorMod(level.getOverworldClockTime(), ThreatScale.TICKS_PER_DAY);
		boolean dark = level.isDarkOutside();
		if (dark) {
			for (ServerPlayer player : level.players()) {
				if (player.isSleeping()) {
					player.stopSleepInBed(true, true);
					tellOnce(player, level.getRandom());
				}
			}
		}
		if (data.lastVisitDay == day) {
			return;
		}
		if (time < NIGHT_START_TICK || time >= DAWN_TICK) {
			return;
		}
		if (alreadyAlive(level)) {
			data.lastVisitDay = day;
			data.setDirty();
			return;
		}
		ServerPlayer target = nearestSpawn(level);
		if (target == null) {
			return;
		}
		Vec3 pos = spawnSpot(level, target);
		if (pos == null || WhiteEyes.spawn(level, pos, target) == null) {
			return;
		}
		data.lastVisitDay = day;
		data.setDirty();
	}

	/**
	 * Bed click on a visit night. Null = the bed works as usual.
	 * OTHER_PROBLEM has no vanilla line, so the words above are the only answer.
	 */
	public static Player.BedSleepingProblem onAllowSleeping(Player player, BlockPos pos) {
		if (!WhiteEyes.ENABLED || !(player instanceof ServerPlayer serverPlayer)) {
			return null;
		}
		if (!(serverPlayer.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			return null;
		}
		if (level.getDifficulty() == Difficulty.PEACEFUL || !level.isDarkOutside() || !isVisitDay(level)) {
			return null;
		}
		tell(serverPlayer, level.getRandom());
		return Player.BedSleepingProblem.OTHER_PROBLEM;
	}

	/** Scale day 100, 200, 300… {@link ThreatTable#WHITE_EYES_DAY} is both the first night and the gap. */
	public static boolean isVisitDay(Level level) {
		int every = ThreatTable.WHITE_EYES_DAY;
		if (every <= 0) {
			return false;
		}
		int day = ThreatScale.day(level);
		return day >= every && day % every == 0;
	}

	/** True if one is already up near somebody in this world. A far unloaded one is not a second visit. */
	private static boolean alreadyAlive(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			AABB box = player.getBoundingBox().inflate(200.0);
			if (!level.getEntitiesOfClass(WhiteEyes.class, box, WhiteEyes::isAlive).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Who he walks toward when the night starts: the overworld player closest to world spawn. */
	private static ServerPlayer nearestSpawn(ServerLevel level) {
		BlockPos spawn = level.getRespawnData().pos();
		ServerPlayer best = null;
		double bestDist = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			double dist = player.distanceToSqr(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
			if (dist < bestDist) {
				best = player;
				bestDist = dist;
			}
		}
		return best;
	}

	/**
	 * Surface point {@link WhiteEyes#SPAWN_DISTANCE} blocks from the player.
	 * Tries a few directions. Null = no floor this tick; the night will try again.
	 */
	private static Vec3 spawnSpot(ServerLevel level, ServerPlayer player) {
		var random = level.getRandom();
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * WhiteEyes.SPAWN_DISTANCE);
			int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * WhiteEyes.SPAWN_DISTANCE);
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos feet = new BlockPos(x, y, z);
			if (WhiteEyes.hasRoom(level, feet)) {
				return new Vec3(x + 0.5, y, z + 0.5);
			}
		}
		return null;
	}

	private static void tellOnce(ServerPlayer player, RandomSource random) {
		if (!TOLD.add(player.getUUID())) {
			return;
		}
		tell(player, random);
	}

	private static void tell(ServerPlayer player, RandomSource random) {
		String[] lines = prefersRussian(player) ? SLEEP_RU : SLEEP_EN;
		String line = lines[random.nextInt(lines.length)];
		player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}

	private static boolean prefersRussian(ServerPlayer player) {
		String language = player.clientInformation().language();
		return language != null && language.toLowerCase(Locale.ROOT).startsWith("ru");
	}
}
