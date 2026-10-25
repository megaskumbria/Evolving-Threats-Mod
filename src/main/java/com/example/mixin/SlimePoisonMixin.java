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
 * Удар объявлен у общего куба, своей копии нет ни у слизня, ни у магмового куба.
 * Срабатывает только если удар снял здоровье: щит и мигание после удара ничего не дают.
 * Яд — только слизень. Огонь и усиленный урон — только магмовый куб.
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
