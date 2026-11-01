package com.example.mixin;

import com.example.skeleton.SpectralArrowHunt;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 Hunting after the spectral arrow.
 The goal is set after the choice of the target mob and right before the step of the path,
 Otherwise, he immediately forgets the player behind the wall and goes nowhere.
 */
@Mixin(Mob.class)
public class MobSpectralHuntMixin {

	@Inject(
			method = "serverAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;tick()V"
			)
	)
	private void evolvingthreats$keepSpectralHunt(CallbackInfo ci) {
		SpectralArrowHunt.keepChasing((Mob) (Object) this);
	}
}
