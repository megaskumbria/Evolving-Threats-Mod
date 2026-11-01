package com.example.mixin;

import com.example.entity.ai.DrownedFishingGoal;
import net.minecraft.world.entity.monster.zombie.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * To the list of drowned behavior is added the cast of the fishing rod.
 A close strike remains his usual attack.
 */
@Mixin(Drowned.class)
public class DrownedFishingMixin {

	@Inject(method = "addBehaviourGoals", at = @At("RETURN"))
	private void evolvingthreats$fishingRod(CallbackInfo ci) {
		Drowned drowned = (Drowned) (Object) this;
		drowned.getGoalSelector().addGoal(2, new DrownedFishingGoal(drowned));
	}
}
