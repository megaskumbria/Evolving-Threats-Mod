package com.example.mixin;

import com.example.skeleton.SkeletonHorseman;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.SkeletonTrapGoal;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The thunder trap after our set again enchants the bow and helmet and, if the head is empty,
 * puts on an iron helmet. Because of this, the shield and potions remain, but the armor with weapons does not.
 Here this second passage is off: the rider remains the skeleton that the fashion put on.
 */
@Mixin(SkeletonTrapGoal.class)
public class SkeletonTrapRiderMixin {

	@Inject(method = "enchant", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$keepRiderEnchants(Skeleton skeleton, EquipmentSlot slot, DifficultyInstance difficulty, CallbackInfo ci) {
		if (SkeletonHorseman.ENABLED) {
			ci.cancel();
		}
	}

	@Redirect(
			method = "createSkeleton",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z"))
	private boolean evolvingthreats$noForcedIronHelmet(ItemStack stack) {
		if (SkeletonHorseman.ENABLED) {
			return false;
		}
		return stack.isEmpty();
	}
}
