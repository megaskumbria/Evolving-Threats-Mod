package com.example.mixin;

import com.example.illager.PillagerFirework;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After a rocket has already dealt its explosion damage.
 * A rocket that missed, or one a player fired, does not count.
 */
@Mixin(FireworkRocketEntity.class)
public class PillagerFireworkHitMixin {

	@Inject(method = "dealExplosionDamage", at = @At("RETURN"))
	private void evolvingthreats$rocketHit(ServerLevel level, CallbackInfo ci) {
		PillagerFirework.onExplosionHit((FireworkRocketEntity) (Object) this, level);
	}
}
