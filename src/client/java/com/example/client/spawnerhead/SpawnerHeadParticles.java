package com.example.client.spawnerhead;

import com.example.spawnerhead.SpawnerHeadMob;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.phys.Vec3;

/**
 * Smoke and fire above the spawner on the head — like the spawner block in vanilla (clientTick spawner).
 * Only the client; we don't send anything on the server.
 */
public final class SpawnerHeadParticles {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — no particles (spavner on the head only visually as an object). */
	public static final boolean ENABLED = true;

	/**
	 * Do not draw particles if the player moves on (blocks). As in spawner: the effect is only nearby.
	 * 16.0 — as a vanilla mob spawner.
	 */
	public static final double NEAR_PLAYER_RANGE = 16.0;

	/**
	 * How many "attempts" of particles per tick per one mob (0–3). 1 ≈ as one tick spawner.
	 * More — thicker smoke, but slightly heavier for FPS with many carriers.
	 */
	public static final int PARTICLE_BURSTS_PER_TICK = 1;

	/** Scatter of particles inside the "cube" above the head (blocks). 0.5 ≈ as random inside the spawner block. */
	public static final double HEAD_PARTICLE_SPREAD = 0.45;

	/** Shifting the center of the effect upwards from the mob's legs (blocks). Adjust if the particles are not above the spavner. */
	public static final double HEAD_CENTER_ABOVE_FEET = 1.55;

	/**
	 * The same for the skeleton-dryer: it is higher than a normal skeleton.
	 * 2.20 — smoke at the block on the head, not on the chest.
	 */
	public static final double WITHER_HEAD_CENTER_ABOVE_FEET = 2.20;

	// ========================================================================

	private SpawnerHeadParticles() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SpawnerHeadParticles::onClientTick);
	}

	private static void onClientTick(Minecraft client) {
		if (!ENABLED || !SpawnerHeadMob.ENABLED) {
			return;
		}
		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			return;
		}

		Vec3 playerPos = client.player.position();
		double rangeSqr = NEAR_PLAYER_RANGE * NEAR_PLAYER_RANGE;

		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof Mob mob)) {
				continue;
			}
			RandomSource random = mob.getRandom();
			if (!SpawnerHeadMob.hasSpawnerOnHead(mob)) {
				continue;
			}
			if (mob.distanceToSqr(playerPos) > rangeSqr) {
				continue;
			}
			emitSpawnerLikeBurst(level, mob, random);
		}
	}

	/** Like the {@code BaseSpawner} on the client: smoke + flame at a random point inside the cube. */
	private static void emitSpawnerLikeBurst(ClientLevel level, Mob mob, RandomSource random) {
		double baseX = mob.getX();
		double aboveFeet = mob instanceof WitherSkeleton ? WITHER_HEAD_CENTER_ABOVE_FEET : HEAD_CENTER_ABOVE_FEET;
		double baseY = mob.getY() + aboveFeet;
		double baseZ = mob.getZ();

		for (int i = 0; i < PARTICLE_BURSTS_PER_TICK; i++) {
			double px = baseX + (random.nextDouble() - 0.5) * HEAD_PARTICLE_SPREAD;
			double py = baseY + (random.nextDouble() - 0.5) * HEAD_PARTICLE_SPREAD;
			double pz = baseZ + (random.nextDouble() - 0.5) * HEAD_PARTICLE_SPREAD;
			level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.0, 0.0);
			level.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, 0.0, 0.0);
		}
	}
}
