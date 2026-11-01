package com.example.mixin;

import com.example.vex.VexDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Vex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blow from the molester. It works when it touches the target.
 Blows of other mobs do not change here.
 */
@Mixin(Mob.class)
public class VexDamageMixin {

	@ModifyArg(
			method = "doHurtTarget",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			),
			index = 2
	)
	private float evolvingthreats$vexDamage(float damage) {
		Mob mob = (Mob) (Object) this;
		if (!(mob instanceof Vex vex)) {
			return damage;
		}
		return VexDamage.attackDamage(vex, damage);
	}

	/**
	 Weakness only if the blow has already passed. A shielded and blinked blow does not hang it.
	 */
	@Inject(method = "doHurtTarget", at = @At("RETURN"))
	private void evolvingthreats$vexWeakness(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		if ((Object) this instanceof Vex vex && target instanceof LivingEntity living) {
			VexDamage.onHit(vex, living);
		}
	}
}
