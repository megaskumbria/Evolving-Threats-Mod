package com.example.mixin;

import com.example.magma.MagmaCubeThreats;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 Common cube tick. Slug doesn't do anything here.
 The magma cube does not have its own tick, so overheating and magma are counted from here.
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
