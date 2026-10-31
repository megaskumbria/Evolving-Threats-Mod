package com.example.mixin;

import com.example.skeleton.RandomArrowSkeleton;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Вместо обычной стрелы выпускает следующий эффект круга. Быстрый лук свою скорость не теряет. */
@Mixin(AbstractSkeleton.class)
public class RandomArrowSkeletonAttackMixin {

	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$randomArrow(LivingEntity target, float power, CallbackInfo ci) {
		if (RandomArrowSkeleton.shoot((AbstractSkeleton) (Object) this, target, power)) {
			ci.cancel();
		}
	}
}
