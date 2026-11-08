package com.example.mixin;

import com.example.illager.RavagerWallBreak;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game forgets a target it cannot see, before movement runs.
 * For a ravager, put that target back if a wall he can break blocks the view,
 * so he walks up and headbutts through the pause between strikes.
 * Same moments as the champion: after the target goals, before the movement goals.
 */
@Mixin(Mob.class)
public abstract class RavagerWallTargetMixin {

	@Inject(
			method = "serverAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V",
					ordinal = 1,
					shift = At.Shift.BEFORE))
	private void evolvingthreats$keepWallTargetBeforeRunGoals(CallbackInfo ci) {
		keepWallTarget();
	}

	@Inject(
			method = "serverAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V",
					ordinal = 1,
					shift = At.Shift.BEFORE))
	private void evolvingthreats$keepWallTargetBeforeGoalTick(CallbackInfo ci) {
		keepWallTarget();
	}

	private void keepWallTarget() {
		Mob self = (Mob) (Object) this;
		if (self instanceof Ravager ravager) {
			RavagerWallBreak.keepTargetThroughWall(ravager);
		}
	}
}
