package com.example.mixin;

import com.example.entity.ai.BreakToTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the dig-toward-target goal for zombies.
 * Who can break blocks (not drowned or piglins, etc.) — {@link com.example.digging.BlockBreaking#isBreaker}.
 */
@Mixin(Zombie.class)
public class ZombieBreakGoalsMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerBreakGoal(CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		self.getGoalSelector().addGoal(4, new BreakToTargetGoal(self));
	}
}
