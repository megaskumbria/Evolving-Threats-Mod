package com.example.mixin;

import com.example.advancement.ModAdvancements;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

	/**
	 * A totem just saved this mob. Runs only when the check returns true,
	 * so a totem that was not in hand does not count. The player's own totem
	 * is ignored: this entity is then a player, not a mob.
	 */
	@Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
	private void evolvingthreats$mobTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			ModAdvancements.onMobTotem((LivingEntity) (Object) this, source);
		}
	}
}
