package com.example.mixin;

import com.example.enderman.EndermanBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Момент, когда эндермен решает поставить блок, который уже несёт.
 * Проверка живёт во внутреннем классе эндермена, своего файла у неё нет.
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
