package com.example.mixin;

import com.example.entity.SniperSkeletonEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Пометка читается до того, как скелет заново выбирает стрельбу из лука. */
@Mixin(Mob.class)
public class SniperSkeletonSaveMixin {

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveSniper(ValueOutput output, CallbackInfo ci) {
		if ((Object) this instanceof SniperSkeletonEntity data && data.evolvingthreats$isSniper()) {
			output.putBoolean("EvolvingThreatsSniper", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadSniper(ValueInput input, CallbackInfo ci) {
		if ((Object) this instanceof SniperSkeletonEntity data) {
			data.evolvingthreats$setSniper(input.getBooleanOr("EvolvingThreatsSniper", false));
		}
	}
}
