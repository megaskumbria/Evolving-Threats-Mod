package com.example.mixin;

import com.example.witch.WitchThrownPotions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Witch ranged attack when the potion stack is chosen and launched.
 * Here it may become a custom effect or a fog potion.
 * Ally healing and plain white potions pass through unchanged.
 */
@Mixin(Witch.class)
public class WitchThrownPotionMixin {

	@Redirect(
			method = "performRangedAttack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile$ProjectileFactory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"
			)
	)
	private Projectile evolvingthreats$witchThrow(
			Projectile.ProjectileFactory creator,
			ServerLevel level,
			ItemStack stack,
			LivingEntity source,
			double x,
			double y,
			double z,
			float power,
			float uncertainty) {
		return WitchThrownPotions.launch(creator, level, stack, source, x, y, z, power, uncertainty);
	}
}
