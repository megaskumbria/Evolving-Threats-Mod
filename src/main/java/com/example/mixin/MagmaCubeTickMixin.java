package com.example.mixin;

import com.example.magma.MagmaCubeThreats;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Общий тик кубов. Слизень здесь ничего не делает.
 * У магмового куба своего тика нет, поэтому перегрев и магма считаются отсюда.
 */
@Mixin(AbstractCubeMob.class)
public class MagmaCubeTickMixin {

	@Inject(method = "tick", at = @At("RETURN"))
	private void evolvingthreats$magmaTick(CallbackInfo ci) {
		if ((Object) this instanceof MagmaCube cube) {
			MagmaCubeThreats.tick(cube);
		}
	}
}
