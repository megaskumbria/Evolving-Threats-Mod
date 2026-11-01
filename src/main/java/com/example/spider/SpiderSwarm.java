package com.example.spider;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rare swarm: a normal spider sometimes brings several more of the same kind on spawn.
 * Not a matriarch or weaver. Cave spiders and jockey spiders are filtered out by
 * {@link SpiderThreats} before this runs.
 * Extra spiders use MOB_SUMMONED, so they never start a new swarm themselves.
 */
public final class SpiderSwarm {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — no swarms; spiders spawn as before. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a normal spider starts a swarm.
	 * 0.02 = 2%. Rarer than matriarch (4%): swarm adds several spiders at once.
	 * Single roll order: matriarch, then weaver, then swarm.
	 */
	public static final float SPAWN_CHANCE = 0.02f;

	/**
	 * Total spiders in the swarm, including the leader.
	 * 6–10 = six to ten spiders.
	 */
	public static final int PACK_MIN = 6;
	public static final int PACK_MAX = 10;

	/**
	 * How far from the first spider to search for a spot, in blocks.
	 * 2.0–6.0 = two to six blocks away so they do not stack inside each other.
	 */
	public static final double RADIUS_MIN = 2.0;
	public static final double RADIUS_MAX = 6.0;

	/**
	 * Random spot attempts per extra spider.
	 * No valid floor — that spider is skipped and the pack may be smaller.
	 */
	public static final int SPOT_TRIES = 16;

	/**
	 * Vertical search range (blocks up/down from the leader's feet).
	 * 3 = uneven caves. 0 = same Y only.
	 */
	public static final int VERTICAL_STEPS = 3;

	// ========================================================================

	private SpiderSwarm() {
	}

	/**
	 * Adds spiders until the pack reaches target size. The leader is already in the world.
	 * Called once on its spawn, only in a live world (not chunk generation).
	 */
	public static void spawnPack(Spider leader, ServerLevel level) {
		if (!ENABLED) {
			return;
		}
		int min = Math.max(1, PACK_MIN);
		int max = Math.max(min, PACK_MAX);
		int total = leader.getRandom().nextIntBetweenInclusive(min, max);
		int extras = total - 1;
		for (int i = 0; i < extras; i++) {
			spawnOne(leader, level);
		}
	}

	private static void spawnOne(Spider leader, ServerLevel level) {
		Vec3 pos = findOpenSpot(level, leader);
		if (pos == null) {
			return;
		}
		Spider spider = EntityTypes.SPIDER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (spider == null) {
			return;
		}
		spider.snapTo(pos.x, pos.y, pos.z, leader.getRandom().nextFloat() * 360.0f, 0.0f);
		DifficultyInstance difficulty = level.getCurrentDifficultyAt(spider.blockPosition());
		// MOB_SUMMONED: SpiderThreats leaves these normal — no new swarm.
		spider.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, (SpawnGroupData) null);
		level.addFreshEntity(spider);
	}

	/** Nearby spot with solid floor and spider body not inside a block — otherwise it suffocates. */
	private static Vec3 findOpenSpot(ServerLevel level, Spider leader) {
		var random = leader.getRandom();
		double min = RADIUS_MIN;
		double max = Math.max(min, RADIUS_MAX);
		int baseY = leader.blockPosition().getY();
		for (int attempt = 0; attempt < SPOT_TRIES; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double radius = min + random.nextDouble() * (max - min);
			double x = leader.getX() + Math.cos(angle) * radius;
			double z = leader.getZ() + Math.sin(angle) * radius;
			for (int dy = VERTICAL_STEPS; dy >= -VERTICAL_STEPS; dy--) {
				int y = baseY + dy;
				if (canStand(level, x, y, z)) {
					return new Vec3(x, y, z);
				}
			}
		}
		return null;
	}

	/**
	 * Spiders are short (under one block) but wide.
	 * We do not require two blocks of air like zombies — the entity AABB decides fit.
	 */
	private static boolean canStand(ServerLevel level, double x, int y, double z) {
		BlockPos feet = BlockPos.containing(x, y, z);
		if (!level.getWorldBorder().isWithinBounds(feet)) {
			return false;
		}
		if (!hasCollision(level, feet.below())) {
			return false;
		}
		if (level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(feet).is(FluidTags.WATER)) {
			return false;
		}
		AABB body = EntityTypes.SPIDER.getDimensions().makeBoundingBox(x, y, z);
		return level.noCollision(body);
	}

	private static boolean hasCollision(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.getCollisionShape(level, pos).isEmpty();
	}
}
