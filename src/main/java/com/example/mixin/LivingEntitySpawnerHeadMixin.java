package com.example.mixin;

import com.example.spawnerhead.SpawnerHeadMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Считает удары игрока по носителю спавнера (после успешного hurtServer).
 */
@Mixin(LivingEntity.class)
public class LivingEntitySpawnerHeadMixin {

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void evolvingthreats$spawnerHeadOnHit(
			ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof Mob mob) {
			SpawnerHeadMob.onPlayerHit(mob, source);
		}
	}
}
