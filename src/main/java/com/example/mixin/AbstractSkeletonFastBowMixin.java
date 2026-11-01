package com.example.mixin;

import com.example.entity.BasiliskSkeletonEntity;
import com.example.entity.FastBowSkeletonEntity;
import com.example.entity.SniperSkeletonEntity;
import com.example.skeleton.BasiliskSkeleton;
import com.example.skeleton.FastBowSkeleton;
import com.example.skeleton.SniperSkeleton;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
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
		AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;
		// Сначала быстрый лук, потом снайпер: дальность 30 не должна затирать 128.
		FastBowSkeleton.apply(skeleton, bowGoal);
		SniperSkeleton.apply(skeleton, bowGoal);
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
		if (isBasilisk()) {
			return BasiliskSkeleton.ARROW_SPEED;
		}
		if (isSniper()) {
			return SniperSkeleton.ARROW_SPEED;
		}
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
		if (isBasilisk()) {
			return BasiliskSkeleton.arrowArc();
		}
		if (isFastBow()) {
			return FastBowSkeleton.arrowArc();
		}
		return arc;
	}

	/**
	 * Прицел снайпера. Одно число задира на дальней дистанции поднимает стрелу над головой,
	 * поэтому направление считается заново под текущее расстояние.
	 */
	@ModifyArg(
			method = "performRangedAttack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"
			),
			index = 4
	)
	private double evolvingthreats$sniperAim(double directionY) {
		if (!isSniper()) {
			return directionY;
		}
		AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;
		LivingEntity target = skeleton.getTarget();
		if (target == null) {
			return directionY;
		}
		return SniperSkeleton.launchDirectionY(skeleton, target);
	}

	/** Урон не растёт от скорости полёта: иначе быстрая стрела убивает даже в незерите. */
	@ModifyArg(
			method = "performRangedAttack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"
			),
			index = 0
	)
	private Projectile evolvingthreats$sniperDamage(Projectile projectile) {
		if (isSniper() && projectile instanceof AbstractArrow arrow) {
			arrow.setBaseDamage(SniperSkeleton.baseDamage());
		}
		return projectile;
	}

	/** Разброс. У снайпера свой: ванильный на 128 блоках промахивается. */
	@ModifyArg(
			method = "performRangedAttack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"
			),
			index = 7
	)
	private float evolvingthreats$sniperInaccuracy(float inaccuracy) {
		if (isSniper()) {
			return Math.max(0.0f, SniperSkeleton.INACCURACY);
		}
		return inaccuracy;
	}

	private boolean isBasilisk() {
		AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;
		return skeleton instanceof BasiliskSkeletonEntity data && data.evolvingthreats$isBasilisk();
	}

	private boolean isFastBow() {
		AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;
		return skeleton instanceof FastBowSkeletonEntity data && data.evolvingthreats$isFastBow();
	}

	private boolean isSniper() {
		AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;
		return skeleton instanceof SniperSkeletonEntity data && data.evolvingthreats$isSniper();
	}
}
