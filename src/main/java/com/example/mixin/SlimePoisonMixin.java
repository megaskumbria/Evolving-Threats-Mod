package com.example.mixin;

import com.example.slime.SlimePoison;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Удар слизня. Метод объявлен у общего куба, свой копии у слизня нет.
 * Яд вешается только если удар снял здоровье: щит и мигание после удара шанс не бросают.
 */
@Mixin(AbstractCubeMob.class)
public class SlimePoisonMixin {

	@Redirect(
			method = "dealDamage",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean evolvingthreats$slimePoison(LivingEntity target, ServerLevel level, DamageSource source, float damage) {
		boolean hit = target.hurtServer(level, source, damage);
		if (hit && (Object) this instanceof Slime slime) {
			SlimePoison.onHit(slime, target);
		}
		return hit;
	}
}
