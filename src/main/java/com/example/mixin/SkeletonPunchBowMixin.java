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
 * Вещи моба падают внутри dropCustomDeathLoot, метод объявлен у Mob.
 * До этого обнуляем шанс лука с Откидыванием V или VI, иначе он может упасть игроку.
 */
@Mixin(Mob.class)
public class SkeletonPunchBowMixin {

	@Inject(method = "dropCustomDeathLoot", at = @At("HEAD"))
	private void evolvingthreats$keepHighPunchBow(ServerLevel level, DamageSource source, boolean recentlyHit, CallbackInfo ci) {
		SkeletonPunchBow.preventDrop((Mob) (Object) this);
	}
}
