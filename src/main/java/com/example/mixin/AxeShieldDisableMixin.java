package com.example.mixin;

import com.example.axe.AxeShieldDisable;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Игра спрашивает это число, когда удар уже попал в поднятый щит.
 * У топора в основной руке оно 5 секунд. Здесь то же для второй руки
 * и ноль для деревянного топора у зомби, скелета и брута.
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
