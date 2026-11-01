package com.example.mixin;

import com.example.skeleton.BasiliskSkeleton;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 The shield has already repulsed. If it's Basiliska's arrow, the impact builds up on the shield.
 If the player did not look at the arrow, the shield did not repel it and the blow does not fall here.
 */
@Mixin(LivingEntity.class)
public class BasiliskShieldMixin {

	@Inject(method = "applyItemBlocking", at = @At("RETURN"))
	private void evolvingthreats$basiliskShield(
			ServerLevel level,
			DamageSource source,
			float damage,
			CallbackInfoReturnable<Float> cir) {
		if (cir.getReturnValue() <= 0.0F) {
			return;
		}
		if ((Object) this instanceof Player player) {
			BasiliskSkeleton.onShieldBlock(player, source);
		}
	}
}
