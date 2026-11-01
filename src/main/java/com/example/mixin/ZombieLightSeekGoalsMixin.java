package com.example.mixin;

import com.example.entity.ai.LightSeekGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the move-toward-light goal. Priority 5: below attack (3), above wander (7).
 * Exclusions (drowned, piglin, bosses) — {@link com.example.light.LightSeeking#isEligible}.
 */
@Mixin(Zombie.class)
public class ZombieLightSeekGoalsMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerLightSeekGoal(CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		self.getGoalSelector().addGoal(5, new LightSeekGoal(self));
	}
}
