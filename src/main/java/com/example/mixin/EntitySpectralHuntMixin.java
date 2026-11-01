package com.example.mixin;

import com.example.skeleton.SpectralArrowHunt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 Removal of the creature is declared not in the mob, but in the general class of the creature.
 When the mob disappears from the hunt, it is removed from the list so that it does not accumulate.
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
