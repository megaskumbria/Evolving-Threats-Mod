package com.example.mixin;

import com.example.entity.ai.DispenserBarrageGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Connects the target to all skeletons; the target is only active with a dispenser on its head.
 */
@Mixin(AbstractSkeleton.class)
public class AbstractSkeletonDispenserGoalMixin {

	private static final int DISPENSER_BARRAGE_GOAL_PRIORITY = 2;

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerDispenserBarrageGoal(CallbackInfo ci) {
		AbstractSkeleton self = (AbstractSkeleton) (Object) this;
		self.getGoalSelector().addGoal(DISPENSER_BARRAGE_GOAL_PRIORITY, new DispenserBarrageGoal(self));
	}
}
