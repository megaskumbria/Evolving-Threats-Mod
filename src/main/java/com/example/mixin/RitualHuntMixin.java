package com.example.mixin;

import com.example.ritual.RitualBand;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The pack with the head of the player takes the target again before the AI targets have time to reset it.
 The points are the same as the memory of ordinary zombies: otherwise, the goal disappears behind the wall.
 At the end of the step, the pack of piglin is extinguished: the sun sets the zombies on fire after the goals.
 */
@Mixin(Mob.class)
public abstract class RitualHuntMixin {

	@Inject(
			method = "serverAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V",
					ordinal = 1,
					shift = At.Shift.BEFORE))
	private void evolvingthreats$ritualHuntBeforeRunGoals(CallbackInfo ci) {
		RitualBand.syncHuntTarget((Mob) (Object) this);
	}

	@Inject(
			method = "serverAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V",
					ordinal = 1,
					shift = At.Shift.BEFORE))
	private void evolvingthreats$ritualHuntBeforeGoalTick(CallbackInfo ci) {
		RitualBand.syncHuntTarget((Mob) (Object) this);
	}

	@Inject(method = "aiStep", at = @At("RETURN"))
	private void evolvingthreats$ritualNoSun(CallbackInfo ci) {
		RitualBand.keepOutOfFire((Mob) (Object) this);
	}
}
