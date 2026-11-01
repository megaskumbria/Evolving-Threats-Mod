package com.example.mixin;

import com.example.spiky.Spiky;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 Two seats in one stroke.
 At the beginning: extinguish the usual spines of the game, if Spiky put them on, so that they do not fold with a prick on 7-8 hearts.
 * At the end: If the player hits Spiky, we return.
 */
@Mixin(LivingEntity.class)
public class SpikyHitMixin {

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$cancelSpikyVanillaThorns(
			ServerLevel level,
			DamageSource source,
			float damage,
			CallbackInfoReturnable<Boolean> cir) {
		if (Spiky.isVanillaThorns(source)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void evolvingthreats$spikyHit(
			ServerLevel level,
			DamageSource source,
			float damage,
			CallbackInfoReturnable<Boolean> cir) {
		Spiky.onHurt((LivingEntity) (Object) this, level, source, Boolean.TRUE.equals(cir.getReturnValue()));
	}
}
