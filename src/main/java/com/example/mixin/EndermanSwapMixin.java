package com.example.mixin;

import com.example.enderman.EndermanSwap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 *Enderman's server train of thought. The exchange of seats counts here.
 Because anger and purpose are already known. The teleport itself is at {@link EndermanSwap}.
 */
@Mixin(Enderman.class)
public class EndermanSwapMixin {

	@Inject(method = "customServerAiStep", at = @At("RETURN"))
	private void evolvingthreats$swap(ServerLevel level, CallbackInfo ci) {
		EndermanSwap.tick((Enderman) (Object) this, level);
	}
}
