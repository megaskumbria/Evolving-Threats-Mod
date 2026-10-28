package com.example.mixin;

import com.example.beacon.JackLanternLight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Моб с тыквой ушёл из мира. Свет гасим здесь: aiStep после удаления уже не идёт.
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
