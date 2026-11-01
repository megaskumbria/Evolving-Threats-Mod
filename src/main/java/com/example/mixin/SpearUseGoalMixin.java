package com.example.mixin;

import net.minecraft.world.entity.ai.goal.SpearUseGoal;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 NPE Protection 26.3: On odd tics, SpearUseGoal.tick() is called without target verification.
 * Mob field is generic Monster, not Mob; we use @Accessor, not @Shadow for access.
 */
@Mixin(SpearUseGoal.class)
public abstract class SpearUseGoalMixin {

	@Accessor("mob")
	abstract Monster evolvingthreats$getMob();

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$skipTickWithoutTarget(CallbackInfo ci) {
		if (this.evolvingthreats$getMob().getTarget() == null) {
			ci.cancel();
		}
	}
}
