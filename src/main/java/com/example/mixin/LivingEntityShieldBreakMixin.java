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
 * Срабатывает, когда живой моб уже принял удар и щит часть урона отбил.
 * Если источник — крипер-ломатель, щит в руке уничтожается.
 * Само число заблокированного урона не меняется: жизни от этого взрыва не снимаются.
 * Если игрок смотрел не на взрыв, щит удар не отбил и этот код не ломает его.
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
