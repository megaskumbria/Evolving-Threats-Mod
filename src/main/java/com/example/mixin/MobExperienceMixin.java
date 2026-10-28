package com.example.mixin;

import com.example.MobExperience;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Удваивает опыт любого враждебного моба. Игра зовёт этот метод при смерти.
 * У зомби свой такой же метод: он сначала считает опыт, потом вызывает этот.
 * Поэтому множитель стоит здесь, а не в каждом зомби отдельно.
 */
@Mixin(Mob.class)
public class MobExperienceMixin {

	@Inject(method = "getBaseExperienceReward", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$bonusXp(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
		int xp = MobExperience.reward((Mob) (Object) this, cir.getReturnValue());
		if (xp >= 0) {
			cir.setReturnValue(xp);
		}
	}
}
