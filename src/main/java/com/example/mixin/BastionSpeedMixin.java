package com.example.mixin;

import com.example.entity.BastionEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Любому живому вешают эффект. Бастиону зелье «Скорость» не вешается:
 * ни при появлении, ни из бутылки, ни от маяка тыквы.
 * Замедление, сила и прыгучесть проходят как обычно.
 */
@Mixin(LivingEntity.class)
public class BastionSpeedMixin {

	@Inject(
			method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
			at = @At("HEAD"),
			cancellable = true)
	private void evolvingthreats$bastionIgnoreSpeed(
			MobEffectInstance effect,
			@Nullable Entity source,
			CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof BastionEntity data
				&& data.evolvingthreats$isBastion()
				&& effect.getEffect().equals(MobEffects.SPEED)) {
			cir.setReturnValue(false);
		}
	}
}
