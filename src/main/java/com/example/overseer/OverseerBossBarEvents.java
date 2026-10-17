package com.example.overseer;

import com.example.entity.ZombieOverseerEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Убирает boss bar, когда надсмотрщик умирает или выгружается с сервера.
 * (Mixin в {@code remove} не подходит: метод объявлен в LivingEntity, не в Zombie.)
 */
public final class OverseerBossBarEvents {

	private OverseerBossBarEvents() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof Zombie zombie && isOverseer(zombie)) {
				OverseerBossBars.remove(zombie.getUUID());
			}
		});

		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof Zombie zombie && isOverseer(zombie)) {
				OverseerBossBars.remove(zombie.getUUID());
			}
		});
	}

	private static boolean isOverseer(Zombie zombie) {
		return zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer();
	}
}
