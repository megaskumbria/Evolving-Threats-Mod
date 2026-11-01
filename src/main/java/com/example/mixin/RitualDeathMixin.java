package com.example.mixin;

import com.example.ritual.RitualBand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*After the death of a pack of cryper - a small explosion. Loot had already fallen by this point. */
@Mixin(LivingEntity.class)
public class RitualDeathMixin {

	@Inject(method = "die", at = @At("TAIL"))
	private void evolvingthreats$ritualPop(DamageSource source, CallbackInfo ci) {
		if ((Object) this instanceof Mob mob) {
			RitualBand.onDeath(mob);
		}
	}
}
