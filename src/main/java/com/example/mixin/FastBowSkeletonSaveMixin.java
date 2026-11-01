package com.example.mixin;

import com.example.entity.FastBowSkeletonEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*The mark is read before the skeleton re-chooses archery. */
@Mixin(Mob.class)
public class FastBowSkeletonSaveMixin {

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveFastBow(ValueOutput output, CallbackInfo ci) {
		if ((Object) this instanceof FastBowSkeletonEntity data && data.evolvingthreats$isFastBow()) {
			output.putBoolean("EvolvingThreatsFastBow", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadFastBow(ValueInput input, CallbackInfo ci) {
		if ((Object) this instanceof FastBowSkeletonEntity data) {
			data.evolvingthreats$setFastBow(input.getBooleanOr("EvolvingThreatsFastBow", false));
		}
	}
}
