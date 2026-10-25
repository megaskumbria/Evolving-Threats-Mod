package com.example.mixin;

import com.example.entity.FastBowSkeletonEntity;
import com.example.skeleton.FastBowSkeleton;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * После того как игра выставила обычную стрельбу, дальний скелет получает свою дальность и паузу.
 * Скорость стрелы в ванили всегда 1.6 — здесь она выше, чтобы долетать.
 */
@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonFastBowMixin {

	@Shadow
	@Final
	private RangedBowAttackGoal<AbstractSkeleton> bowGoal;

	@Inject(method = "reassessWeaponGoal", at = @At("RETURN"))
	private void evolvingthreats$applyFastBow(CallbackInfo ci) {
		FastBowSkeleton.apply((AbstractSkeleton) (Object) this, bowGoal);
	}

	@ModifyArg(
			method = "performRangedAttack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"
			),
			index = 6
	)
	private float evolvingthreats$arrowSpeed(float speed) {
		if (isFastBow()) {
			return FastBowSkeleton.ARROW_SPEED;
		}
		return speed;
	}

	/**
	 * Надбавка вверх у обычного скелета: в исходниках это 0.2, но игра хранит его
	 * длинным числом, потому что умножает на расстояние.
	 * Это число менять нельзя — иначе мод не запустится. Прицел быстрого лука
	 * считается в {@link FastBowSkeleton#arrowArc()} из скорости стрелы.
	 */
	@ModifyConstant(method = "performRangedAttack", constant = @Constant(doubleValue = 0.20000000298023224))
	private double evolvingthreats$arrowArc(double arc) {
		if (isFastBow()) {
			return FastBowSkeleton.arrowArc();
		}
		return arc;
	}

	private boolean isFastBow() {
		AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;
		return skeleton instanceof FastBowSkeletonEntity data && data.evolvingthreats$isFastBow();
	}
}
