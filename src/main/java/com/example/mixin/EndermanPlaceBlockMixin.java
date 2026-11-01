package com.example.mixin;

import com.example.enderman.EndermanBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 The moment when the enderman decides to put a block that is already carrying.
 Check lives in the inner class of the Enderman, she does not have her own file.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Enderman$EndermanLeaveBlockGoal")
public class EndermanPlaceBlockMixin {

	@Redirect(
			method = "canUse",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/monster/Enderman$EndermanLeaveBlockGoal;reducedTickDelay(I)I"
			)
	)
	private int evolvingthreats$placeInterval(int vanillaTicks) {
		return EndermanBlocks.placeInterval(vanillaTicks);
	}
}
