package com.example.mixin;

import com.example.blaze.BlazeAlchemist;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The moment the Blaze shot and the ball hit.
 The alchemical ball on the outside is the same as the usual: damage, arson and fire on the block remain.
 * In addition, when hit, the fire resistance is cut off.
 */
@Mixin(SmallFireball.class)
public class SmallFireballAlchemyMixin {

	@Unique
	private boolean evolvingthreats$alchemy;

	@Inject(
			method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/Vec3;)V",
			at = @At("RETURN"))
	private void evolvingthreats$markAlchemyShot(Level level, LivingEntity owner, Vec3 movement, CallbackInfo ci) {
		if (owner instanceof Blaze blaze && BlazeAlchemist.claimShot(blaze)) {
			evolvingthreats$alchemy = true;
		}
	}

	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void evolvingthreats$alchemyHitEntity(EntityHitResult result, CallbackInfo ci) {
		if (!evolvingthreats$alchemy) {
			return;
		}
		BlazeAlchemist.onHitEntity((SmallFireball) (Object) this, result.getEntity());
	}
}
