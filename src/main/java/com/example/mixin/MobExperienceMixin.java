package com.example.mixin;

import com.example.MobExperience;
import com.example.bastion.Bastion;
import com.example.skeleton.WitherSkeletonSword;
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
		Mob mob = (Mob) (Object) this;
		// Своё число Бастиона. Иначе ниже его удвоил бы общий множитель.
		int bastion = Bastion.experienceReward(mob);
		if (bastion >= 0) {
			cir.setReturnValue(bastion);
			return;
		}
		// Своё число иссушителя. Иначе ниже его удвоил бы общий множитель.
		int wither = WitherSkeletonSword.experienceReward(mob);
		if (wither >= 0) {
			cir.setReturnValue(wither);
			return;
		}
		int xp = MobExperience.reward(mob, cir.getReturnValue());
		if (xp >= 0) {
			cir.setReturnValue(xp);
		}
	}
}
