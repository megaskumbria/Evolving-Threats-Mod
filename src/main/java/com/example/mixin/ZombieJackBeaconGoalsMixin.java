package com.example.mixin;

import com.example.entity.ai.JackBeaconAuraGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Цель «маяк стаи» — только у зомби с jack o'lantern на голове (см. {@link com.example.beacon.JackLanternBeacon}).
 */
@Mixin(Zombie.class)
public class ZombieJackBeaconGoalsMixin {

	private static final int JACK_BEACON_AURA_GOAL_PRIORITY = 6;

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerJackBeaconGoal(CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		self.getGoalSelector().addGoal(JACK_BEACON_AURA_GOAL_PRIORITY, new JackBeaconAuraGoal(self));
	}
}
