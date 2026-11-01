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
 The composition of the siege: who participates in it and in what proportions. It comes out once the siege begins.
 * and does not change until dawn (stored at {@link SiegeData#shares}).
 *
 Two factions, they never mix in the same siege:
 Undead and creatures - zombies, skeletons, dehumidifiers, crypers, spiders, witches, phantoms.
 Illaghers - robbers, champions, summoners, destroyers.
 There are no drowned or slugs under siege.
 Extinguishers - only if someone in the world has already been to Hell ({@link #WITHER SKELETONS REQUIRE NETHER}).
 *
 * How composition is selected:
 1 Faction. Illagers are possible with {@link ThreatTable#SIEGE ILLAGER DAY} of the day, chance {@link #ILLAGER SIEGE CHANCE}.
 2. How many species are involved: 1, 2 or 3 (the odds are ONE KIND CHANCE/TWO KINDS CHANCE, the rest are three).
 3. Which species: randomly, but each species has its own "weight" (see {@link Kind}).
 Exciters are not included in this choice until no one has been to Hell.
 * 4. The shares of selected species are random, in the amount of 100%. For example, 90% of crypers and 10% of zombies or 100% of zombies.
 *
 * Here: which mob corresponds to which species and which mobs receive a “call to the player”.
 */
public final class SiegeComposition {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * Chance that the siege is Illagher (when they are already resolved by day, see ThreatTable.SIEGE ILLAGER DAY).
	 0.25 = 25% (every fourth). 0 = there are no sieges. 1 = Illaghers only.
	 */
	public static final float ILLAGER_SIEGE_CHANCE = 0.25f;

	/*The chance that only one species is involved in the siege ("100% of crypers"). 0.15 = 15%. 0 = that doesn't happen. */
	public static final float ONE_KIND_CHANCE = 0.15f;

	/**
	 Chances are two. 0.50 = 50%. The remaining share (by default 35%) is three types.
	 The sum of ONE KIND CHANCE and TWO KINDS CHANCE must not exceed 1.0.
	 */
	public static final float TWO_KINDS_CHANCE = 0.50f;

	/**
	 * How smooth the shares are. Each selected species is thrown a random number from 0 to 1,
	 This value is added to it, then everything is reduced to 100%.
	 * 0.0 = quite accidentally, a share of 1-2% is possible. 0.10 = the smallest fraction is usually about 5%.
	 * 1.0 = shares almost equal.
	 */
	public static final float SHARE_FLOOR = 0.10f;

	/**
	 True: Exhauster skeletons in an accidental siege only after going to Hell.
	 False - the humidifiers can fall out without Hell, like the rest of the undead.
	 * The command /evolvingthreats siege start wither skeleton does not watch it: it is for verification.
	 */
	public static final boolean WITHER_SKELETONS_REQUIRE_NETHER = true;

	/**
	 Achievement of the game "We need to get deeper": is issued when the player enters Hell.
	 It shows old visits that were before this rule.
	 */
	private static final Identifier ENTER_NETHER = Identifier.withDefaultNamespace("story/enter_the_nether");

	/*What part of the spiders are cave (poisonous). 0.20 = 20%. */
	public static final float CAVE_SPIDER_FRACTION = 0.20f;

	/*At what height above the ground appear phantoms, from and to, in blocks. They fly, they have nothing to do on the ground. */
	public static final int PHANTOM_HEIGHT_MIN = 10;
	public static final int PHANTOM_HEIGHT_MAX = 18;

	// ========================================================================

	/*Siege factions. There's only one siege inside. */
	public enum Faction {
		UNDEAD("undead"),
		ILLAGER("llagher");

		public final String label;

		Faction(String label) {
			this.label = label;
		}
	}

	/**
	 * Siege mobs. For each type of two numbers, they can be changed:
	 Weight – how often a species is under siege (more = more often). Not a share inside the siege, but a chance to be chosen.
	 Cost is the price of one mob in wave size. Wave size (WAVE SIZE MIN/MAX in SiegeWaves)
	 * is considered in these units: at the price of 3 in the wave of 12 fit 4 ruiners instead of 12.
	 max - how many such mobs of siege can be alive at the same time around one player.
	 While so many are already there, new of this species do not appear (neither in the waves nor from the usual spawn):
	 Instead of them, a different species is taken from the composition. 0 = without limitation.
	 Provocateurs, devastators and witches are limited because they are very strong.
	 */
	public enum Kind {
		//        fraction weight price max name for command /siege status
		ZOMBIE(Faction.UNDEAD, 3.0f, 1, 0, "zombie"),
		SKELETON(Faction.UNDEAD, 3.0f, 1, 0, "skeletons"),
		// Weight like rare species. In a random composition only gets after Hell, see WITHER SKELETONS REQUIRE NETHER.
		WITHER_SKELETON(Faction.UNDEAD, 1.0f, 1, 0, "dryer"),
		CREEPER(Faction.UNDEAD, 2.0f, 1, 0, "crypers"),
		SPIDER(Faction.UNDEAD, 2.0f, 1, 0, "spiders"),
		WITCH(Faction.UNDEAD, 1.5f, 1, 6, "witches"),
		PHANTOM(Faction.UNDEAD, 1.0f, 1, 0, "phantom"),
		PILLAGER(Faction.ILLAGER, 3.0f, 1, 0, "robber"),
		VINDICATOR(Faction.ILLAGER, 3.0f, 1, 0, "champion"),
		EVOKER(Faction.ILLAGER, 1.0f, 2, 3, "caller"),
		RAVAGER(Faction.ILLAGER, 1.5f, 3, 3, "ruiner");

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

	/*What behavior "go to the player" need mobu. */
	public enum AiMode {
		/*Nothing (a common mob, or zombie and skeleton – they have their own bundle in ZombieRaidGoalsMixin and AbstractSkeletonRaidGoalsMixin). */
		NONE,
		/*Only keep the player a target. Phantoms themselves fly to the target, they do not need to lead in segments. */
		TARGET_ONLY,
		/*Purpose and campaign segments (like zombies). */
		TARGET_AND_MARCH
	}

	private SiegeComposition() {
	}

	// ------------------------------------------------------------------
	// Choice of composition
	// ------------------------------------------------------------------

	/*Are Illagher sieges allowed on the day of the scale? */
	public static boolean illagersAllowed(Level level) {
		return ThreatScale.reached(level, ThreatTable.SIEGE_ILLAGER_DAY);
	}

	/**
	 * Can you select the scissors for random selection?
	 Watches the memory of the world and, if it does not yet exist, players online: achieving "entry into hell" or Hell itself.
	 * I remember the find. While false - under siege will be other species of undead and creatures.
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

	/*The player entered the world. If he's been to Hell before, the world remembers. */
	public static void notePlayer(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || !playerVisitedNether(player)) {
			return;
		}
		markNetherVisited(level);
	}

	/**
	 Someone has entered Hell. Enough once for the whole world: the next siege may already be with the dehumidifiers.
	 Re-entry doesn't change anything.
	 */
	public static void markNetherVisited(ServerLevel level) {
		SiegeData data = SiegeData.get(level);
		if (data.netherVisited) {
			return;
		}
		data.netherVisited = true;
		data.setDirty();
	}

	/*Stands in Hell or has already received a vanilla achievement for entering there. */
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
	 * Selects a new lineup.
	 *
	 * @param forced if not null - faction is forced (command to check), day and chance do not watch
	 * @param witherSkeletonsAllowed humidifiers are included in the selection. A team of one species doesn't get here.
	 * @return shares in order {@link Kind#values()}, in total 1. 0
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
			// Without going to Hell, the savages simply do not participate in the choice. The rest are in place.
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

	/** Composition of one species 100%. For verification teams: /evolvingthreats siege start zombie. */
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

	/*The composition is preserved and fits the current list of species (otherwise it must be selected again). */
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
	 * Composition of the ongoing siege. If for some reason it is not (for example, the siege began in the old world before this update),
	 * Selected immediately and remembered.
	 */
	public static float[] current(ServerLevel level) {
		SiegeData data = SiegeData.get(level);
		if (!isValid(data.shares)) {
			data.shares = roll(level.getRandom(), illagersAllowed(level), witherSkeletonsAllowed(level), null);
			data.setDirty();
		}
		return data.shares;
	}

	/** Composition fraction or null if no composition. */
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

	/** Random view by share. */
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
	 * View for the new mob subject to constraints {@link Kind#max} Around the center are considered live mobs of siege of this species.
	 If the species limit is exhausted, another species is taken from the composition. If you don't find the right one.
	 * (for example, a composition of one conjurer or one witch) - a reserve form of the same faction: a champion or a zombie.
	 * center = = null - limits are not checked.
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

	/*Mobs of this kind around the center is already as many as Kind.max allows. */
	private static boolean isFull(ServerLevel level, Kind kind, BlockPos center) {
		if (kind.max <= 0) {
			return false;
		}
		AABB area = new AABB(center).inflate(SiegeWaves.CONTROL_RADIUS);
		int alive = level.getEntitiesOfClass(Mob.class, area,
				mob -> SiegeWaves.isSiegeMob(mob) && kindOf(mob.getType()) == kind).size();
		return alive >= kind.max;
	}

	/** Text for /evolvingthreats siege status: undead and creatures: crypers 90%, zombies 10%. */
	public static String describe(float[] shares) {
		Faction faction = factionOf(shares);
		if (faction == null) {
			return "not selected";
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
	// Which mob corresponds to which species
	// ------------------------------------------------------------------

	/*A type of mob or null, if it is not under siege (cow, enderman, zombie piglin, etc.). */
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

	/*Is this mob part of the ongoing siege (its form is in the composition). */
	public static boolean fitsCurrent(ServerLevel level, EntityType<?> type) {
		Kind kind = kindOf(type);
		return kind != null && current(level)[kind.ordinal()] > 0.0f;
	}

	/**
	 * Specific type of mob for the species. Zombies and skeletons are taken from the list of biome monsters
	 * (husks in the desert, strangers in the snow, marsh skeletons in the swamp). If there is no position
	 Or in the biome there is no such thing - a normal zombie or skeleton.
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
	 Replacement of the usual night spawn during the siege: the game chose some monster,
	 * Instead of it, a mob is placed from the siege (in parts). So the usual spawn does not dilute the siege by strangers.
	 */
	public static EntityType<?> swapForSiege(ServerLevel level, @Nullable BlockPos feet) {
		Kind kind = pickKindLimited(level, current(level), level.getRandom(), feet);
		return typeOf(kind, level, feet);
	}

	// ------------------------------------------------------------------
	// Going to the player
	// ------------------------------------------------------------------

	/**
	 What behavior of the siege to hang on the mob at its creation. Zombies and skeletons are not included:
	 They have their own bundle (ZombieRaidGoalsMixin, AbstractSkeletonRaidGoalsMixin).
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
