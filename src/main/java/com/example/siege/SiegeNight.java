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
 * «Осада» — редкая ночь, когда в верхнем мире бродят только мобы одной фракции
 * (нежить и твари либо иллагеры, см. {@link SiegeComposition}),
 * идут волнами издалека и охотятся на игрока до самого рассвета.
 *
 * Здесь расписание (когда осада), предупреждение, запрет сна и порталов, подмена обычного спавна.
 * Сами волны мобов — в {@link SiegeWaves}, состав и доли — в {@link SiegeComposition}. Эта механика не связана с «Рейдовым якорем» (raid/RaidAnchor).
 *
 * Этапы одной осады:
 *   1. Утром за день до осады — туманный намёк в чат (необязательно).
 *   2. За 2 минуты до ночи — страшное сообщение, колокол и стоны вдали. С этого момента
 *      нельзя спать и входить в порталы в Ад и Энд.
 *   3. Ночь — идут волны. Мини-боссов и особых событий мода в это время нет.
 *   4. Рассвет — сообщение в чат, всё возвращается как было.
 *
 * Mixin и события здесь только вызывают методы этого класса.
 */
public final class SiegeNight {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — осад нет совсем. Уже идущая осада сразу заканчивается. */
	public static final boolean ENABLED = true;

	/**
	 * Самая короткая пауза между осадами, в игровых днях. 10 = десять дней.
	 * Один игровой день длится 20 минут. Меньше — осады чаще.
	 */
	public static final int DAYS_MIN = 10;

	/**
	 * Самая длинная пауза между осадами, в игровых днях. 15 = пятнадцать.
	 * Каждый раз берётся случайное число от DAYS_MIN до этого. Если поставить меньше минимума,
	 * игра возьмёт минимум.
	 */
	public static final int DAYS_MAX = 15;

	/**
	 * Насколько луна влияет на выбор дня осады (см. scheduleNext). Вес дня = сила луны в этой степени:
	 * полнолуние 1.0, убывающая 0.75, четверть 0.5, серп 0.25, новолуние 0 (осады не бывает никогда).
	 * 1.0 = влияние мягкое, 2.0 = полнолуние заметно предпочтительнее, 0 = луна не влияет (кроме новолуния).
	 * Первая осада на 25-й день (ThreatTable.SIEGE_FIRST_DAY) от луны не зависит.
	 */
	public static final double MOON_WEIGHT_POWER = 2.0;

	/**
	 * Во сколько начинается ночь осады, в тиках от начала дня. 13000 = как ночь в ванили.
	 * 12000 = закат, 18000 = полночь, 24000 = следующее утро.
	 */
	public static final int NIGHT_START_TICK = 13000;

	/** Во сколько осада заканчивается. 23000 = перед самым рассветом. Должно быть больше NIGHT_START_TICK. */
	public static final int DAWN_TICK = 23000;

	/**
	 * За сколько тиков до ночи предупреждать. 2400 = 2 минуты (20 тиков = 1 секунда).
	 * С этого момента запрещён сон и вход в порталы.
	 */
	public static final int WARNING_BEFORE_NIGHT_TICKS = 2400;

	/** true — утром за день до осады приходит туманный намёк без точной даты. */
	public static final boolean OMEN_ENABLED = true;

	/**
	 * За сколько дней до осады приходит намёк. 1 = утром накануне,
	 * то есть осада будет через одну спокойную ночь. 2 = за два дня.
	 */
	public static final int OMEN_DAYS_BEFORE = 1;

	/** Во сколько утром приходит намёк, в тиках от начала дня. 1000 = вскоре после рассвета. */
	public static final int OMEN_TICK = 1000;

	/** Сколько длится осада, если запустить её командой в тестовых целях, в тиках. 10000 = как ночь. */
	public static final int FORCED_NIGHT_LENGTH_TICKS = 10000;

	/**
	 * Как часто можно показывать игроку сообщение «портал не пускает», в тиках.
	 * Пока игрок стоит в портале, игра проверяет его каждый тик, без паузы чат заспамит.
	 */
	public static final int PORTAL_MESSAGE_COOLDOWN_TICKS = 200;

	// ---------- Звуки предупреждения (играют один раз, когда оно приходит) ----------

	/** false — предупреждение без звуков. */
	public static final boolean WARNING_SOUNDS_ENABLED = true;

