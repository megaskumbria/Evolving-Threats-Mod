package com.example.mixin;

import com.example.drowned.DrownedThreats;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Попадание брошенного трезубца. Сюда заходит только удар, который уже снял здоровье.
 * Щит и мигание молнию не вызывают. Ближний удар трезубцем молнию не вызывает.
 */
@Mixin(ThrownTrident.class)
public class DrownedTridentMixin {

	@Inject(
			method = "onHitEntity",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;doPostHurtEffects(Lnet/minecraft/world/entity/LivingEntity;)V"
			)
	)
	private void evolvingthreats$tridentLightning(EntityHitResult hitResult, CallbackInfo ci) {
		DrownedThreats.onTridentHit((ThrownTrident) (Object) this, hitResult.getEntity());
	}
}
