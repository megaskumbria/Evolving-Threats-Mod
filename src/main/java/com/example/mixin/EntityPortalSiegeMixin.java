package com.example.mixin;

import com.example.siege.SiegeNight;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Игра спрашивает у сущности «можешь ли ты сейчас пройти через портал», пока та стоит в портале.
 * Во время осады игроку в верхнем мире отвечаем «нет»: портал в Ад или Энд не срабатывает.
 * Решает {@link SiegeNight#blocksPortal}: только игроки верхнего мира, и только пока идёт осада.
 * Мобы и предметы, а также игроки в Аду и Энде не затронуты.
 */
@Mixin(Entity.class)
public class EntityPortalSiegeMixin {

	@Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$siegeBlocksPortal(boolean allowPassengers, CallbackInfoReturnable<Boolean> cir) {
		if (SiegeNight.blocksPortal((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
