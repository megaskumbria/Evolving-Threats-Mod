package com.example.mixin;

import com.example.beacon.JackLanternLight;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 Glowing pumpkin on his head. Mob moves the light.
 Removal of the mob (death, unloading of the chank) lights out.
 */
@Mixin(Mob.class)
public class JackLanternLightMixin {

	@Inject(method = "aiStep", at = @At("RETURN"))
	private void evolvingthreats$jackLight(CallbackInfo ci) {
		JackLanternLight.tick((Mob) (Object) this);
	}
}
