package com.example.phantom;

import com.example.entity.WeakeningPhantomEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;

/**
 Part of the phantom hangs weakness when the bite relieved health.
 Who knows - is decided once upon appearance. The rest bite as before, only with damage.
 A shield that repulsed the blow, and a short blink after the blow do not give weakness.
 */
public final class PhantomWeakness {

	// ===================== SETTINGS (change these numbers) =====================

	/*False - bite again only with damage, weakness does not hang even in already labeled phantoms. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0-1.0) that the phantom can hang weakness when it appears.
	 * 0.30 = 30%, approximately one in three. Throw one: night pack, egg and team spawn.
	 This phantom hangs the weakness with every bite that has passed. The rest never.
	 * 0 = no weakness. 1 = each phantom hangs it.
	 */
	public static final float SPAWN_CHANCE = 0.30f;

	/**
	 How many seconds does weakness last? 10 = ten seconds.
	 Repeated bite updates the time if the player's weakness is already shorter or the same.
	 Longer weakness (for example, from an arrow) is not shortened by this bite.
	 Milk removes the effect immediately. 0 = no effect.
	 */
	public static final int WEAKNESS_SECONDS = 10;

	/**
	 The power of weakness. 0 = Weakness I: The player's close beats are weaker by 4 health (2 hearts).
	 1 = Weakness II: weaker by 8 health (4 hearts), the sword often hardly beats.
	 */
	public static final int AMPLIFIER = 0;

	// ========================================================================

	private PhantomWeakness() {
	}

	/** Once when a phantom appears. Repeated call already marked does not throw the chance. */
	public static void rollOnSpawn(Phantom phantom) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f || !(phantom instanceof WeakeningPhantomEntity data)) {
			return;
		}
		if (data.evolvingthreats$weakensOnBite()) {
			return;
		}
		if (phantom.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		data.evolvingthreats$setWeakensOnBite(true);
	}

	/**
	 The bite has taken its toll.
	 The player does not get here: golem, animal and other mob do not get weakness.
	 */
	public static void onBite(Phantom phantom, LivingEntity target) {
		if (!ENABLED || WEAKNESS_SECONDS <= 0 || !(target instanceof Player)) {
			return;
		}
		if (!(phantom instanceof WeakeningPhantomEntity data) || !data.evolvingthreats$weakensOnBite()) {
			return;
		}
		target.addEffect(new MobEffectInstance(
				MobEffects.WEAKNESS,
				WEAKNESS_SECONDS * 20,
				Math.max(0, AMPLIFIER),
				false,
				true,
				true), phantom);
	}
}
