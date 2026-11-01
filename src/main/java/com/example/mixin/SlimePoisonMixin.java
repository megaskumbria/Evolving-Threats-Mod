package com.example.mixin;

import com.example.magma.MagmaCubeThreats;
import com.example.slime.SlimePoison;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 The impact is declared at the common cube, neither the slug nor the magma cube has its copy.
 It works only if the blow relieved health: the shield and blinking after the impact do not give anything.
 The poison is just a slug. Fire and increased damage are just a magma cube.
 */
@Mixin(AbstractCubeMob.class)
public class SlimePoisonMixin {

	@Redirect(
			method = "dealDamage",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean evolvingthreats$cubeHit(LivingEntity target, ServerLevel level, DamageSource source, float damage) {
		float dealt = damage;
		if ((Object) this instanceof MagmaCube cube) {
			dealt = MagmaCubeThreats.scaleHit(cube, damage);
		}
		boolean hit = target.hurtServer(level, source, dealt);
		if (hit && (Object) this instanceof Slime slime) {
			SlimePoison.onHit(slime, target);
		}
		if (hit && (Object) this instanceof MagmaCube cube) {
			MagmaCubeThreats.onHit(cube, target);
		}
		return hit;
	}
}
