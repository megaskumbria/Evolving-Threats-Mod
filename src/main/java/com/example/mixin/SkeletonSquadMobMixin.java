package com.example.mixin;

import com.example.skeleton.SkeletonCaptain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 Common mob tick: a squad of skeletons shares a target, the captain drops his loot.
 Methods announced by Mob, Skeleton has no copies.
 */
@Mixin(Mob.class)
public class SkeletonSquadMobMixin {

	@Inject(method = "customServerAiStep", at = @At("RETURN"))
	private void evolvingthreats$shareSquadTarget(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof Skeleton skeleton) {
			SkeletonCaptain.shareTarget(level, skeleton);
		}
	}

	@Inject(method = "dropCustomDeathLoot", at = @At("RETURN"))
	private void evolvingthreats$captainLoot(ServerLevel level, DamageSource source, boolean recentlyHit, CallbackInfo ci) {
		if ((Object) this instanceof Skeleton skeleton) {
			SkeletonCaptain.dropBonus(level, skeleton);
		}
	}
}
