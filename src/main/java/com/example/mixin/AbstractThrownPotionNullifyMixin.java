package com.example.mixin;

import com.example.witch.WitchNullifier;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 Explosive potion hitting a block or creature.
 Ordinary potions pass by. White potion of the witch-zero removes the effects from the player.
 */
@Mixin(AbstractThrownPotion.class)
public class AbstractThrownPotionNullifyMixin {

	@Inject(method = "onHit", at = @At("HEAD"))
	private void evolvingthreats$nullifySplash(HitResult hitResult, CallbackInfo ci) {
		WitchNullifier.onSplash((AbstractThrownPotion) (Object) this, hitResult);
	}
}
