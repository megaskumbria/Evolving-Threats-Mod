package com.example.mixin;

import com.example.entity.ai.ZombiePlayerGrudgeGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Память об игроке — приоритет 0 (выше атаки 3 и света 5).
 */
@Mixin(Zombie.class)
public class ZombieGrudgeGoalsMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerGrudgeGoal(CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		self.getGoalSelector().addGoal(0, new ZombiePlayerGrudgeGoal(self));
	}
}
