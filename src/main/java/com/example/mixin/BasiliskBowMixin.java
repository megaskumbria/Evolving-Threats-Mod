package com.example.mixin;

import com.example.skeleton.BasiliskSkeleton;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * После обычной стрельбы Василиск получает дальнюю дистанцию.
 * Скорость стрелы и прицел — в {@link AbstractSkeletonFastBowMixin}, там же, где у дальнего лука.
 */
@Mixin(AbstractSkeleton.class)
public abstract class BasiliskBowMixin {

	@Shadow
	@Final
	private RangedBowAttackGoal<AbstractSkeleton> bowGoal;

	@Inject(method = "reassessWeaponGoal", at = @At("RETURN"))
	private void evolvingthreats$basiliskRange(CallbackInfo ci) {
		BasiliskSkeleton.applyBow((AbstractSkeleton) (Object) this, bowGoal);
	}
}
