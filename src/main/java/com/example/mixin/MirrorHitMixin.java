package com.example.mixin;

import com.example.mirror.Mirror;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hitting a living thing. The number at the beginning of the method is before the armor of the person hit.
 If the strike comes and it is Mirror, the same number is returned to the player, and his armor cuts him.
 Shield fashion extinguishes the blow at the beginning of another mixin: then here comes the "blow did not come."
 */
@Mixin(LivingEntity.class)
public class MirrorHitMixin {

	/*Damage to target armor. The game can reduce the local variable, this number does not touch. */
	@Unique
	private float evolvingthreats$mirrorIncoming;

	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void evolvingthreats$rememberMirrorIncoming(
			ServerLevel level,
			DamageSource source,
			float damage,
			CallbackInfoReturnable<Boolean> cir) {
		this.evolvingthreats$mirrorIncoming = damage;
	}

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void evolvingthreats$mirrorHit(
			ServerLevel level,
			DamageSource source,
			float damage,
			CallbackInfoReturnable<Boolean> cir) {
		Mirror.onHurt(
				(LivingEntity) (Object) this,
				level,
				source,
				this.evolvingthreats$mirrorIncoming,
				Boolean.TRUE.equals(cir.getReturnValue()));
	}
}
