package com.example.mixin;

import com.example.skeleton.RandomArrowSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** После ванильных 5 секунд огня ставит длительность из настроек. */
@Mixin(AbstractArrow.class)
public class RandomArrowHitMixin {

	@Inject(method = "onHitEntity", at = @At("RETURN"))
	private void evolvingthreats$randomArrowFire(EntityHitResult result, CallbackInfo ci) {
		RandomArrowSkeleton.onFireHit((AbstractArrow) (Object) this, result.getEntity());
	}
}
