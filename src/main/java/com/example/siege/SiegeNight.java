package com.example.siege;

import com.example.siege.SiegeComposition.Faction;
import com.example.siege.SiegeComposition.Kind;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Siege — a rare night when only mobs of one faction roam the Overworld
 * (undead and monsters or illagers; see {@link SiegeComposition}),
 * arrive in waves from afar, and hunt the player until dawn.
 *
 * Schedule, warning, sleep and portal lockdown, and natural spawn replacement live here.
 * Wave spawning — {@link SiegeWaves}; composition and shares — {@link SiegeComposition}.
 * Not related to the raid anchor (RaidAnchor).
 *
 * One siege:
 *   1. Optional vague chat hint the morning before.
 *   2. Two minutes before night — warning message, bell, distant moans. Sleep and Nether/End portals blocked from then.
 *   3. Night — waves. No mod mini-bosses or special events during the window.
 *   4. Dawn — chat message; everything returns to normal.
 *
 * Mixins and events only call methods in this class.
 */
public final class SiegeNight {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — sieges disabled. An active siege ends immediately. */
	public static final boolean ENABLED = true;

	/**
	 * Shortest gap between sieges, in game days. 10 = ten days.
	 * One game day is 20 minutes. Lower = sieges more often.
	 */
	public static final int DAYS_MIN = 10;

	/**
	 * Longest gap between sieges, in game days. 15 = fifteen.
	 * Each time a random value from DAYS_MIN through this. If set below DAYS_MIN, DAYS_MIN is used.
	 */
	public static final int DAYS_MAX = 15;

	/**
	 * How strongly the moon affects which day is picked (see scheduleNext). Day weight = moon brightness raised to this power:
	 * full moon 1.0, waning 0.75, quarter 0.5, crescent 0.25, new moon 0 (no siege that day).
	 * 1.0 = mild effect, 2.0 = full moon much more likely, 0 = moon ignored (except new moon still blocks).
	 * First siege on ThreatTable.SIEGE_FIRST_DAY (25) ignores the moon.
	 */
	public static final double MOON_WEIGHT_POWER = 2.0;

	/**
	 * When siege night starts, ticks from start of day. 13000 = vanilla night.
	 * 12000 = sunset, 18000 = midnight, 24000 = next morning.
	 */
	public static final int NIGHT_START_TICK = 13000;

	/** When the siege ends. 23000 = just before dawn. Must be greater than NIGHT_START_TICK. */
	public static final int DAWN_TICK = 23000;

	/**
	 * Warn this many ticks before siege night. 2400 = 2 minutes (20 ticks = 1 second).
	 * Sleep and Nether/End portals are blocked from this moment until dawn.
	 */
	public static final int WARNING_BEFORE_NIGHT_TICKS = 2400;

	/** true — vague morning hint the day before the siege (no exact date). */
	public static final boolean OMEN_ENABLED = true;

	/**
	 * How many days before the siege the hint appears. 1 = morning before (one calm night left).
	 * 2 = two days ahead.
	 */
	public static final int OMEN_DAYS_BEFORE = 1;

	/** Morning omen time, ticks from start of day. 1000 = shortly after sunrise. */
	public static final int OMEN_TICK = 1000;

	/** Forced test siege length in ticks when started by command. 10000 ≈ one night. */
	public static final int FORCED_NIGHT_LENGTH_TICKS = 10000;

	/**
	 * Cooldown between "portal blocked" chat messages, in ticks.
	 * Portals are checked every tick; without this, chat spams while standing in a portal.
	 */
	public static final int PORTAL_MESSAGE_COOLDOWN_TICKS = 200;

	// ---------- Warning sounds (play once when warning starts) ----------

	/** false — warning with no sounds. */
	public static final boolean WARNING_SOUNDS_ENABLED = true;

	/** Bell toll count and interval in ticks. 60 = every three seconds. */
	public static final int BELL_TOLLS = 4;
	public static final int BELL_INTERVAL_TICKS = 60;

	/** Bell volume and pitch. Volume above 1 carries farther. Pitch below 1 = lower and heavier. */
	public static final float BELL_VOLUME = 4.0f;
	public static final float BELL_PITCH = 0.7f;

	/** Distant moan count, start tick, and interval. */
	public static final int MOAN_COUNT = 8;
	public static final int MOAN_START_TICKS = 40;
	public static final int MOAN_INTERVAL_TICKS = 50;

