package com.example.mixin;

import com.example.RandomWeapons;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * У dual-wield скелета ванильная melee-цель включается, если есть путь к игроку (бег с 20 блоков).
 * Здесь: ближний бой только в настоящем радиусе удара — иначе при отбегании снова стреляет.
 */
@Mixin(MeleeAttackGoal.class)
public class MeleeAttackGoalMixin {

	@Shadow
	protected PathfinderMob mob;

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$meleeOnlyInRange(CallbackInfoReturnable<Boolean> cir) {
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

		if (!mob.isWithinMeleeAttackRange(target)) {
			cir.setReturnValue(false);
		}
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

		if (!mob.isWithinMeleeAttackRange(target)) {
			cir.setReturnValue(false);
		}
	}
}
