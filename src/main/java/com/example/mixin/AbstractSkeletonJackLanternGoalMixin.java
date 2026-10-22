package com.example.mixin;

import com.example.entity.ai.JackLanternFireShotGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Огненная стрела раз в кулдаун — только со jack o'lantern на голове. */
@Mixin(AbstractSkeleton.class)
public class AbstractSkeletonJackLanternGoalMixin {

	private static final int JACK_FIRE_SHOT_GOAL_PRIORITY = 3;

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerJackFireShotGoal(CallbackInfo ci) {
		AbstractSkeleton self = (AbstractSkeleton) (Object) this;
		self.getGoalSelector().addGoal(JACK_FIRE_SHOT_GOAL_PRIORITY, new JackLanternFireShotGoal(self));
	}
}
