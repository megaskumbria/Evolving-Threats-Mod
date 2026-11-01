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
 After the game has set the usual shooting, the distant skeleton gets its range and pause.
 The speed of the arrow in the vanilla is always 1.6 - here it is higher to fly.
 */
@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonFastBowMixin {

	@Shadow
	@Final
	private RangedBowAttackGoal<AbstractSkeleton> bowGoal;

	@Inject(method = "reassessWeaponGoal", at = @At("RETURN"))
	private void evolvingthreats$applyFastBow(CallbackInfo ci) {
		AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;
		// First fast bow, then sniper: range 30 should not wipe 128.
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
	 The premium up for a normal skeleton: in the source it is 0.2, but the game stores it
	 A long number because it multiplies by distance.
	 This number cannot be changed - otherwise the mod will not start. Fast bow sight
	 * counts in {@link FastBowSkeleton#arrowArc()} from arrow speed.
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
	 Sniper sight. One bully number at a distance raises an arrow over his head.
	 * Therefore, the direction is counted again at the current distance.
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

	/*Damage does not increase with the speed of flight: otherwise, a fast arrow kills even in nezerite. */
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

	/** Scatter. The sniper has his own: vanilla on 128 blocks misses. */
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
