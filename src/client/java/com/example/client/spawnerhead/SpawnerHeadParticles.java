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
 * Дым и огонь над спавнером на голове — как у блока spawner в ванили (clientTick спawner).
 * Только клиент; на сервере ничего не шлём.
 */
public final class SpawnerHeadParticles {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** false — без частиц (спавнер на голове только визуально как предмет). */
	public static final boolean ENABLED = true;

	/**
	 * Частиц не рисуем, если игрок дальше (блоки). Как у спawner: эффект только рядом.
	 * 16.0 — как ванильный mob spawner.
	 */
	public static final double NEAR_PLAYER_RANGE = 16.0;

	/**
	 * Сколько «попыток» частиц за тик на одного моба (0–3). 1 ≈ как один тик спawner.
	 * Больше — гуще дым, но чуть тяжелее для FPS при многих носителях.
	 */
	public static final int PARTICLE_BURSTS_PER_TICK = 1;

	/** Разброс частиц внутри «кубика» над головой (блоки). 0.5 ≈ как random внутри блока spawner. */
	public static final double HEAD_PARTICLE_SPREAD = 0.45;

	/** Смещение центра эффекта вверх от ног моба (блоки). Подстрой, если частицы не над спавнером. */
	public static final double HEAD_CENTER_ABOVE_FEET = 1.55;

	/**
	 * То же для скелета-иссушителя: он выше обычного скелета.
	 * 2.20 — дым у блока на голове, а не на груди.
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

	/** Как {@code BaseSpawner} на клиенте: smoke + flame в случайной точке внутри куба. */
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
