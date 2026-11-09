package com.example.spider;

import com.example.advancement.ModAdvancements;
import com.example.entity.SpiderRoleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Two rare variants of the normal spider (cave spiders and jockey spiders excluded):
 * <ul>
 *   <li><b>Weaver</b> — places cobweb under the player's feet (goal {@link com.example.entity.ai.SpiderWebWeaveGoal});</li>
 *   <li><b>Matriarch</b> — 40 HP spider, 4 hearts a bite, spawns 1–4 cave spiders nearby on spawn.</li>
 * </ul>
 * One roll on spawn: matriarch, else weaver, else swarm ({@link SpiderSwarm}), else normal spider.
 * Weaver cobweb does not decay on its own.
 * MOB_SUMMONED spiders stay normal — otherwise swarm extras would chain new swarms.
 */
public final class SpiderThreats {

	// ===================== SETTINGS (change these numbers) =====================

	/** Master switch. false — all spiders are normal. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a normal spider spawns as a WEAVER. 0.10 = 10%.
	 * Shared roll with matriarch: matriarch is checked first.
	 */
	public static final float WEAVER_SPAWN_CHANCE = 0.10f;

	/** Chance (0.0–1.0) for MATRIARCH. 0.04 = 4% (uncommon). */
	public static final float MATRIARCH_SPAWN_CHANCE = 0.04f;

	// --- Weaver ---

	/** Pause between webs: min and max seconds (random between). */
	public static final int WEB_COOLDOWN_MIN_SECONDS = 5;
	public static final int WEB_COOLDOWN_MAX_SECONDS = 9;

	/** If web placement failed (cell blocked), retry after this many seconds. */
	public static final int WEB_RETRY_SECONDS = 1;

	/** Range (blocks) at which the weaver places cobweb on the player. Requires line of sight. */
	public static final double WEB_MAX_DISTANCE = 12.0;

	/**
	 * Honor mobGriefing (/gamerule mobGriefing false):
	 * true — when the rule is off, weavers do not place cobweb.
	 */
	public static final boolean RESPECT_MOB_GRIEFING = true;

	// --- Matriarch ---

	/** Matriarch health (normal spider 16). 40 = 20 hearts. */
	public static final float MATRIARCH_MAX_HEALTH = 40.0f;

	/**
	 * Normal spider bite. Vanilla counts half-hearts: the game default is 2 = 1 heart.
	 * 4 = 2 hearts. Fixed from day 0; does not grow with the threat scale.
	 * Cave spiders are not changed. A matriarch overwrites this with {@link #MATRIARCH_ATTACK_DAMAGE}.
	 */
	public static final double SPIDER_ATTACK_DAMAGE = 4.0;

	/**
	 * Matriarch bite. 8 = 4 hearts. Fixed from day 0; does not grow with the threat scale.
	 * Higher hurts more per hit. Lower brings her back toward a normal spider.
	 */
	public static final double MATRIARCH_ATTACK_DAMAGE = 8.0;

	/** Cave spiders spawned near matriarch on spawn (inclusive range). */
	public static final int MATRIARCH_CAVE_SPIDERS_MIN = 1;
	public static final int MATRIARCH_CAVE_SPIDERS_MAX = 4;

	/** Radius (blocks) around matriarch for cave spider spawn. */
	public static final double MATRIARCH_MINION_RADIUS = 2.5;

	/** Matriarch scale: 1.0 = normal spider, 1.3 = visibly larger for combat recognition. */
	public static final double MATRIARCH_SCALE = 1.3;

	// ========================================================================

	public static final int ROLE_NONE = 0;
	public static final int ROLE_WEAVER = 1;
	public static final int ROLE_MATRIARCH = 2;

	private SpiderThreats() {
	}

	public static int getRole(Mob mob) {
		return mob instanceof SpiderRoleEntity data ? data.evolvingthreats$getSpiderRole() : ROLE_NONE;
	}

	public static boolean isWeaver(Mob mob) {
		return ENABLED && getRole(mob) == ROLE_WEAVER;
	}

	/** Called from spider finalizeSpawn (once when the mob enters the world). */
	public static void rollOnSpawn(Spider spider, ServerLevelAccessor level, EntitySpawnReason spawnReason) {
		if (!ENABLED || !(spider instanceof SpiderRoleEntity data) || !isEligible(spider)) {
			return;
		}
		// Swarm extras and other summoned spiders never become matriarch, weaver, or a new swarm.
		if (spawnReason == EntitySpawnReason.MOB_SUMMONED) {
			return;
		}
		float roll = spider.getRandom().nextFloat();
		if (roll < MATRIARCH_SPAWN_CHANCE) {
			// Without a real server level (chunk gen) skip matriarch — minions could not spawn.
			if (level instanceof ServerLevel serverLevel) {
				data.evolvingthreats$setSpiderRole(ROLE_MATRIARCH);
				applyMatriarch(spider, serverLevel);
			}
		} else if (roll < MATRIARCH_SPAWN_CHANCE + WEAVER_SPAWN_CHANCE) {
			data.evolvingthreats$setSpiderRole(ROLE_WEAVER);
		} else if (SpiderSwarm.ENABLED
				&& level instanceof ServerLevel serverLevel
				&& roll < MATRIARCH_SPAWN_CHANCE + WEAVER_SPAWN_CHANCE + SpiderSwarm.SPAWN_CHANCE) {
			// Chunk generation skipped too — no world to place the pack.
			SpiderSwarm.spawnPack(spider, serverLevel);
		}
	}

