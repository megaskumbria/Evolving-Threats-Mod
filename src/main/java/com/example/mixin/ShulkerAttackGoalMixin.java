package com.example.mixin;

import com.example.shulker.ShulkerThreats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Момент, когда шалкер выпускает снаряд.
 * Цель атаки — внутренний класс шалкера, своего файла у неё нет.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Shulker$ShulkerAttackGoal")
public class ShulkerAttackGoalMixin {

	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
			)
	)
	private boolean evolvingthreats$launchBullet(Level level, Entity entity) {
		return ShulkerThreats.launch(level, entity);
	}
}
