package com.example.mixin;

import com.example.axe.AxeShieldDisable;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 The game asks for this number when the hit has already hit the raised shield.
 The axe in the main hand has 5 seconds. Same here for the second hand.
 * and zero for a wooden axe in zombies, skeleton and brute.
 */
@Mixin(LivingEntity.class)
public class AxeShieldDisableMixin {

	@Inject(method = "getSecondsToDisableBlocking", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$axeShieldCooldown(CallbackInfoReturnable<Float> cir) {
		float seconds = AxeShieldDisable.seconds((LivingEntity) (Object) this);
		if (seconds >= 0.0f) {
			cir.setReturnValue(seconds);
		}
	}
}
