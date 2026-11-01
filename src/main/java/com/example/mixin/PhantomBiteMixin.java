package com.example.mixin;

import com.example.phantom.PhantomThreats;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 Phantom bite. It works when it hits the target on a dive.
 The usual mob kick doesn't touch here.
 */
@Mixin(Mob.class)
public class PhantomBiteMixin {

	@ModifyArg(
			method = "doHurtTarget",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			),
			index = 2
	)
	private float evolvingthreats$phantomBite(float damage) {
		Mob mob = (Mob) (Object) this;
		if (!(mob instanceof Phantom phantom)) {
			return damage;
		}
		return PhantomThreats.biteDamage(phantom, damage);
	}
}
