package com.example.mixin;

import com.example.entity.ai.VindicatorChopGoal;
import net.minecraft.world.entity.monster.illager.Vindicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The goal is to cut down a tree along the way.
 * What counts as a tree and how fast - {@link com.example.illager.VindicatorWoodBreak}
 */
@Mixin(Vindicator.class)
public class VindicatorChopMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$chopWood(CallbackInfo ci) {
		Vindicator self = (Vindicator) (Object) this;
		// 4 - above the usual impact (5), below the breaking of doors in the raid (2).
		self.getGoalSelector().addGoal(4, new VindicatorChopGoal(self));
	}
}
