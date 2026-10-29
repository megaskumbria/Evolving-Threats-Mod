package com.example.mixin;

import com.example.spider.CaveSpiderPoison;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.PoisonMobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Тик любого отравления. Пока здоровья больше половины сердца, урон наносит игра.
 * Если яд повесил пещерный паук, этот же тик бьёт и на половине сердца — и может убить.
 */
@Mixin(PoisonMobEffect.class)
public class PoisonTickMixin {

	@Inject(method = "applyEffectTick", at = @At("HEAD"))
	private void evolvingthreats$lethalCavePoison(
			ServerLevel level,
			LivingEntity entity,
			int amplifier,
			CallbackInfoReturnable<Boolean> cir) {
		CaveSpiderPoison.onPoisonTick(level, entity);
	}
}
