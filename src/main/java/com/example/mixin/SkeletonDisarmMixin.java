package com.example.mixin;

import com.example.skeleton.SkeletonDisarm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Arrow hit.
 The usual stroke calls the general method when health is already removed.
 In creative health is not removed, so there is a check from the very hit.
 * Shield still holds out. The numbers are {@link SkeletonDisarm}.
 */
@Mixin(AbstractArrow.class)
public class SkeletonDisarmMixin {

	@Inject(method = "doPostHurtEffects", at = @At("HEAD"))
	private void evolvingthreats$disarm(LivingEntity target, CallbackInfo ci) {
		if (target instanceof Player player) {
			SkeletonDisarm.onArrowHit((AbstractArrow) (Object) this, player);
		}
	}

	/**
	 The creative and the observer do not receive damage, and the method above is not called for them.
	 We're not touching the observer. The shield in the creative also beats off the arrow - the hands remain.
	 */
	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void evolvingthreats$disarmCreative(EntityHitResult hit, CallbackInfo ci) {
		if (!(hit.getEntity() instanceof Player player) || player.isSpectator()) {
			return;
		}
		if (!player.getAbilities().invulnerable || player.isBlocking()) {
			return;
		}
		SkeletonDisarm.onArrowHit((AbstractArrow) (Object) this, player);
	}
}
