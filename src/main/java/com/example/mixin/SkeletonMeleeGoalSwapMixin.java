package com.example.mixin;

import com.example.RandomWeapons;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 *Inner class AbstractSkeleton$1 is a goal to "beat in melee."
 When starting / stopping, change hands in places so that the blows go with the sword, not with the bow.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.skeleton.AbstractSkeleton$1")
public class SkeletonMeleeGoalSwapMixin {

	@Shadow
	@Final
	AbstractSkeleton this$0;

	@Inject(method = "start", at = @At("RETURN"))
	private void evolvingthreats$swapToMelee(CallbackInfo ci) {
		RandomWeapons.swapToMeleeHands(this$0);
	}

	@Inject(method = "stop", at = @At("RETURN"))
	private void evolvingthreats$swapToRanged(CallbackInfo ci) {
		RandomWeapons.swapToRangedHands(this$0);
	}
}
