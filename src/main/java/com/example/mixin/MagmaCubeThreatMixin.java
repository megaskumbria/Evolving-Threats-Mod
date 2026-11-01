package com.example.mixin;

import com.example.magma.MagmaCubeThreats;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 The size of the cube is set by the game itself, including when the large is divided into averages.
 Here, after that, health and speed rise. The jump pause is shorter only in overheating.
 */
@Mixin(MagmaCube.class)
public class MagmaCubeThreatMixin {

	@Inject(method = "setSize", at = @At("RETURN"))
	private void evolvingthreats$magmaStats(int size, boolean updateHealth, CallbackInfo ci) {
		MagmaCubeThreats.onSize((MagmaCube) (Object) this, updateHealth);
	}

	@Inject(method = "getJumpDelay", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$overheatJump(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(MagmaCubeThreats.jumpDelay((MagmaCube) (Object) this, cir.getReturnValue()));
	}
}
