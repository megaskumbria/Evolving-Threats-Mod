package com.example.mixin;

import com.example.illager.PillagerFirework;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The crossbow shot has already been counted as a game, with an arc like an arrow.
 * For a robber's rocket, the arc is removed in {@link PillagerFirework#correctAim}.
 */
@Mixin(CrossbowItem.class)
public class PillagerFireworkAimMixin {

	@Inject(method = "shootProjectile", at = @At("RETURN"))
	private void evolvingthreats$aimRocket(
			LivingEntity shooter,
			Projectile projectile,
			int index,
			float power,
			float uncertainty,
			float angle,
			LivingEntity target,
			CallbackInfo ci) {
		PillagerFirework.correctAim(shooter, projectile, target, power, uncertainty, angle);
	}
}
