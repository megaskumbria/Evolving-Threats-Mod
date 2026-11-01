package com.example.mixin;

import com.example.skeleton.SkeletonPunchBow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 Mob items fall inside dropCustomDeathLoot, method announced by Mob
 Before that, zero chance of a bow with a Retraction V or VI, otherwise it may fall to the player.
 */
@Mixin(Mob.class)
public class SkeletonPunchBowMixin {

	@Inject(method = "dropCustomDeathLoot", at = @At("HEAD"))
	private void evolvingthreats$keepHighPunchBow(ServerLevel level, DamageSource source, boolean recentlyHit, CallbackInfo ci) {
		SkeletonPunchBow.preventDrop((Mob) (Object) this);
	}
}
