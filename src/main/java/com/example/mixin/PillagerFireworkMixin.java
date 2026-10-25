package com.example.mixin;

import com.example.illager.PillagerFirework;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Начало зарядки арбалета, до того как игра положит в него стрелу.
 * Ракета уже лежит на арбалете, поэтому всю зарядку виден фейерверк.
 * Шанс — {@link PillagerFirework}.
 */
@Mixin(LivingEntity.class)
public class PillagerFireworkMixin {

	@Inject(method = "startUsingItem", at = @At("HEAD"))
	private void evolvingthreats$chargeFirework(InteractionHand hand, CallbackInfo ci) {
		PillagerFirework.onChargeStart((LivingEntity) (Object) this, hand);
	}
}
