package com.example.mixin;

import com.example.RandomDispenserSkull;
import com.example.beacon.JackLanternBeacon;
import com.example.skeleton.FastBowSkeleton;
import com.example.skeleton.RandomArrowSkeleton;
import com.example.skeleton.SkeletonPunchBow;
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
		AbstractArrow arrow = (AbstractArrow) (Object) this;
		RandomArrowSkeleton.onFireHit(arrow, result.getEntity());
		RandomArrowSkeleton.onPlayerHit(arrow, result.getEntity());
		FastBowSkeleton.onArrowHit(arrow, result.getEntity());
		SkeletonPunchBow.onArrowHit(arrow, result.getEntity());
		JackLanternBeacon.onArrowHit(arrow, result.getEntity());
		RandomDispenserSkull.onArrowHit(arrow, result.getEntity());
	}
}
