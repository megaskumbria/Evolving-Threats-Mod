package com.example.mixin;

import com.example.skeleton.TacticianSkeleton;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*Instead of the usual arrow, the 700 IQ releases its own: weakness, slowdown and blindness. */
@Mixin(AbstractSkeleton.class)
public class TacticianSkeletonAttackMixin {

	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$tacticianArrow(LivingEntity target, float power, CallbackInfo ci) {
		if (TacticianSkeleton.shoot((AbstractSkeleton) (Object) this, target, power)) {
			ci.cancel();
		}
	}
}
