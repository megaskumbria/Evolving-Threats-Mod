package com.example.mixin;

import com.example.skeleton.SpectralArrowHunt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Удаление существа объявлено не у моба, а у общего класса существа.
 * Когда моб из охоты исчезает, его убирают из списка, чтобы тот не копился.
 */
@Mixin(Entity.class)
public class EntitySpectralHuntMixin {

	@Inject(method = "remove", at = @At("HEAD"))
	private void evolvingthreats$forgetSpectralHunt(Entity.RemovalReason reason, CallbackInfo ci) {
		if ((Object) this instanceof Mob mob) {
			SpectralArrowHunt.forget(mob);
		}
	}
}
