package com.example.mixin;

import com.example.skeleton.SpectralArrowHunt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The spectral arrow just put a light on.
 If it was released by a skeleton and hit a player, a hunt goes up next to it.
 The player's arrow does not wake the crowd.
 */
@Mixin(SpectralArrow.class)
public class SpectralArrowHuntMixin {

	@Inject(method = "doPostHurtEffects", at = @At("RETURN"))
	private void evolvingthreats$alertMobs(LivingEntity hit, CallbackInfo ci) {
		SpectralArrow arrow = (SpectralArrow) (Object) this;
		if (arrow.getOwner() instanceof AbstractSkeleton && hit instanceof Player player) {
			SpectralArrowHunt.alert(player);
		}
	}
}
