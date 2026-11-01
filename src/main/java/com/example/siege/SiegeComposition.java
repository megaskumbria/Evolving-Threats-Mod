package com.example.siege;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Состав осады: кто в ней участвует и в каких долях. Выбирается один раз, когда осада начинается,
 * и не меняется до рассвета (хранится в {@link SiegeData#shares}).
 *
 * Две фракции, они НИКОГДА не смешиваются в одной осаде:
 *   НЕЖИТЬ И ТВАРИ — зомби, скелеты, иссушители, криперы, пауки, ведьмы, фантомы.
 *   ИЛЛАГЕРЫ       — разбойники, поборники, вызыватели, разорители.
 * Утопленников и слизней в осаде нет.
 * Иссушители — только если кто-то в мире уже был в Аду ({@link #WITHER_SKELETONS_REQUIRE_NETHER}).
 *
 * Как выбирается состав:
 *   1. Фракция. Иллагеры возможны с {@link ThreatTable#SIEGE_ILLAGER_DAY} дня, шанс {@link #ILLAGER_SIEGE_CHANCE}.
 *   2. Сколько видов участвует: 1, 2 или 3 (шансы ONE_KIND_CHANCE / TWO_KINDS_CHANCE, остальное — три).
 *   3. Какие именно виды: случайно, но у каждого вида свой «вес» (см. {@link Kind}).
 *      Иссушители в этот выбор не входят, пока в Аду никто не был.
 *   4. Доли выбранных видов — случайные, в сумме 100%. Например, 90% криперов и 10% зомби, или 100% зомби.
 *
 * Здесь же: какой моб соответствует какому виду и какие мобы получают «поход к игроку».
 */
public final class SiegeComposition {

	// ===================== SETTINGS (меняй эти числа) =====================

	/**
	 * Шанс, что осада иллагерская (когда они уже разрешены по дню, см. ThreatTable.SIEGE_ILLAGER_DAY).
	 * 0.25 = 25% (каждая четвёртая). 0 = иллагерских осад нет. 1 = только иллагерские.
	 */
	public static final float ILLAGER_SIEGE_CHANCE = 0.25f;

	/** Шанс, что в осаде участвует только один вид («100% криперов»). 0.15 = 15%. 0 = такого не бывает. */
	public static final float ONE_KIND_CHANCE = 0.15f;

	/**
	 * Шанс, что видов два. 0.50 = 50%. Оставшаяся доля (по умолчанию 35%) — три вида.
	 * Сумма ONE_KIND_CHANCE и TWO_KINDS_CHANCE не должна быть больше 1.0.
	 */
	public static final float TWO_KINDS_CHANCE = 0.50f;

	/**
	 * Насколько ровными получаются доли. Каждому выбранному виду бросается случайное число от 0 до 1,
	 * к нему прибавляется это значение, потом всё приводится к 100%.
	 * 0.0 = совсем случайно, возможна доля в 1–2%. 0.10 = самая маленькая доля обычно около 5%.
	 * 1.0 = доли почти равные.
	 */
	public static final float SHARE_FLOOR = 0.10f;

	/**
	 * true — скелеты-иссушители в случайной осаде только после похода в Ад.
	 * false — иссушители могут выпасть и без Ада, как остальные виды нежити.
	 * Команда /evolvingthreats siege start wither_skeleton это не смотрит: она для проверки.
	 */
	public static final boolean WITHER_SKELETONS_REQUIRE_NETHER = true;

	/**
	 * Достижение игры «Нам нужно забраться глубже»: выдаётся, когда игрок входит в Ад.
	 * По нему видно старые визиты, которые были до этого правила.
	 */
	private static final Identifier ENTER_NETHER = Identifier.withDefaultNamespace("story/enter_the_nether");

	/** Какая часть пауков — пещерные (ядовитые). 0.20 = 20%. */
	public static final float CAVE_SPIDER_FRACTION = 0.20f;

	/** На какой высоте над землёй появляются фантомы, от и до, в блоках. Они летают, на земле им делать нечего. */
	public static final int PHANTOM_HEIGHT_MIN = 10;
	public static final int PHANTOM_HEIGHT_MAX = 18;

	// ========================================================================

	/** Фракции осады. Внутри одной осады только одна. */
	public enum Faction {
		UNDEAD("нежить и твари"),
		ILLAGER("иллагеры");

		public final String label;

		Faction(String label) {
			this.label = label;
		}
	}

	/**
	 * Виды мобов осады. Для каждого вида два числа, их можно менять:
	 *   weight — как часто вид попадает в осаду (больше = чаще). Не доля внутри осады, а шанс быть выбранным.
	 *   cost   — «цена» одного моба в размере волны. Размер волны (WAVE_SIZE_MIN/MAX в SiegeWaves)
	 *            считается в этих единицах: при цене 3 в волну из 12 влезет 4 разорителя вместо 12.
	 *   max    — сколько таких мобов осады может быть живыми одновременно вокруг одного игрока.
	 *            Пока столько уже есть, новые этого вида не появляются (ни в волнах, ни из обычного спавна):
	 *            вместо них берётся другой вид из состава. 0 = без ограничения.
	 *            Вызыватели, разорители и ведьмы ограничены, потому что очень сильны.
	 */
	public enum Kind {
		//        фракция           вес   цена  max  название для команды /siege status
		ZOMBIE(Faction.UNDEAD, 3.0f, 1, 0, "зомби"),
		SKELETON(Faction.UNDEAD, 3.0f, 1, 0, "скелеты"),
		// Вес как у редких видов. В случайный состав попадает только после Ада, см. WITHER_SKELETONS_REQUIRE_NETHER.
		WITHER_SKELETON(Faction.UNDEAD, 1.0f, 1, 0, "иссушители"),
		CREEPER(Faction.UNDEAD, 2.0f, 1, 0, "криперы"),
		SPIDER(Faction.UNDEAD, 2.0f, 1, 0, "пауки"),
		WITCH(Faction.UNDEAD, 1.5f, 1, 6, "ведьмы"),
		PHANTOM(Faction.UNDEAD, 1.0f, 1, 0, "фантомы"),
		PILLAGER(Faction.ILLAGER, 3.0f, 1, 0, "разбойники"),
		VINDICATOR(Faction.ILLAGER, 3.0f, 1, 0, "поборники"),
		EVOKER(Faction.ILLAGER, 1.0f, 2, 3, "вызыватели"),
		RAVAGER(Faction.ILLAGER, 1.5f, 3, 3, "разорители");

		public final Faction faction;
		public final float weight;
		public final int cost;
		public final int max;
		public final String label;

		Kind(Faction faction, float weight, int cost, int max, String label) {
			this.faction = faction;
			this.weight = weight;
			this.cost = cost;
			this.max = max;
			this.label = label;
		}
	}

	/** Какое поведение «идти к игроку» нужно мобу. */
	public enum AiMode {
		/** Ничего (обычный моб, или зомби и скелет — у них своя связка в ZombieRaidGoalsMixin и AbstractSkeletonRaidGoalsMixin). */
		NONE,
		/** Только держать игрока целью. Фантомы сами летят к цели, отрезками их вести не нужно. */
		TARGET_ONLY,
		/** Цель и поход отрезками (как у зомби). */
		TARGET_AND_MARCH
	}

	private SiegeComposition() {
	}

	// ------------------------------------------------------------------
	// Выбор состава
	// ------------------------------------------------------------------

	/** Иллагерские осады уже разрешены по дню шкалы? */
	public static boolean illagersAllowed(Level level) {
		return ThreatScale.reached(level, ThreatTable.SIEGE_ILLAGER_DAY);
	}

	/**
	 * Иссушители уже можно выбирать в случайный состав?
	 * Смотрит память мира и, если её ещё нет, игроков онлайн: достижение «вход в Ад» или сам Ад.
	 * Находку запоминает. Пока false — в осаде будут другие виды нежити и тварей.
	 */
	public static boolean witherSkeletonsAllowed(ServerLevel level) {
		if (!WITHER_SKELETONS_REQUIRE_NETHER) {
			return true;
		}
		SiegeData data = SiegeData.get(level);
		if (data.netherVisited) {
			return true;
		}
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (playerVisitedNether(player)) {
				markNetherVisited(level);
				return true;
			}
		}
		return false;
	}

	/** Игрок зашёл в мир. Если он уже бывал в Аду, мир это запоминает. */
	public static void notePlayer(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || !playerVisitedNether(player)) {
			return;
		}
		markNetherVisited(level);
	}

	/**
	 * Кто-то вошёл в Ад. Хватает одного раза на весь мир: следующие осады уже могут быть с иссушителями.
	 * Повторный заход ничего не меняет.
	 */
	public static void markNetherVisited(ServerLevel level) {
		SiegeData data = SiegeData.get(level);
		if (data.netherVisited) {
			return;
		}
		data.netherVisited = true;
		data.setDirty();
	}

	/** Стоит в Аду или уже получил ванильное достижение за вход туда. */
	private static boolean playerVisitedNether(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (level.dimension() == Level.NETHER) {
			return true;
		}
		AdvancementHolder holder = level.getServer().getAdvancements().get(ENTER_NETHER);
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/**
	 * Выбирает новый состав.
	 *
	 * @param forced если не null — фракция задана принудительно (команда для проверки), день и шанс не смотрим
	 * @param witherSkeletonsAllowed иссушители входят в выбор. Команда одного вида сюда не попадает
	 * @return доли по порядку {@link Kind#values()}, в сумме 1.0
	 */
	public static float[] roll(RandomSource random, boolean illagersAllowed, boolean witherSkeletonsAllowed,
			@Nullable Faction forced) {
		Faction faction;
		if (forced != null) {
			faction = forced;
		} else {
			faction = illagersAllowed && random.nextFloat() < ILLAGER_SIEGE_CHANCE ? Faction.ILLAGER : Faction.UNDEAD;
		}

		List<Kind> pool = new ArrayList<>();
		for (Kind kind : Kind.values()) {
			if (kind.faction != faction) {
				continue;
			}
			// Без похода в Ад иссушители просто не участвуют в выборе. Остальные виды на месте.
			if (kind == Kind.WITHER_SKELETON && !witherSkeletonsAllowed) {
				continue;
			}
			pool.add(kind);
		}

		int count = Math.min(pool.size(), rollKindCount(random));
		float[] shares = new float[Kind.values().length];
		float sum = 0.0f;
		for (int i = 0; i < count; i++) {
			Kind kind = pickByWeight(pool, random);
			pool.remove(kind);
			float raw = Math.max(0.0f, SHARE_FLOOR) + random.nextFloat();
			shares[kind.ordinal()] = raw;
			sum += raw;
		}
		for (int i = 0; i < shares.length; i++) {
			shares[i] = sum > 0.0f ? shares[i] / sum : 0.0f;
		}
		return shares;
	}

	/** Состав из одного вида на 100%. Для команд проверки: /evolvingthreats siege start zombie. */
	public static float[] only(Kind kind) {
		float[] shares = new float[Kind.values().length];
		shares[kind.ordinal()] = 1.0f;
		return shares;
	}

	private static int rollKindCount(RandomSource random) {
		float roll = random.nextFloat();
		if (roll < ONE_KIND_CHANCE) {
			return 1;
		}
		if (roll < ONE_KIND_CHANCE + TWO_KINDS_CHANCE) {
			return 2;
		}
		return 3;
	}

	private static Kind pickByWeight(List<Kind> pool, RandomSource random) {
		float total = 0.0f;
		for (Kind kind : pool) {
			total += Math.max(0.0f, kind.weight);
		}
		if (total <= 0.0f) {
			return pool.get(random.nextInt(pool.size()));
		}
		float roll = random.nextFloat() * total;
		for (Kind kind : pool) {
			roll -= Math.max(0.0f, kind.weight);
			if (roll < 0.0f) {
				return kind;
			}
		}
		return pool.get(pool.size() - 1);
	}

	/** Состав сохранён и подходит под нынешний список видов (иначе его нужно выбрать заново). */
	public static boolean isValid(float[] shares) {
		if (shares == null || shares.length != Kind.values().length) {
			return false;
		}
		float sum = 0.0f;
		for (float share : shares) {
			sum += share;
		}
		return sum > 0.0f;
	}

	/**
	 * Состав идущей осады. Если его почему-то нет (например, осада началась в старом мире до этого обновления),
	 * выбирается сразу и запоминается.
	 */
	public static float[] current(ServerLevel level) {
		SiegeData data = SiegeData.get(level);
		if (!isValid(data.shares)) {
			data.shares = roll(level.getRandom(), illagersAllowed(level), witherSkeletonsAllowed(level), null);
			data.setDirty();
		}
		return data.shares;
	}

	/** Фракция состава или null, если состава нет. */
	public static @Nullable Faction factionOf(float[] shares) {
		if (!isValid(shares)) {
			return null;
		}
		for (Kind kind : Kind.values()) {
			if (shares[kind.ordinal()] > 0.0f) {
				return kind.faction;
			}
		}
		return null;
	}

	/** Случайный вид по долям. */
	public static Kind pickKind(float[] shares, RandomSource random) {
		if (!isValid(shares)) {
			return Kind.ZOMBIE;
		}
		float total = 0.0f;
		for (float share : shares) {
			total += Math.max(0.0f, share);
		}
		float roll = random.nextFloat() * total;
		Kind last = Kind.ZOMBIE;
		for (Kind kind : Kind.values()) {
			float share = Math.max(0.0f, shares[kind.ordinal()]);
			if (share <= 0.0f) {
				continue;
			}
			last = kind;
			roll -= share;
			if (roll < 0.0f) {
				return kind;
			}
		}
		return last;
	}

	/**
	 * Вид для нового моба с учётом ограничений {@link Kind#max}. Вокруг center считаются живые мобы осады этого вида.
	 * Если лимит вида исчерпан, берётся другой вид из состава. Если подходящих не нашлось
	 * (например, состав из одних вызывателей или одних ведьм) — запасной вид той же фракции: поборник или зомби.
	 * center == null — лимиты не проверяются.
	 */
	public static Kind pickKindLimited(ServerLevel level, float[] shares, RandomSource random, @Nullable BlockPos center) {
		Kind kind = pickKind(shares, random);
		if (center == null || !isFull(level, kind, center)) {
			return kind;
		}
		for (int attempt = 0; attempt < 8; attempt++) {
			Kind other = pickKind(shares, random);
			if (!isFull(level, other, center)) {
				return other;
			}
		}
		Faction faction = factionOf(shares);
		return faction == Faction.ILLAGER ? Kind.VINDICATOR : Kind.ZOMBIE;
	}

	/** Мобов этого вида вокруг center уже столько, сколько разрешает Kind.max. */
	private static boolean isFull(ServerLevel level, Kind kind, BlockPos center) {
		if (kind.max <= 0) {
			return false;
		}
		AABB area = new AABB(center).inflate(SiegeWaves.CONTROL_RADIUS);
		int alive = level.getEntitiesOfClass(Mob.class, area,
				mob -> SiegeWaves.isSiegeMob(mob) && kindOf(mob.getType()) == kind).size();
		return alive >= kind.max;
	}

	/** Текст для /evolvingthreats siege status: «нежить и твари: криперы 90%, зомби 10%». */
	public static String describe(float[] shares) {
		Faction faction = factionOf(shares);
		if (faction == null) {
			return "состав не выбран";
		}
		List<Kind> kinds = new ArrayList<>();
		for (Kind kind : Kind.values()) {
			if (shares[kind.ordinal()] > 0.0f) {
				kinds.add(kind);
			}
		}
		kinds.sort((a, b) -> Float.compare(shares[b.ordinal()], shares[a.ordinal()]));
		StringBuilder text = new StringBuilder(faction.label).append(": ");
		for (int i = 0; i < kinds.size(); i++) {
			if (i > 0) {
				text.append(", ");
			}
			Kind kind = kinds.get(i);
			text.append(kind.label).append(' ').append(Math.round(shares[kind.ordinal()] * 100.0f)).append('%');
		}
		return text.toString();
	}

	// ------------------------------------------------------------------
	// Какой моб какому виду соответствует
	// ------------------------------------------------------------------

	/** Вид моба или null, если он в осаде не участвует (корова, эндермен, зомби-пиглин и т. д.). */
	public static @Nullable Kind kindOf(EntityType<?> type) {
		if (type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK || type == EntityTypes.ZOMBIE_VILLAGER) {
			return Kind.ZOMBIE;
		}
		if (type == EntityTypes.SKELETON || type == EntityTypes.STRAY
				|| type == EntityTypes.BOGGED || type == EntityTypes.PARCHED) {
			return Kind.SKELETON;
		}
		if (type == EntityTypes.WITHER_SKELETON) {
			return Kind.WITHER_SKELETON;
		}
		if (type == EntityTypes.CREEPER) {
			return Kind.CREEPER;
		}
		if (type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER) {
			return Kind.SPIDER;
		}
		if (type == EntityTypes.WITCH) {
			return Kind.WITCH;
		}
		if (type == EntityTypes.PHANTOM) {
			return Kind.PHANTOM;
		}
		if (type == EntityTypes.PILLAGER) {
			return Kind.PILLAGER;
		}
		if (type == EntityTypes.VINDICATOR) {
			return Kind.VINDICATOR;
		}
		if (type == EntityTypes.EVOKER) {
			return Kind.EVOKER;
		}
		if (type == EntityTypes.RAVAGER) {
			return Kind.RAVAGER;
		}
		return null;
	}

	/** Входит ли этот моб в состав идущей осады (его вид есть в составе). */
	public static boolean fitsCurrent(ServerLevel level, EntityType<?> type) {
		Kind kind = kindOf(type);
		return kind != null && current(level)[kind.ordinal()] > 0.0f;
	}

	/**
	 * Конкретный тип моба для вида. Зомби и скелеты берутся из списка монстров биома
	 * (кадавры в пустыне, странники на снегу, болотные скелеты в болоте). Если позиции нет
	 * или в биоме таких нет — обычный зомби или скелет.
	 */
	public static EntityType<?> typeOf(Kind kind, ServerLevel level, @Nullable BlockPos pos) {
		return switch (kind) {
			case ZOMBIE -> biomePick(level, pos, Kind.ZOMBIE, EntityTypes.ZOMBIE);
			case SKELETON -> biomePick(level, pos, Kind.SKELETON, EntityTypes.SKELETON);
			case WITHER_SKELETON -> EntityTypes.WITHER_SKELETON;
			case CREEPER -> EntityTypes.CREEPER;
			case SPIDER -> level.getRandom().nextFloat() < CAVE_SPIDER_FRACTION
					? EntityTypes.CAVE_SPIDER : EntityTypes.SPIDER;
			case WITCH -> EntityTypes.WITCH;
			case PHANTOM -> EntityTypes.PHANTOM;
			case PILLAGER -> EntityTypes.PILLAGER;
			case VINDICATOR -> EntityTypes.VINDICATOR;
			case EVOKER -> EntityTypes.EVOKER;
			case RAVAGER -> EntityTypes.RAVAGER;
		};
	}

	private static EntityType<?> biomePick(ServerLevel level, @Nullable BlockPos pos, Kind kind, EntityType<?> fallback) {
		if (pos == null) {
			return fallback;
		}
		WeightedList<MobSpawnSettings.SpawnerData> list = level.getChunkSource().getGenerator()
				.getMobsAt(level, level.structureManager(), MobCategory.MONSTER, pos);

		int total = 0;
		for (Weighted<MobSpawnSettings.SpawnerData> entry : list.unwrap()) {
			if (kindOf(entry.value().type()) == kind) {
				total += Math.max(0, entry.weight());
			}
		}
		if (total <= 0) {
			return fallback;
		}
		int roll = level.getRandom().nextInt(total);
		for (Weighted<MobSpawnSettings.SpawnerData> entry : list.unwrap()) {
			EntityType<?> type = entry.value().type();
			if (kindOf(type) != kind) {
				continue;
			}
			roll -= Math.max(0, entry.weight());
			if (roll < 0) {
				return type;
			}
		}
		return fallback;
	}

	/**
	 * Подмена обычного ночного спавна во время осады: игра выбрала какого-то монстра,
	 * вместо него ставится моб из состава осады (по долям). Так обычный спавн не разбавляет осаду чужими.
	 */
	public static EntityType<?> swapForSiege(ServerLevel level, @Nullable BlockPos feet) {
		Kind kind = pickKindLimited(level, current(level), level.getRandom(), feet);
		return typeOf(kind, level, feet);
	}

	// ------------------------------------------------------------------
	// Поход к игроку
	// ------------------------------------------------------------------

	/**
	 * Какое поведение осады навесить на моба при его создании. Зомби и скелеты сюда не попадают:
	 * у них своя связка (ZombieRaidGoalsMixin, AbstractSkeletonRaidGoalsMixin).
	 */
	public static AiMode aiModeOf(Mob mob) {
		Kind kind = kindOf(mob.getType());
		if (kind == null) {
			return AiMode.NONE;
		}
		return switch (kind) {
			case ZOMBIE, SKELETON, WITHER_SKELETON -> AiMode.NONE;
			case PHANTOM -> AiMode.TARGET_ONLY;
			default -> AiMode.TARGET_AND_MARCH;
		};
	}
}
