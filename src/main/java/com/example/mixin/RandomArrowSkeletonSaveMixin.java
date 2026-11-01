package com.example.mixin;

import com.example.entity.RandomArrowSkeletonEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*The queue of effects persists, otherwise, after loading the world, the same effect could come out twice. */
@Mixin(Mob.class)
public class RandomArrowSkeletonSaveMixin {

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveRandomArrow(ValueOutput output, CallbackInfo ci) {
		if (!((Object) this instanceof RandomArrowSkeletonEntity data) || !data.evolvingthreats$isRandomArrow()) {
			return;
		}
		output.putBoolean("EvolvingThreatsRandomArrow", true);
		output.putString("EvolvingThreatsRandomArrowQueue", data.evolvingthreats$getArrowQueue());
		output.putString("EvolvingThreatsRandomArrowLast", data.evolvingthreats$getLastArrow());
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadRandomArrow(ValueInput input, CallbackInfo ci) {
		if (!((Object) this instanceof RandomArrowSkeletonEntity data)) {
			return;
		}
		if (!input.getBooleanOr("EvolvingThreatsRandomArrow", false)) {
			return;
		}
		data.evolvingthreats$setRandomArrow(true);
		data.evolvingthreats$setArrowQueue(input.getString("EvolvingThreatsRandomArrowQueue").orElse(""));
		data.evolvingthreats$setLastArrow(input.getString("EvolvingThreatsRandomArrowLast").orElse(""));
	}
}