	/** Moan volume and pitch (pitch random between min and max). */
	public static final float MOAN_VOLUME = 3.0f;
	public static final float MOAN_PITCH_MIN = 0.5f;
	public static final float MOAN_PITCH_MAX = 0.8f;

	/** Distance in blocks where bell and moans seem to originate (random point around the player). */
	public static final double SOUND_DISTANCE_MIN = 20.0;
	public static final double SOUND_DISTANCE_MAX = 40.0;

	// ---------- Player-facing text ----------
	// Primary arrays are English. *_EN duplicates remain for locale helper compatibility.

	/** Morning hint. No exact date, random choice. */
	public static final String[] OMEN_MESSAGES = {
			"Something is gathering over the horizon.",
			"No one will be surprised if the night is long.",
	};
	public static final String[] OMEN_MESSAGES_EN = {
			"Something is gathering past the horizon.",
			"Nobody would be surprised if the night runs long.",
	};

	/** Two minutes before siege night. */
	public static final String[] WARNING_MESSAGES = {
			"They're coming. You can't sleep.",
	};
	public static final String[] WARNING_MESSAGES_EN = {
			"They're coming. You can't sleep.",
	};

	/** Player tries to sleep in a bed. */
	public static final String[] SLEEP_BLOCKED_MESSAGES = {
			"You can't sleep. They're close.",
			"Don't lie down. They're already near.",
			"Not the time for sleep.",
	};
	public static final String[] SLEEP_BLOCKED_MESSAGES_EN = {
			"You can't sleep. They're close.",
			"Don't lie down. They're already near.",
			"Not the time for sleep.",
	};

	/** Player in a Nether or End portal in the Overworld. */
	public static final String[] PORTAL_BLOCKED_MESSAGES = {
			"The portal won't let you through.",
			"You can't leave right now.",
	};
	public static final String[] PORTAL_BLOCKED_MESSAGES_EN = {
			"The portal won't let you through.",
			"You can't leave right now.",
	};

	/** Dawn after the siege. */
	public static final String[] DAWN_MESSAGES = {
			"It's dawn. They left, but I think they'll be back.",
	};
	public static final String[] DAWN_MESSAGES_EN = {
			"It's dawn. They left, but I think they'll be back.",
	};

	// ========================================================================

	/** One game day. 24000 ticks = 20 minutes. */
	private static final long TICKS_PER_DAY = 24000L;

	/** True from warning until dawn. No sleep, no portals, no mini-bosses, no spawning. */
	private static boolean windowOpen;

	/*True only when the night has already begun. The waves are coming, the usual mobs of the world are also hunting the player. */
	private static boolean nightActive;

	/** Beginning of the warning sound script. -1 = not playing. */
	private static long soundStartGameTime = -1L;

	private static int soundMoansPlayed;

	private static final Map<UUID, Long> PORTAL_MESSAGE_AT = new HashMap<>();

	private SiegeNight() {
	}

	// ------------------------------------------------------------------
	// Questions like “is there a siege” are asked by mixin and other fashion mechanics.
	// ------------------------------------------------------------------

	/**
	 True is a siege in full swing: from warning until dawn, and only in the upper world.
	 On this basis, mini-bosses and fashion events do not work, and the usual spawn is replaced.
	 */
	public static boolean isWindowOpen(Level level) {
		return windowOpen && level.dimension() == Level.OVERWORLD;
	}

	/*The night of the siege has already begun (the waves are coming). Just the upper world. */
	public static boolean isNight(Level level) {
		return nightActive && level.dimension() == Level.OVERWORLD;
	}

	/** Reset when the server is turned off. Otherwise, in the next world at the same launch of the game, the siege will stick. */
	public static void clearRuntime() {
		windowOpen = false;
		nightActive = false;
		soundStartGameTime = -1L;
		soundMoansPlayed = 0;
		PORTAL_MESSAGE_AT.clear();
		SiegeWaves.clearRuntime();
	}

	// ------------------------------------------------------------------
	// Tick of the world
	// ------------------------------------------------------------------

	/*The end of the tick of every world. It only works on the top. */
	public static void tick(ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}

		SiegeData data = SiegeData.get(level);
		long clock = level.getOverworldClockTime();
		long today = Math.floorDiv(clock, TICKS_PER_DAY);
		boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;

