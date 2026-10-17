package com.example.mixin;

import com.example.entity.ZombieOverseerEntity;
import com.example.entity.ai.OverseerHuntGoal;
import com.example.entity.ai.OverseerSummonGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public class ZombieOverseerGoalsMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerOverseerGoals(CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		if (!(self instanceof ZombieOverseerEntity overseer)) {
			return;
		}
		self.getGoalSelector().addGoal(1, new OverseerHuntGoal(self, overseer));
		self.getGoalSelector().addGoal(3, new OverseerSummonGoal(self, overseer));
	}
}
