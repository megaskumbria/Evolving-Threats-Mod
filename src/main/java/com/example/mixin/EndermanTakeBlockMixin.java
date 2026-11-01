package com.example.mixin;

import com.example.enderman.EndermanBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 The moment the enderman decides to take the block.
 Check lives in the inner class of the Enderman, she does not have her own file.
 What blocks can be carried and where to look - still decides the game.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal")
public class EndermanTakeBlockMixin {

	@Redirect(
			method = "canUse",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/monster/Enderman$EndermanTakeBlockGoal;reducedTickDelay(I)I"
			)
	)
	private int evolvingthreats$takeInterval(int vanillaTicks) {
		return EndermanBlocks.takeInterval(vanillaTicks);
	}
}
