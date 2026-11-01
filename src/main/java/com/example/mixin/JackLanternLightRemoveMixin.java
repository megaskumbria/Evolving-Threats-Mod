package com.example.mixin;

import com.example.beacon.JackLanternLight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The pumpkin mob is gone. Lights go out here: aiStep after removal no longer goes.
 */
@Mixin(Entity.class)
public class JackLanternLightRemoveMixin {

	@Inject(method = "remove", at = @At("HEAD"))
	private void evolvingthreats$clearJackLight(Entity.RemovalReason reason, CallbackInfo ci) {
		if ((Object) this instanceof Mob mob) {
			JackLanternLight.clear(mob);
		}
	}
}
