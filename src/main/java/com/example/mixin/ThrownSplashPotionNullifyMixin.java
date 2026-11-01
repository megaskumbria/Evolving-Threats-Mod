package com.example.mixin;

import com.example.witch.WitchNullifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The moment the witch created the explosive potion.
 If this throw is nullifying, a bottle of poison or damage is replaced with a white one without its effects.
 * Hit by {@link AbstractThrownPotionNullifyMixin}.
 */
@Mixin(ThrownSplashPotion.class)
public class ThrownSplashPotionNullifyMixin {

	@Inject(
			method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("RETURN"))
	private void evolvingthreats$swapNullifyPotion(Level level, LivingEntity owner, ItemStack stack, CallbackInfo ci) {
		if (WitchNullifier.consumeArmed(owner)) {
			((ThrownSplashPotion) (Object) this).setItem(WitchNullifier.potionStack());
		}
	}
}