	/** Normal spider: not cave spider and not a jockey (rider on top or passenger). */
	private static boolean isEligible(Spider spider) {
		return !(spider instanceof CaveSpider) && !spider.isVehicle() && !spider.isPassenger();
	}

	private static void applyMatriarch(Spider spider, ServerLevel level) {
		spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(MATRIARCH_MAX_HEALTH);
		spider.setHealth(MATRIARCH_MAX_HEALTH);
		spider.getAttribute(Attributes.SCALE).setBaseValue(MATRIARCH_SCALE);
		applyBite(spider);

		int count = spider.getRandom().nextIntBetweenInclusive(MATRIARCH_CAVE_SPIDERS_MIN, MATRIARCH_CAVE_SPIDERS_MAX);
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			if (spawnCaveSpider(level, spider)) {
				spawned++;
			}
		}
		// The card is the pack appearing, not the big spider merely existing.
		if (spawned > 0) {
			ModAdvancements.onLittleOnes(level, spider.getX(), spider.getY(), spider.getZ());
		}
	}

	/**
	 * After the world loads a saved spider. Bite only — a wounded matriarch is not healed.
	 * A matriarch keeps 4 hearts; any other normal spider gets 2. Cave spiders stay at 1.
	 */
	public static void onRoleLoaded(Mob mob) {
		if (!(mob instanceof Spider spider) || spider instanceof CaveSpider) {
			return;
		}
		if (ENABLED && getRole(mob) == ROLE_MATRIARCH) {
			applyBite(spider);
			return;
		}
		applyBaseBite(spider);
	}

	/** Two hearts a bite for a normal spider, including a weaver. Cave spiders stay at one heart. */
	public static void applyBaseBite(Spider spider) {
		if (spider instanceof CaveSpider) {
			return;
		}
		setBite(spider, SPIDER_ATTACK_DAMAGE);
	}

	/** Four hearts a bite. Safe if the attribute is missing. */
	private static void applyBite(Spider spider) {
		setBite(spider, MATRIARCH_ATTACK_DAMAGE);
	}

	private static void setBite(Spider spider, double damage) {
		var attack = spider.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.setBaseValue(damage);
		}
	}

	/** @return true when a cave spider actually entered the world. */
	private static boolean spawnCaveSpider(ServerLevel level, Spider matriarch) {
		CaveSpider minion = EntityTypes.CAVE_SPIDER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (minion == null) {
			return false;
		}
		Vec3 pos = findMinionPos(level, matriarch);
		minion.snapTo(pos.x, pos.y, pos.z, matriarch.getRandom().nextFloat() * 360.0f, 0.0f);
		DifficultyInstance difficulty = level.getCurrentDifficultyAt(minion.blockPosition());
		minion.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, (SpawnGroupData) null);
		level.addFreshEntity(minion);
		return true;
	}

	private static Vec3 findMinionPos(ServerLevel level, Spider matriarch) {
		var dims = EntityTypes.CAVE_SPIDER.getDimensions();
		float half = dims.width() / 2.0f;
		for (int attempt = 0; attempt < 8; attempt++) {
			double x = matriarch.getX() + (matriarch.getRandom().nextDouble() * 2.0 - 1.0) * MATRIARCH_MINION_RADIUS;
			double z = matriarch.getZ() + (matriarch.getRandom().nextDouble() * 2.0 - 1.0) * MATRIARCH_MINION_RADIUS;
			double y = matriarch.getY();
			AABB box = new AABB(x - half, y, z - half, x + half, y + dims.height(), z + half);
			if (level.noCollision(box)) {
				return new Vec3(x, y, z);
			}
		}
		return matriarch.position();
	}

	public static int randomWebCooldownTicks(net.minecraft.util.RandomSource random) {
		int min = WEB_COOLDOWN_MIN_SECONDS * 20;
		int max = WEB_COOLDOWN_MAX_SECONDS * 20;
		return max <= min ? min : random.nextIntBetweenInclusive(min, max);
	}

	/** Whether cobweb placement is allowed under mobGriefing. */
	public static boolean webPlacementAllowed(ServerLevel level) {
		return !RESPECT_MOB_GRIEFING || level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	/**
	 * Places cobweb in the cell under the target's feet (if empty or replaceable, not fluid).
	 *
	 * @return true if cobweb was placed
	 */
	public static boolean placeWebUnderTarget(ServerLevel level, LivingEntity target) {
		BlockPos pos = target.blockPosition();
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.COBWEB)) {
			return false;
		}
		boolean free = state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
		if (!free) {
			return false;
		}
		BlockState web = Blocks.COBWEB.defaultBlockState();
		level.setBlockAndUpdate(pos, web);
		level.playSound(null, pos, web.getSoundType().getPlaceSound(),
				net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.0f);
		// Cobweb stays until broken (sword, shears, or mining).
		if (target instanceof ServerPlayer player) {
			ModAdvancements.onCobweb(player);
		}
		return true;
	}
}
