package com.example.mixin;

import com.example.RandomWeapons;
import com.example.digging.BlockBreaking;
import com.example.entity.ai.BreakToTargetGoal;
import com.example.skeleton.TacticianSkeleton;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 In a dual-wield skeleton, a vanilla melee target is turned on if there is a path to the player (running from 20 blocks).
 Next to the bow - melee does not turn on (shooting). Close onions are extinguished by mixin' onions;
 Here you are allowed to come and hit, otherwise the skeleton “freezes” between 6-7 blocks.
 */
@Mixin(MeleeAttackGoal.class)
public class MeleeAttackGoalMixin {

	@Shadow
	protected PathfinderMob mob;

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$tacticianKeepsChasing(CallbackInfoReturnable<Boolean> cir) {
		// A 700 IQ hits the player, even if he is around the corner.
		if (TacticianSkeleton.isChasingMarked(mob)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$tacticianKeepsChasingContinue(CallbackInfoReturnable<Boolean> cir) {
		if (TacticianSkeleton.isChasingMarked(mob)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$meleeOnlyInRange(CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		// A zombie breaks a wall: a conventional attack sees a snippet of path to the wall and would lead it away from the block.
		// If the player is already there, you can still hit.
		if (mob instanceof Zombie zombie && BreakToTargetGoal.isDigging(zombie)) {
			LivingEntity target = zombie.getTarget();
			if (target == null || !BlockBreaking.shouldPreferMeleeOverDig(zombie, target)) {
				cir.setReturnValue(false);
			}
			return;
		}
		if (!(mob instanceof AbstractSkeleton skeleton)) {
			return;
		}
		if (!RandomWeapons.isDualWieldArcher(skeleton)) {
			return;
		}

		LivingEntity target = mob.getTarget();
		if (target == null) {
			return;
		}

		if (mob.isWithinMeleeAttackRange(target)) {
			return;
		}
		// The onion is already "off" closely - otherwise the skeleton stands without a bow and without a blow.
		if (RandomWeapons.shouldSuppressBow(skeleton, target, false)) {
			return;
		}

		cir.setReturnValue(false);
	}

	@Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$stopMeleeWhenFar(CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		if (!(mob instanceof AbstractSkeleton skeleton)) {
			return;
		}
		if (!RandomWeapons.isDualWieldArcher(skeleton)) {
			return;
		}

		LivingEntity target = mob.getTarget();
		if (target == null) {
			return;
		}

		if (mob.isWithinMeleeAttackRange(target)) {
			return;
		}
		if (RandomWeapons.shouldSuppressBow(skeleton, target, true)) {
			return;
		}

		cir.setReturnValue(false);
	}
}
