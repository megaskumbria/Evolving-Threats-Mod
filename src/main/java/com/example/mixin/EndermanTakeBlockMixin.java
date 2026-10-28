package com.example.mixin;

import com.example.enderman.EndermanBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Момент, когда эндермен решает взять блок.
 * Проверка живёт во внутреннем классе эндермена, своего файла у неё нет.
 * Какие блоки можно нести и куда смотреть — по-прежнему решает игра.
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