		if (!ENABLED) {
			if (data.phase != SiegeData.PHASE_IDLE) {
				finish(level, data, clock, false);
			}
			windowOpen = false;
			nightActive = false;
			return;
		}

		if (data.nextSiegeDay < 0) {
			scheduleNext(level, data, today);
		}

		switch (data.phase) {
			case SiegeData.PHASE_WARNING -> {
				if (peaceful || clock >= data.endClock) {
					finish(level, data, clock, !peaceful);
				} else if (clock >= data.nightStartClock) {
					data.phase = SiegeData.PHASE_NIGHT;
					data.setDirty();
					SiegeWaves.reset();
				}
			}
			case SiegeData.PHASE_NIGHT -> {
				if (peaceful || clock >= data.endClock) {
					finish(level, data, clock, !peaceful);
				}
			}
			default -> idleTick(level, data, clock, today, peaceful);
		}

		windowOpen = data.phase != SiegeData.PHASE_IDLE;
		nightActive = data.phase == SiegeData.PHASE_NIGHT;

		if (windowOpen) {
			tickWarningSounds(level);
		}
		if (nightActive) {
			long span = Math.max(1L, data.endClock - data.nightStartClock);
			double progress = Math.min(1.0, Math.max(0.0, (clock - data.nightStartClock) / (double) span));
			SiegeWaves.tick(level, progress);
		}
	}

	private static void idleTick(ServerLevel level, SiegeData data, long clock, long today, boolean peaceful) {
		long day = data.nextSiegeDay;
		long nightStart = day * TICKS_PER_DAY + NIGHT_START_TICK;
		long warnAt = nightStart - Math.max(0, WARNING_BEFORE_NIGHT_TICKS);
		long end = day * TICKS_PER_DAY + Math.max(NIGHT_START_TICK + 1, DAWN_TICK);

		if (clock < warnAt) {
			tickOmen(level, data, clock);
			return;
		}
		if (clock >= end) {
			// Overslept or rewinded time by the team. We skip the siege, set the next one.
			scheduleNext(level, data, today);
			return;
		}
		if (peaceful) {
			// There are no mobs on peaceful complexity at all. We wait for the end of the night, then we'll schedule a new one.
			return;
		}
		begin(level, data, nightStart, end,
				clock >= nightStart ? SiegeData.PHASE_NIGHT : SiegeData.PHASE_WARNING, null, null);
	}

	/*Morning hint the day before the siege (optional). */
	private static void tickOmen(ServerLevel level, SiegeData data, long clock) {
		if (!OMEN_ENABLED || data.omenSentFor == data.nextSiegeDay) {
			return;
		}
		long omenAt = (data.nextSiegeDay - Math.max(1, OMEN_DAYS_BEFORE)) * TICKS_PER_DAY + OMEN_TICK;
		if (clock < omenAt) {
			return;
		}
		data.omenSentFor = data.nextSiegeDay;
		data.setDirty();
		broadcast(level, OMEN_MESSAGES, OMEN_MESSAGES_EN);
	}

	/**
	 * Begins the siege: with warning immediately, or straight to night if it is already dark.
	 * Picks siege composition (mob mix) here; it holds until dawn.
	 */
	private static void begin(ServerLevel level, SiegeData data, long nightStart, long end, int phase,
			@Nullable Faction forced, @Nullable Kind forcedKind) {
		data.phase = phase;
		data.nightStartClock = nightStart;
		data.endClock = end;
		// Debug command can force one mob type at 100%. Otherwise random composition (faction by day or command).
		data.shares = forcedKind != null
				? SiegeComposition.only(forcedKind)
				: SiegeComposition.roll(level.getRandom(), SiegeComposition.illagersAllowed(level),
						SiegeComposition.witherSkeletonsAllowed(level), forced);
		data.setDirty();

		wakeSleepers(level);
		SiegeWaves.reset();

		if (phase == SiegeData.PHASE_WARNING) {
			broadcast(level, WARNING_MESSAGES, WARNING_MESSAGES_EN);
			if (WARNING_SOUNDS_ENABLED) {
				soundStartGameTime = level.getGameTime();
				soundMoansPlayed = 0;
			}
		}
	}

	/*Ends the siege and schedules the next one through DAYS MIN-DAYS MAX days. */
	private static void finish(ServerLevel level, SiegeData data, long clock, boolean announceDawn) {
		data.phase = SiegeData.PHASE_IDLE;
		data.shares = new float[0];
		scheduleNext(level, data, Math.floorDiv(clock, TICKS_PER_DAY));
		soundStartGameTime = -1L;
		if (announceDawn) {
			broadcast(level, DAWN_MESSAGES, DAWN_MESSAGES_EN);
		}
	}

	/**
	 He sets the next siege.
	 * 1) Until the world reaches ThreatTable.SIEGE FIRST DAY (25), the siege is scheduled for this day.
	 2) DAYS MIN...DAYS MAX days, but the day is chosen according to the moon:
	 The brighter the moon that day, the more likely it is (weight = moon power in MOON WEIGHT POWER)
	 There is never a siege on the new moon.
	 */
	private static void scheduleNext(ServerLevel level, SiegeData data, long today) {
		if (ThreatScale.ENABLED && ThreatScale.day(level) < ThreatTable.SIEGE_FIRST_DAY) {
			// Sieges are counted according to the days of peace, and the 25th day is Scale Day (from the fashion installation).
			data.nextSiegeDay = Math.max(today, ThreatScale.worldDayOfThreatDay(ThreatTable.SIEGE_FIRST_DAY));
			data.setDirty();
			return;
		}
		int min = Math.max(1, DAYS_MIN);
		int max = Math.max(min, DAYS_MAX);
		double[] weights = new double[max - min + 1];
		double total = 0.0;
		for (int i = 0; i < weights.length; i++) {
			long candidate = today + min + i;
			float moon = ThreatScale.moonLight(ThreatScale.moonPhaseOfDay(candidate));
			weights[i] = Math.pow(moon, MOON_WEIGHT_POWER);
			total += weights[i];
		}
		long chosen = today + min;
		if (total > 0.0) {
			double roll = level.getRandom().nextDouble() * total;
			for (int i = 0; i < weights.length; i++) {
				roll -= weights[i];
				if (roll < 0.0) {
					chosen = today + min + i;
					break;
				}
			}
		}
		data.nextSiegeDay = chosen;
		data.setDirty();
	}

	/*He lifts everyone who has already gone to bed: otherwise you can sleep in the evening before the ban begins. */
	private static void wakeSleepers(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (player.isSleeping()) {
				player.stopSleepInBed(true, true);
			}
		}
	}

	// ------------------------------------------------------------------
	// Banning sleep and portals
	// ------------------------------------------------------------------

	/**
	 Fabric Event Player Trying to Go to Bed OTHER PROBLEM - rejection without its own inscription
	 Over the quick access panel: we send the message ourselves to the chat.
	 */
	public static Player.BedSleepingProblem onAllowSleeping(Player player, BlockPos pos) {
		if (player.level() instanceof ServerLevel level && isWindowOpen(level)) {
			if (player instanceof ServerPlayer serverPlayer) {
				tell(serverPlayer, level.getRandom(), SLEEP_BLOCKED_MESSAGES, SLEEP_BLOCKED_MESSAGES_EN);
			}
			return Player.BedSleepingProblem.OTHER_PROBLEM;
		}
		return null;
	}

	/**
	 * Called from mixin on Entity.canUsePortal. True - the player portal is not allowed.
	 Only the entrance from the upper world is blocked (Hell and End). Those who are already in Hell or End may return.
	 * Not more than once in PORTAL MESSAGE COOLDOWN TICKS.
	 */
	public static boolean blocksPortal(net.minecraft.world.entity.Entity entity) {
		if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!isWindowOpen(level)) {
			return false;
		}
		long now = level.getGameTime();
		Long last = PORTAL_MESSAGE_AT.get(player.getUUID());
		if (last == null || now - last >= PORTAL_MESSAGE_COOLDOWN_TICKS) {
			PORTAL_MESSAGE_AT.put(player.getUUID(), now);
			tell(player, level.getRandom(), PORTAL_BLOCKED_MESSAGES, PORTAL_BLOCKED_MESSAGES_EN);
		}
		return true;
	}

	// ------------------------------------------------------------------
	// Replacement of the usual spawn
	// ------------------------------------------------------------------

	/**
	 What to put instead of the mob that the game chose? Any night monster is replaced by a mob.
	 * of this siege, see {@link SiegeComposition}. Called from NaturalMonsterSwap.
	 */
	public static EntityType<?> swapForSiege(ServerLevel level, @Nullable BlockPos feet) {
		return SiegeComposition.swapForSiege(level, feet);
	}

	// ------------------------------------------------------------------
	// Warning sounds
	// ------------------------------------------------------------------

	private static void tickWarningSounds(ServerLevel level) {
		if (soundStartGameTime < 0L) {
			return;
		}
		long elapsed = level.getGameTime() - soundStartGameTime;
		int bellInterval = Math.max(1, BELL_INTERVAL_TICKS);
		int moanInterval = Math.max(1, MOAN_INTERVAL_TICKS);

		long bellsEnd = (long) BELL_TOLLS * bellInterval;
		long moansEnd = MOAN_START_TICKS + (long) MOAN_COUNT * moanInterval;
		if (elapsed > Math.max(bellsEnd, moansEnd)) {
			soundStartGameTime = -1L;
			return;
		}

		if (elapsed >= 0 && elapsed % bellInterval == 0 && elapsed / bellInterval < BELL_TOLLS) {
			playDistant(level, SoundEvents.BELL_BLOCK, BELL_VOLUME, BELL_PITCH, BELL_PITCH);
		}
		if (elapsed >= MOAN_START_TICKS
				&& (elapsed - MOAN_START_TICKS) % moanInterval == 0
				&& soundMoansPlayed < MOAN_COUNT) {
			soundMoansPlayed++;
			SoundEvent[] moans = {
					SoundEvents.ZOMBIE_AMBIENT,
					SoundEvents.SKELETON_AMBIENT,
					SoundEvents.HUSK_AMBIENT,
					SoundEvents.ZOMBIE_VILLAGER_AMBIENT,
			};
			SoundEvent moan = moans[level.getRandom().nextInt(moans.length)];
			playDistant(level, moan, MOAN_VOLUME, MOAN_PITCH_MIN, MOAN_PITCH_MAX);
		}
	}

	/*Plays a sound for each player of the upper world, from a random point in the SOUND DISTANCE blocks away. */
	private static void playDistant(ServerLevel level, SoundEvent sound, float volume, float pitchMin, float pitchMax) {
		RandomSource random = level.getRandom();
		double min = Math.max(1.0, SOUND_DISTANCE_MIN);
		double max = Math.max(min, SOUND_DISTANCE_MAX);
		for (ServerPlayer player : level.players()) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = min + random.nextDouble() * (max - min);
			double x = player.getX() + Math.cos(angle) * distance;
			double z = player.getZ() + Math.sin(angle) * distance;
			float pitch = pitchMin + random.nextFloat() * Math.max(0.0f, pitchMax - pitchMin);
			level.playSound(null, x, player.getY(), z, sound, SoundSource.AMBIENT, volume, pitch);
		}
	}

	// ------------------------------------------------------------------
	// In-game testing commands (see EvolvingThreatsCommands)
	// ------------------------------------------------------------------

	/*Short summary for /evolvingthreats siege status. */
	public static String status(ServerLevel level) {
		SiegeData data = SiegeData.get(level);
		long clock = level.getOverworldClockTime();
		long today = Math.floorDiv(clock, TICKS_PER_DAY);
		String phase = switch (data.phase) {
			case SiegeData.PHASE_WARNING -> "warning (night soon)";
			case SiegeData.PHASE_NIGHT -> "siege in progress";
			default -> "idle";
		};
		String next = data.nextSiegeDay < 0
				? "not scheduled yet"
				: "day " + data.nextSiegeDay + " (in " + (data.nextSiegeDay - today) + " days)";
		String mobs = data.phase == SiegeData.PHASE_IDLE
				? ""
				: "; roster: " + SiegeComposition.describe(SiegeComposition.current(level));
		// If nobody has visited the Nether yet, explain why wither skeletons are absent from random rosters.
		String wither = SiegeComposition.witherSkeletonsAllowed(level)
				? ""
				: "; wither skeletons: after a Nether visit";
		return "Siege: " + phase + "; today day " + today + "; next: " + next + mobs + wither
				+ (ENABLED ? "" : " [DISABLED, ENABLED = false]");
	}

	/**
	 Warning now: the night of the siege will begin through WARNING BEFORE NIGHT TICKS.
	 * faction - a forced faction for verification (null = choose according to the usual rules).
	 */
	public static void forceWarning(ServerLevel level, @Nullable Faction faction) {
		SiegeData data = SiegeData.get(level);
		long clock = level.getOverworldClockTime();
		long start = clock + Math.max(0, WARNING_BEFORE_NIGHT_TICKS);
		begin(level, data, start, start + FORCED_NIGHT_LENGTH_TICKS, SiegeData.PHASE_WARNING, faction, null);
	}

	/*Same, but a siege 100% of the same species. To test the game. */
	public static void forceWarning(ServerLevel level, Kind kind) {
		SiegeData data = SiegeData.get(level);
		long clock = level.getOverworldClockTime();
		long start = clock + Math.max(0, WARNING_BEFORE_NIGHT_TICKS);
		begin(level, data, start, start + FORCED_NIGHT_LENGTH_TICKS, SiegeData.PHASE_WARNING, null, kind);
	}

	/*Night of siege immediately, without warning. During the day, zombies and skeletons burn in the sun: this is not a mistake. */
	public static void forceStart(ServerLevel level, @Nullable Faction faction) {
		SiegeData data = SiegeData.get(level);
		long clock = level.getOverworldClockTime();
		begin(level, data, clock, clock + FORCED_NIGHT_LENGTH_TICKS, SiegeData.PHASE_NIGHT, faction, null);
	}

	/*Night of siege at once, 100% of one species. The day and chance of the faction are not watched. */
	public static void forceStart(ServerLevel level, Kind kind) {
		SiegeData data = SiegeData.get(level);
		long clock = level.getOverworldClockTime();
		begin(level, data, clock, clock + FORCED_NIGHT_LENGTH_TICKS, SiegeData.PHASE_NIGHT, null, kind);
	}

	/*Ends the siege now and appoints the next one according to the usual schedule. */
	public static void forceStop(ServerLevel level) {
		SiegeData data = SiegeData.get(level);
		finish(level, data, level.getOverworldClockTime(), false);
		windowOpen = false;
		nightActive = false;
	}

	/**
	 Recalculates the schedule of the siege according to the usual rules (for example, after /evolvingthreats day set).
	 If the siege is going on right now, it doesn’t change anything.
	 */
	public static void reschedule(ServerLevel level) {
		SiegeData data = SiegeData.get(level);
		if (data.phase != SiegeData.PHASE_IDLE) {
			return;
		}
		long today = Math.floorDiv(level.getOverworldClockTime(), TICKS_PER_DAY);
		data.omenSentFor = -1L;
		scheduleNext(level, data, today);
	}

	/**
	 Assigns a siege through the specified number of game days (0 = on the night of the current day).
	 False - now the siege is on, first stop her command stop.
	 */
	public static boolean scheduleIn(ServerLevel level, int days) {
		SiegeData data = SiegeData.get(level);
		if (data.phase != SiegeData.PHASE_IDLE) {
			return false;
		}
		long today = Math.floorDiv(level.getOverworldClockTime(), TICKS_PER_DAY);
		data.nextSiegeDay = today + Math.max(0, days);
		data.omenSentFor = -1L;
		data.setDirty();
		return true;
	}

	// ------------------------------------------------------------------
	// Communications
	// ------------------------------------------------------------------

	/** True - in the game settings selected Russian (ru ru and any other en ..). */
	private static boolean prefersRussian(ServerPlayer player) {
		String language = player.clientInformation().language();
		return language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("ru");
	}

	/*One message to this player, in his language. The empty list is replaced by the second. */
	private static void tell(ServerPlayer player, RandomSource random, String[] russian, String[] english) {
		String[] lines = prefersRussian(player) ? russian : english;
		if (lines.length == 0) {
			lines = lines == russian ? english : russian;
		}
		if (lines.length == 0) {
			return;
		}
		player.sendSystemMessage(styled(lines[random.nextInt(lines.length)]));
	}

	private static Component styled(String text) {
		return Component.literal(text).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
	}

	/**
	 To all server players, even those in Hell.
	 The same version, but everyone reads it in their own language.
	 */
	private static void broadcast(ServerLevel level, String[] russian, String[] english) {
		int count = Math.max(russian.length, english.length);
		if (count == 0) {
			return;
		}
		int index = level.getRandom().nextInt(count);
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			String[] lines = prefersRussian(player) ? russian : english;
			if (lines.length == 0) {
				lines = lines == russian ? english : russian;
			}
			player.sendSystemMessage(styled(lines[index % lines.length]));
		}
	}
}
