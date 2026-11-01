package com.example.mixin;

import com.example.grudge.PlayerGrudge;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 ServerAiStep announced in Mob, not in Zombie - mixin hang on Mob, works only for zombies.
 */
@Mixin(Mob.class)
public abstract class MobGrudgeAiMixin {

	@Inject(
			method = "serverAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V",
					ordinal = 1,
					shift = At.Shift.BEFORE))
	private void evolvingthreats$syncGrudgeBeforeRunGoals(CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (self instanceof Zombie zombie) {
			PlayerGrudge.syncTarget(zombie);
		}
	}

	@Inject(
			method = "serverAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V",
					ordinal = 1,
					shift = At.Shift.BEFORE))
	private void evolvingthreats$syncGrudgeBeforeGoalTick(CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (self instanceof Zombie zombie) {
			PlayerGrudge.syncTarget(zombie);
		}
	}
}
