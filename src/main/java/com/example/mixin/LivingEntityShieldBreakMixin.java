package com.example.mixin;

import com.example.creeper.ShieldBreakCreeper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 It works when the live mob has already taken a hit and the shield has recaptured some of the damage.
 If the source is a cryper-breaker, the shield in the hand is destroyed.
 The number of blocked damage does not change: lives from this explosion are not removed.
 If the player did not look at the explosion, the shield did not hit and this code does not break it.
 */
@Mixin(LivingEntity.class)
public class LivingEntityShieldBreakMixin {

	@Inject(method = "applyItemBlocking", at = @At("RETURN"))
	private void evolvingthreats$breakShieldOnCreeperExplosion(
			ServerLevel level,
			DamageSource source,
			float damage,
			CallbackInfoReturnable<Float> cir) {
		if (cir.getReturnValue() <= 0.0F) {
			return;
		}
		if (!(source.getDirectEntity() instanceof Creeper creeper) || !ShieldBreakCreeper.isShieldBreak(creeper)) {
			return;
		}
		if ((Object) this instanceof Player player) {
			ShieldBreakCreeper.breakBlockingShield(player);
		}
	}
}
