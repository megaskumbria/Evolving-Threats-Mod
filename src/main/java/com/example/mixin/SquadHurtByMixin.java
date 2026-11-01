package com.example.mixin;

import com.example.skeleton.SkeletonCaptain;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 “Who hits, and hits” at the skeleton. If the offender is from the squad, we do not take the goal.
 And we don't incite our neighbors.
 *
 The mob field is in the parent of TargetGoal. Read it through TargetGoalMobAccessor.
 * Otherwise, the game drops at launch: @Shadow does not find this field in HurtByTargetGoal.
 */
@Mixin(HurtByTargetGoal.class)
public class SquadHurtByMixin {

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$ignoreSquadAttacker(CallbackInfoReturnable<Boolean> cir) {
		Mob mob = ((TargetGoalMobAccessor) (Object) this).evolvingthreats$getMob();
		LivingEntity attacker = mob.getLastHurtByMob();
		if (attacker != null && SkeletonCaptain.isSquadAlly(mob, attacker)) {
			cir.setReturnValue(false);
		}
	}
}