	/** Сколько раз бьёт колокол и с какой паузой, в тиках. 60 = раз в три секунды. */
	public static final int BELL_TOLLS = 4;
	public static final int BELL_INTERVAL_TICKS = 60;

	/** Громкость и высота колокола. Громкость больше 1 слышна дальше. Высота ниже 1 = глуше и тяжелее. */
	public static final float BELL_VOLUME = 4.0f;
	public static final float BELL_PITCH = 0.7f;

	/** Сколько раз раздаётся далёкий стон, с какого тика и с какой паузой. */
	public static final int MOAN_COUNT = 8;
	public static final int MOAN_START_TICKS = 40;
	public static final int MOAN_INTERVAL_TICKS = 50;

	/** Громкость и высота стонов (высота берётся случайно между двумя числами). */
	public static final float MOAN_VOLUME = 3.0f;
	public static final float MOAN_PITCH_MIN = 0.5f;
	public static final float MOAN_PITCH_MAX = 0.8f;

	/** На каком расстоянии от игрока «звучит» колокол и стоны, в блоках. Звук идёт откуда-то издалека. */
	public static final double SOUND_DISTANCE_MIN = 20.0;
	public static final double SOUND_DISTANCE_MAX = 40.0;

	// ---------- Тексты ----------
	// Два списка на каждое событие: русский и английский.
	// Язык берётся из настроек игры у каждого игрока отдельно.
	// ru_ru (и любой ru_…) — русский список. Любой другой язык — английский.
	// Строки идут парами: первая русская = первая английская. Можно добавлять свои,
	// главное держать одинаковое число строк в паре списков.

	/** Утренний намёк. Без точной даты, на случайный выбор. */
	public static final String[] OMEN_MESSAGES = {
			"Что-то собирается там, за горизонтом.",
			"Сегодня никто не удивится, если ночь окажется долгой.",
	};
	public static final String[] OMEN_MESSAGES_EN = {
			"Something is gathering past the horizon.",
			"Nobody would be surprised if the night runs long.",
	};

	/** За 2 минуты до ночи. */
	public static final String[] WARNING_MESSAGES = {
			"Они идут. Спать нельзя.",
	};
	public static final String[] WARNING_MESSAGES_EN = {
			"They're coming. You can't sleep.",
	};

	/** Игрок пытается лечь в кровать. */
	public static final String[] SLEEP_BLOCKED_MESSAGES = {
			"Спать нельзя. Они рядом.",
			"Не ложись. Они уже близко.",
			"Сейчас не до сна.",
	};
	public static final String[] SLEEP_BLOCKED_MESSAGES_EN = {
			"You can't sleep. They're close.",
			"Don't lie down. They're already near.",
			"Not the time for sleep.",
	};

	/** Игрок стоит в портале в Ад или Энд. */
	public static final String[] PORTAL_BLOCKED_MESSAGES = {
			"Портал не пускает.",
			"Сейчас отсюда не уйти.",
	};
	public static final String[] PORTAL_BLOCKED_MESSAGES_EN = {
			"The portal won't let you through.",
			"You can't leave right now.",
	};

	/** Рассвет. */
	public static final String[] DAWN_MESSAGES = {
			"Рассвело. Они ушли, но думаю, они вернутся.",
	};
	public static final String[] DAWN_MESSAGES_EN = {
			"It's dawn. They left, but I think they'll be back.",
	};

	// ========================================================================

	/** Один игровой день. 24000 тиков = 20 минут. */
	private static final long TICKS_PER_DAY = 24000L;

	/** true с предупреждения и до рассвета. Запрещён сон, порталы, нет мини-боссов, идёт подмена спавна. */
	private static boolean windowOpen;

	/** true только когда ночь уже началась. Идут волны, обычные мобы мира тоже охотятся на игрока. */
	private static boolean nightActive;

	/** Начало звукового сценария предупреждения. -1 = не играет. */
	private static long soundStartGameTime = -1L;

	private static int soundMoansPlayed;

	private static final Map<UUID, Long> PORTAL_MESSAGE_AT = new HashMap<>();

	private SiegeNight() {
	}

	// ------------------------------------------------------------------
	// Вопросы «идёт ли осада», их задают mixin и другие механики мода
	// ------------------------------------------------------------------

	/**
	 * true — осада в разгаре: от предупреждения до рассвета, и только в верхнем мире.
	 * По этому признаку не работают мини-боссы и события мода, а обычный спавн подменяется.
	 */
	public static boolean isWindowOpen(Level level) {
		return windowOpen && level.dimension() == Level.OVERWORLD;
	}

