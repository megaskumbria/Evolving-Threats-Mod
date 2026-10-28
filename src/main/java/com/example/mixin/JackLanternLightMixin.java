package com.example.mixin;

import com.example.beacon.JackLanternLight;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Светящаяся тыква на голове. Ход моба переставляет свет.
 * Удаление моба (смерть, выгрузка чанка) свет гасит.
 */
@Mixin(Mob.class)
public class JackLanternLightMixin {

	@Inject(method = "aiStep", at = @At("RETURN"))
	private void evolvingthreats$jackLight(CallbackInfo ci) {
		JackLanternLight.tick((Mob) (Object) this);
	}
}
