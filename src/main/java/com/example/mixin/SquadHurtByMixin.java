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
 * «Кто ударил — того и бить» у скелета. Если обидчик свой из отряда, цель не берём
 * и соседей на него не натравливаем.
 *
 * Поле mob лежит в родителе TargetGoal. Читаем его через TargetGoalMobAccessor,
 * иначе игра падает при запуске: @Shadow это поле в HurtByTargetGoal не находит.
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
