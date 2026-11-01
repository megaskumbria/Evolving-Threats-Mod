package com.example.mixin;

import com.example.creeper.DynamiteCreeper;
import com.example.creeper.SapperCreeper;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 Dynamite thrower and bomber do not use the usual “inflated close”.
 The bomber explodes its target, the thrower throws TNT.
 The fire still sets fire to the cryper: it does the cryper in the tick(), not this target.
 */
@Mixin(SwellGoal.class)
public class SwellGoalMixin {

	@Shadow
	private Creeper creeper;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$noSwellForDynamiteThrower(CallbackInfoReturnable<Boolean> cir) {
		if (DynamiteCreeper.isDynamite(creeper) || SapperCreeper.isSapper(creeper)) {
			cir.setReturnValue(false);
		}
	}
}
