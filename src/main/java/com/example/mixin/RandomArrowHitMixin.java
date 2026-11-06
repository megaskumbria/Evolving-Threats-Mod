package com.example.mixin;

import com.example.skeleton.RandomArrowSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*After a vanilla 5 seconds of fire puts the duration of the settings. */
@Mixin(AbstractArrow.class)
public class RandomArrowHitMixin {

	@Inject(method = "onHitEntity", at = @At("RETURN"))
	private void evolvingthreats$randomArrowFire(EntityHitResult result, CallbackInfo ci) {
		RandomArrowSkeleton.onFireHit((AbstractArrow) (Object) this, result.getEntity());
		RandomArrowSkeleton.onPlayerHit((AbstractArrow) (Object) this, result.getEntity());
	}
}
