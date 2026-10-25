package com.example.mixin;

import com.example.skeleton.SkeletonDisarm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Попадание стрелы. Срабатывает, когда удар уже снял здоровье.
 * Обычная, ядовитая и спектральная стрела зовут этот общий метод.
 * Числа — {@link SkeletonDisarm}.
 */
@Mixin(AbstractArrow.class)
public class SkeletonDisarmMixin {

	@Inject(method = "doPostHurtEffects", at = @At("HEAD"))
	private void evolvingthreats$disarm(LivingEntity target, CallbackInfo ci) {
		if (target instanceof Player player) {
			SkeletonDisarm.onArrowHit((AbstractArrow) (Object) this, player);
		}
	}
}
