package com.example.mixin;

import com.example.entity.ai.RavagerHeadbuttGoal;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The destroyer receives the goal of punching the wall with his head.
 * What breaks and what pause - {@link com.example.illager.RavagerWallBreak}
 */
@Mixin(Ravager.class)
public class RavagerHeadbuttMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$headbuttWall(CallbackInfo ci) {
		Ravager self = (Ravager) (Object) this;
		// 3 - higher than the usual bite (4). If there is a path, this target is silent and the bite works.
		self.getGoalSelector().addGoal(3, new RavagerHeadbuttGoal(self));
	}
}