	/** true — ночь осады уже началась (волны идут). Только верхний мир. */
	public static boolean isNight(Level level) {
		return nightActive && level.dimension() == Level.OVERWORLD;
	}

	/** Сброс при выключении сервера. Иначе в следующем мире на том же запуске игры осада «залипнет». */
	public static void clearRuntime() {
		windowOpen = false;
		nightActive = false;
		soundStartGameTime = -1L;
		soundMoansPlayed = 0;
		PORTAL_MESSAGE_AT.clear();
		SiegeWaves.clearRuntime();
	}

	// ------------------------------------------------------------------
	// Тик мира
	// ------------------------------------------------------------------

	/** Конец тика каждого мира. Работает только в верхнем. */
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
			// Проспали или перемотали время командой. Осаду пропускаем, назначаем следующую.
			scheduleNext(level, data, today);
			return;
		}
		if (peaceful) {
			// На мирной сложности мобов нет вообще. Ждём конца ночи, потом назначим новую.
			return;
		}
		begin(level, data, nightStart, end,
				clock >= nightStart ? SiegeData.PHASE_NIGHT : SiegeData.PHASE_WARNING, null, null);
	}

	/** Утренний намёк за день до осады (необязательно). */
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
	 * Начинает осаду: сразу с предупреждения или (если время уже ночное) с самой ночи.
	 * Здесь же выбирается состав осады (кто придёт и в каких долях), он держится до рассвета.
	 */
	private static void begin(ServerLevel level, SiegeData data, long nightStart, long end, int phase,
			@Nullable Faction forced, @Nullable Kind forcedKind) {
		data.phase = phase;
		data.nightStartClock = nightStart;
		data.endClock = end;
		// Команда проверки может задать один вид на 100%. Иначе состав случайный (фракция — по дню или по команде).
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

	/** Заканчивает осаду и назначает следующую через DAYS_MIN–DAYS_MAX дней. */
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
	 * Назначает следующую осаду.
	 * 1) Пока мир не дошёл до дня ThreatTable.SIEGE_FIRST_DAY (25), осада назначается ровно на этот день.
	 * 2) Дальше — через DAYS_MIN…DAYS_MAX дней, но день выбирается с учётом луны:
	 *    чем светлее луна в тот день, тем вероятнее (вес = сила луны в степени MOON_WEIGHT_POWER),
	 *    а в новолуние осады не бывает никогда.
	 */
	private static void scheduleNext(ServerLevel level, SiegeData data, long today) {
		if (ThreatScale.ENABLED && ThreatScale.day(level) < ThreatTable.SIEGE_FIRST_DAY) {
			// Осады считаются по дням мира, а 25-й день — это день ШКАЛЫ (от установки мода).
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

	/** Поднимает из кроватей всех, кто уже лёг: иначе можно проспать вечер до начала запрета. */
	private static void wakeSleepers(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (player.isSleeping()) {
				player.stopSleepInBed(true, true);
			}
		}
	}

	// ------------------------------------------------------------------
	// Запрет сна и порталов
	// ------------------------------------------------------------------

	/**
	 * Событие Fabric «игрок пытается лечь в кровать». OTHER_PROBLEM — отказ без собственной надписи
	 * над панелью быстрого доступа: сообщение мы отправляем сами, в чат.
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
	 * Вызывается из mixin на Entity.canUsePortal. true — портал игрока сейчас не пускает.
	 * Блокируется только вход из верхнего мира (Ад и Энд). Кто уже в Аду или Энде, может вернуться.
	 * Сообщение не чаще раза в PORTAL_MESSAGE_COOLDOWN_TICKS.
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
	// Подмена обычного спавна
	// ------------------------------------------------------------------

	/**
	 * Что ставить вместо моба, которого выбрала игра. Любой ночной монстр заменяется на моба из состава
	 * этой осады (по долям), см. {@link SiegeComposition}. Вызывается из NaturalMonsterSwap.
	 */
	public static EntityType<?> swapForSiege(ServerLevel level, @Nullable BlockPos feet) {
		return SiegeComposition.swapForSiege(level, feet);
	}

	// ------------------------------------------------------------------
	// Звуки предупреждения
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

	/** Играет звук для каждого игрока верхнего мира, из случайной точки в SOUND_DISTANCE блоках от него. */
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
	// Команды для проверки в игре (см. EvolvingThreatsCommands)
	// ------------------------------------------------------------------

	/** Короткая сводка для /evolvingthreats siege status. */
	public static String status(ServerLevel level) {
		SiegeData data = SiegeData.get(level);
		long clock = level.getOverworldClockTime();
		long today = Math.floorDiv(clock, TICKS_PER_DAY);
		String phase = switch (data.phase) {
			case SiegeData.PHASE_WARNING -> "предупреждение (ночь скоро)";
			case SiegeData.PHASE_NIGHT -> "идёт осада";
			default -> "ожидание";
		};
		String next = data.nextSiegeDay < 0
				? "ещё не назначена"
				: "день " + data.nextSiegeDay + " (через " + (data.nextSiegeDay - today) + " дн.)";
		String mobs = data.phase == SiegeData.PHASE_IDLE
				? ""
				: "; состав: " + SiegeComposition.describe(SiegeComposition.current(level));
		// Пока в Аду никто не был, в статусе видно, почему иссушителей в осаде нет.
		String wither = SiegeComposition.witherSkeletonsAllowed(level)
				? ""
				: "; иссушители: после похода в Ад";
		return "Siege: " + phase + "; сегодня день " + today + "; следующая: " + next + mobs + wither
				+ (ENABLED ? "" : " [ВЫКЛЮЧЕНА, ENABLED = false]");
	}

	/**
	 * Предупреждение сейчас: ночь осады начнётся через WARNING_BEFORE_NIGHT_TICKS.
	 * faction — принудительная фракция для проверки (null = выбрать по обычным правилам).
	 */
	public static void forceWarning(ServerLevel level, @Nullable Faction faction) {
		SiegeData data = SiegeData.get(level);
		long clock = level.getOverworldClockTime();
		long start = clock + Math.max(0, WARNING_BEFORE_NIGHT_TICKS);
		begin(level, data, start, start + FORCED_NIGHT_LENGTH_TICKS, SiegeData.PHASE_WARNING, faction, null);
	}

	/** То же, но осада на 100% из одного вида. Для проверки в игре. */
	public static void forceWarning(ServerLevel level, Kind kind) {
		SiegeData data = SiegeData.get(level);
		long clock = level.getOverworldClockTime();
		long start = clock + Math.max(0, WARNING_BEFORE_NIGHT_TICKS);
		begin(level, data, start, start + FORCED_NIGHT_LENGTH_TICKS, SiegeData.PHASE_WARNING, null, kind);
	}

	/** Ночь осады сразу, без предупреждения. Днём зомби и скелеты на солнце горят: это не ошибка. */
	public static void forceStart(ServerLevel level, @Nullable Faction faction) {
		SiegeData data = SiegeData.get(level);
		long clock = level.getOverworldClockTime();
		begin(level, data, clock, clock + FORCED_NIGHT_LENGTH_TICKS, SiegeData.PHASE_NIGHT, faction, null);
	}

	/** Ночь осады сразу, на 100% из одного вида. День и шанс фракции не смотрим. */
	public static void forceStart(ServerLevel level, Kind kind) {
		SiegeData data = SiegeData.get(level);
		long clock = level.getOverworldClockTime();
		begin(level, data, clock, clock + FORCED_NIGHT_LENGTH_TICKS, SiegeData.PHASE_NIGHT, null, kind);
	}

	/** Прекращает осаду сейчас и назначает следующую по обычному расписанию. */
	public static void forceStop(ServerLevel level) {
		SiegeData data = SiegeData.get(level);
		finish(level, data, level.getOverworldClockTime(), false);
		windowOpen = false;
		nightActive = false;
	}

	/**
	 * Пересчитывает расписание осады по обычным правилам (например, после /evolvingthreats day set).
	 * Если осада идёт прямо сейчас — ничего не меняет.
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
	 * Назначает осаду через указанное число игровых дней (0 = в ночь текущего дня).
	 * false — сейчас осада идёт, сначала останови её командой stop.
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
	// Сообщения
	// ------------------------------------------------------------------

	/** true — в настройках игры выбран русский (ru_ru и любой другой ru_…). */
	private static boolean prefersRussian(ServerPlayer player) {
		String language = player.clientInformation().language();
		return language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("ru");
	}

	/** Одно сообщение этому игроку, на его языке. Пустой список заменяется вторым. */
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
	 * Всем игрокам сервера, даже тем, кто сейчас в Аду.
	 * Один и тот же вариант, но каждый читает его на своём языке.
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
