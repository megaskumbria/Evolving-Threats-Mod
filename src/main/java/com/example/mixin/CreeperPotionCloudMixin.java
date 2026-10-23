package com.example.mixin;

import com.example.MobPotionBuffs;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Крипер с зельем при взрыве оставляет туман и копирует в него эффекты с себя.
 * Бесконечный баф мода здесь подменяется на короткое время, пока туман собирается.
 */
@Mixin(Creeper.class)
public class CreeperPotionCloudMixin {

	@ModifyArg(
			method = "spawnLingeringCloud",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/AreaEffectCloud;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)V"))
	private MobEffectInstance evolvingthreats$finiteCloudEffect(MobEffectInstance effect) {
		return MobPotionBuffs.forDeathCloud(effect);
	}
}
