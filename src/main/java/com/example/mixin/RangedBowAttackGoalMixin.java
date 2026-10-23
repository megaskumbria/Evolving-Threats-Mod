package com.example.mixin;

import com.example.RandomWeapons;
import com.example.entity.FastBowSkeletonEntity;
import com.example.skeleton.FastBowSkeleton;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dual-wield скелет: не стреляет в упор; при начале стрельбы лук снова в основной руке (анимация).
 */
@Mixin(net.minecraft.world.entity.ai.goal.RangedBowAttackGoal.class)
public class RangedBowAttackGoalMixin {

	@Shadow
	@Final
	private Monster mob;

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$deferBowWhenClose(CallbackInfoReturnable<Boolean> cir) {
		suppressBowIfTooClose(cir, false);
	}

	@Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$stopBowWhenClose(CallbackInfoReturnable<Boolean> cir) {
		suppressBowIfTooClose(cir, true);
	}

	@Inject(method = "start", at = @At("RETURN"))
	private void evolvingthreats$prepareBowHand(CallbackInfo ci) {
		if (mob instanceof AbstractSkeleton skeleton && RandomWeapons.isDualWieldArcher(skeleton)) {
			RandomWeapons.swapToRangedHands(skeleton);
			skeleton.stopUsingItem();
		}
	}

	/** Обычное натяжение — 20 тиков. У дальнего скелета короче, стрела при этом всё равно полная. */
	@ModifyConstant(
			method = "tick",
			constant = @Constant(intValue = 20),
			slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getTicksUsingItem()I"))
	)
	private int evolvingthreats$drawTicks(int ticks) {
		if (mob instanceof FastBowSkeletonEntity data && data.evolvingthreats$isFastBow()) {
			return FastBowSkeleton.DRAW_TICKS;
		}
		return ticks;
	}

	@ModifyArg(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/monster/RangedAttackMob;performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V"
			),
			index = 1
	)
	private float evolvingthreats$fullDrawPower(float power) {
		if (mob instanceof FastBowSkeletonEntity data && data.evolvingthreats$isFastBow()) {
			return 1.0f;
		}
		return power;
	}

	private void suppressBowIfTooClose(CallbackInfoReturnable<Boolean> cir, boolean wasActive) {
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

		if (RandomWeapons.shouldSuppressBow(skeleton, target, wasActive)) {
			cir.setReturnValue(false);
		}
	}
}
