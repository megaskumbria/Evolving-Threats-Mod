package com.example.mixin;

import com.example.ritual.RitualBand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 Experience and extra loot packs. Both methods are announced by Mob.
 * Cryper cotton - {@link RitualDeathMixin}, damage and desiccation - {@link RitualHurtMixin}.
 */
@Mixin(Mob.class)
public class RitualMobMixin {

	@Inject(method = "getBaseExperienceReward", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$ritualXp(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
		int xp = RitualBand.experienceReward((Mob) (Object) this);
		if (xp >= 0) {
			cir.setReturnValue(xp);
		}
	}

	@Inject(method = "dropCustomDeathLoot", at = @At("RETURN"))
	private void evolvingthreats$ritualBonus(ServerLevel level, DamageSource source, boolean killedByPlayer, CallbackInfo ci) {
		RitualBand.dropBonus((Mob) (Object) this, level, source, killedByPlayer);
	}
}
