package com.example.mixin;

import com.example.skeleton.BasiliskSkeleton;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 Experience, tag and Basilisk's skull. Onions and Neserite reset before the game drops them.
 Both methods are announced by Mob, Skeleton has no copies.
 */
@Mixin(Mob.class)
public class BasiliskLootMixin {

	@Inject(method = "getBaseExperienceReward", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$basiliskXp(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
		int xp = BasiliskSkeleton.experienceReward((Mob) (Object) this);
		if (xp >= 0) {
			cir.setReturnValue(xp);
		}
	}

	@Inject(method = "dropCustomDeathLoot", at = @At("HEAD"))
	private void evolvingthreats$basiliskKeepGear(ServerLevel level, DamageSource source, boolean recentlyHit, CallbackInfo ci) {
		BasiliskSkeleton.preventDrop((Mob) (Object) this);
	}

	@Inject(method = "dropCustomDeathLoot", at = @At("RETURN"))
	private void evolvingthreats$basiliskLoot(ServerLevel level, DamageSource source, boolean recentlyHit, CallbackInfo ci) {
		BasiliskSkeleton.dropBonus((Mob) (Object) this, level);
	}
}
