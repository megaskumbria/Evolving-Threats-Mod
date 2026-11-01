package com.example.mixin;

import com.example.MobShields;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hitting a living thing. If it's a mob with a shield fashion and a block chance has dropped,
 The blow is extinguished completely: the game does not even begin to count damage.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMobShieldMixin {

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$absorbHitWithShield(
			ServerLevel level,
			DamageSource source,
			float damage,
			CallbackInfoReturnable<Boolean> cir) {
		if (MobShields.tryAbsorb((LivingEntity) (Object) this, source, damage)) {
			cir.setReturnValue(false);
		}
	}
}
