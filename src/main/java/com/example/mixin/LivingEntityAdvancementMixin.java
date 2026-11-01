package com.example.mixin;

import com.example.advancement.ModAdvancements;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kill advancements, at the start of death.
 * Both hands are still full here. By the end of this method the loot has dropped
 * and the hands are empty, so "a weapon in each hand" would never count.
 * A totem cancels death before this method runs, so a saved mob is not a kill.
 */
@Mixin(LivingEntity.class)
public class LivingEntityAdvancementMixin {

	@Inject(method = "die", at = @At("HEAD"))
	private void evolvingthreats$advancements(DamageSource source, CallbackInfo ci) {
		ModAdvancements.onDying((LivingEntity) (Object) this, source);
	}
}
