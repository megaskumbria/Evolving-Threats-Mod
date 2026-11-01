package com.example.creeper;

import com.example.mixin.CreeperPowerAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Creeper;

/**
 The usual cryper occasionally appears already charged, as after lightning.
 The explosion is twice as strong, it can fall head.
 Dynamite does not throw such a cryper. Could be a bomber or a shield breaker.
 */
public final class ChargedCreeper {

	// ===================== SETTINGS (change these numbers) =====================

	/*False – charged do not appear on their own. Lightning is still charging the cryper. */
	public static final boolean ENABLED = true;

	/**
	 The chance (0.0–1.0) that the cryper is already charged when it appears in the world.
	 0.01 = 1%, about one in a hundred. This is less than a bomber and a shield breaker (in those 5%).
	 Separate throw: charged can still be a bomber or a shield breaker.
	 He does not become a dynamite thrower.
	 * 0 = doesn't appear. 1 = each cryper is charged.
	 */
	public static final float SPAWN_CHANCE = 0.01f;

	// ========================================================================

	private ChargedCreeper() {
	}

	/** Call from the appearance of the cryper, before the throw "dynamite thrower". The Spawn team doesn't give up. */
	public static void rollOnSpawn(Creeper creeper, EntitySpawnReason reason) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f || reason == EntitySpawnReason.COMMAND) {
			return;
		}
		if (creeper.isPowered()) {
			return;
		}
		if (creeper.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		creeper.getEntityData().set(CreeperPowerAccessor.evolvingthreats$poweredId(), true);
	}
}
