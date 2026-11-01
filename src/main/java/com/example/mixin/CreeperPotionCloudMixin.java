package com.example.mixin;

import com.example.MobPotionBuffs;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 Cryper with a potion during the explosion leaves a fog and copies the effects of himself into it.
 The endless buff fashion here is replaced for a short time while the fog gathers.
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
