package com.example.mixin;

import com.example.entity.ai.TntDropGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The goal of “dropping dynamite” is for all zombies, but is silent until it is a zombie with dynamite.
 * She doesn't know how to break blocks: it decides {@link com.example.digging.BlockBreaking#isBreaker}.
 */
@Mixin(Zombie.class)
public class TntZombieGoalMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$tntDrop(CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		self.getGoalSelector().addGoal(3, new TntDropGoal(self));
	}
}
