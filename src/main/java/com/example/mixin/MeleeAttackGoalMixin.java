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
 * У dual-wield скелета ванильная melee-цель включается, если есть путь к игроку (бег с 20 блоков).
 * Дальше лука — melee не включается (стреляет). Вплотную лук гасится mixin'ом лука;
 * здесь разрешаем подойти и ударить, иначе скелет «замирает» между 4–5 блоками.
 */
@Mixin(MeleeAttackGoal.class)
public class MeleeAttackGoalMixin {

	@Shadow
	protected PathfinderMob mob;

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$tacticianKeepsChasing(CallbackInfoReturnable<Boolean> cir) {
		// «700 IQ» после попадания бежит на игрока, даже если тот за углом.
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
		// Зомби ломает стену: обычная атака видит обрывок пути до стены и уводила бы его от блока.
		// Ударить, если игрок уже рядом, по-прежнему можно.
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
		// Лук уже «выключен» вплотную — иначе скелет стоит без лука и без удара.
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
