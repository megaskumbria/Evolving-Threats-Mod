package com.example.mixin;

import com.example.skeleton.SkeletonCaptain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Удар до того, как игра запомнит обидчика.
 * Стрела капитана и меч охранника по своим не проходят: нет урона и нет ответной атаки.
 */
@Mixin(LivingEntity.class)
public class SquadFriendlyFireMixin {

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$squadNoFriendlyFire(
			ServerLevel level,
			DamageSource source,
			float damage,
			CallbackInfoReturnable<Boolean> cir) {
		if (!(source.getEntity() instanceof LivingEntity attacker)) {
			return;
		}
		if (SkeletonCaptain.isSquadAlly((LivingEntity) (Object) this, attacker)) {
			cir.setReturnValue(false);
		}
	}
}
