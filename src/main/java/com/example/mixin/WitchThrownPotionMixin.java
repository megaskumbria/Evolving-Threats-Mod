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
 * Момент броска ведьмы, когда бутылка уже выбрана и вылетает.
 * Здесь она может стать новым эффектом или туманной.
 * Лечение союзника и белое зелье этот вызов не трогает: их возвращают как есть.
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
	@SuppressWarnings({ "unchecked", "rawtypes" })
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
