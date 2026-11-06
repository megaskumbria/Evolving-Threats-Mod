package com.example.mixin;

import com.example.skeleton.HarmfulArrowSkeleton;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After a tipped arrow applies its effect. Harming damage is dealt again so the
 * arrow's short invulnerability does not eat the hearts. Numbers are {@link HarmfulArrowSkeleton}.
 */
@Mixin(Arrow.class)
public class HarmfulArrowHitMixin {

	@Inject(method = "doPostHurtEffects", at = @At("RETURN"))
	private void evolvingthreats$harming(LivingEntity target, CallbackInfo ci) {
		HarmfulArrowSkeleton.onHarmingHit((Arrow) (Object) this, target);
	}
}
