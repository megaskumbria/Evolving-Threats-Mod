package com.example.mixin;

import com.example.skeleton.SkeletonDisarm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Попадание стрелы.
 * Обычный удар зовёт общий метод, когда здоровье уже снято.
 * В креативе здоровье не снимается, поэтому там проверка идёт с самого попадания.
 * Щит по-прежнему удар не пропускает. Числа — {@link SkeletonDisarm}.
 */
@Mixin(AbstractArrow.class)
public class SkeletonDisarmMixin {

	@Inject(method = "doPostHurtEffects", at = @At("HEAD"))
	private void evolvingthreats$disarm(LivingEntity target, CallbackInfo ci) {
		if (target instanceof Player player) {
			SkeletonDisarm.onArrowHit((AbstractArrow) (Object) this, player);
		}
	}

	/**
	 * Креатив и наблюдатель не получают урон, и метод выше для них не вызывается.
	 * Наблюдателя не трогаем. Щит в креативе тоже отбивает стрелу — руки остаются.
	 */
	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void evolvingthreats$disarmCreative(EntityHitResult hit, CallbackInfo ci) {
		if (!(hit.getEntity() instanceof Player player) || player.isSpectator()) {
			return;
		}
		if (!player.getAbilities().invulnerable || player.isBlocking()) {
			return;
		}
		SkeletonDisarm.onArrowHit((AbstractArrow) (Object) this, player);
	}
}
