package com.example.raid;

import com.example.entity.RaidParticipantEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Смерть якоря → «разрыв связи» с прислугами.
 */
public final class RaidAnchorEvents {

	private RaidAnchorEvents() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof Zombie anchor)) {
				return;
			}
			if (!(anchor instanceof RaidParticipantEntity raid) || !raid.evolvingthreats$isRaidAnchor()) {
				return;
			}
			if (!(anchor.level() instanceof ServerLevel level)) {
				return;
			}
			var raidId = raid.evolvingthreats$getRaidId();
			if (raidId == null) {
				return;
			}
			RaidAnchor.disbandRaidNear(level, raidId, anchor.position());
		});
	}
}
