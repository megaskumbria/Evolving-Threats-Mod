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
 Tick of any poisoning. As long as the health is more than half the heart, the damage is caused by the game.
 If the poison is hanged by a cave spider, the same tick strikes on the half of the heart - and can kill.
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
