package com.example.mixin;

import com.example.drowned.DrownedThreats;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Abandoned trident hit. This comes only a blow that has already taken away health.
 Shield and flashing do not cause lightning. A near-trident strike does not cause lightning.
 The challenge is sought from the trident itself: in this version of the game it is not recorded as calling an arrow.
 */
@Mixin(ThrownTrident.class)
public class DrownedTridentMixin {

	@Inject(
			method = "onHitEntity",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/arrow/ThrownTrident;doPostHurtEffects(Lnet/minecraft/world/entity/LivingEntity;)V"
			)
	)
	private void evolvingthreats$tridentLightning(EntityHitResult hitResult, CallbackInfo ci) {
		DrownedThreats.onTridentHit((ThrownTrident) (Object) this, hitResult.getEntity());
	}
}
