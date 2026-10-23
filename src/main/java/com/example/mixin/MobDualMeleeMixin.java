package com.example.mixin;

import com.example.RandomWeapons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Удар моба считает только основную руку. Здесь к нему прибавляется оружие из второй.
 * Срабатывает в момент удара, один раз на замах.
 */
@Mixin(Mob.class)
public class MobDualMeleeMixin {

	@ModifyArg(
			method = "doHurtTarget",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			),
			index = 2
	)
	private float evolvingthreats$addOffhandDamage(float damage) {
		Mob mob = (Mob) (Object) this;
		if (!(mob.level() instanceof ServerLevel level)) {
			return damage;
		}
		Entity target = mob.getTarget();
		if (target == null) {
			return damage;
		}
		return damage + RandomWeapons.offhandMeleeDamage(level, mob, target);
	}

	@Inject(method = "doHurtTarget", at = @At("RETURN"))
	private void evolvingthreats$offhandOnHit(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			RandomWeapons.applyOffhandOnHit(level, (Mob) (Object) this, target);
		}
	}
}
