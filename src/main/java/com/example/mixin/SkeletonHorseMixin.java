package com.example.mixin;

import com.example.entity.SkeletonHorsemanMount;
import com.example.skeleton.SkeletonHorseman;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Помечает лошадь наездника и убирает её, когда всадник умер или игрока нет рядом.
 * Код лошади крутится каждый тик здесь, решение — в SkeletonHorseman.
 */
@Mixin(SkeletonHorse.class)
public class SkeletonHorseMixin implements SkeletonHorsemanMount {

	@Unique
	private boolean evolvingthreats$horseman;

	@Override
	public boolean evolvingthreats$isHorseman() {
		return evolvingthreats$horseman;
	}

	@Override
	public void evolvingthreats$setHorseman(boolean horseman) {
		this.evolvingthreats$horseman = horseman;
	}

	@Inject(method = "aiStep", at = @At("HEAD"))
	private void evolvingthreats$tickHorseman(CallbackInfo ci) {
		SkeletonHorseman.tickMount((SkeletonHorse) (Object) this);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveHorseman(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$horseman) {
			output.putBoolean("EvolvingThreatsHorseman", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadHorseman(ValueInput input, CallbackInfo ci) {
		evolvingthreats$horseman = input.getBooleanOr("EvolvingThreatsHorseman", false);
	}
}
