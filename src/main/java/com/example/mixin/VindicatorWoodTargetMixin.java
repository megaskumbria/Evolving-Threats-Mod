package com.example.mixin;

import com.example.illager.VindicatorWoodBreak;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Vindicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game forgets a target it cannot see, before movement runs.
 * For a champion, put that target back if wood is what blocks the view,
 * so he walks up to the house and chops. Same moments as the zombie grudge:
 * after the target goals, before the movement goals.
 */
@Mixin(Mob.class)
public abstract class VindicatorWoodTargetMixin {

	@Inject(
			method = "serverAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V",
					ordinal = 1,
					shift = At.Shift.BEFORE))
	private void evolvingthreats$keepWoodTargetBeforeRunGoals(CallbackInfo ci) {
		keepWoodTarget();
	}

	@Inject(
			method = "serverAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V",
					ordinal = 1,
					shift = At.Shift.BEFORE))
	private void evolvingthreats$keepWoodTargetBeforeGoalTick(CallbackInfo ci) {
		keepWoodTarget();
	}

	private void keepWoodTarget() {
		Mob self = (Mob) (Object) this;
		if (self instanceof Vindicator vindicator) {
			VindicatorWoodBreak.keepTargetThroughWood(vindicator);
		}
	}
}
