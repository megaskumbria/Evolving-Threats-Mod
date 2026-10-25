package com.example.mixin;

import com.example.entity.ai.VindicatorChopGoal;
import net.minecraft.world.entity.monster.illager.Vindicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Поборник получает цель «рубить дерево на пути».
 * Что считается деревом и как быстро — {@link com.example.illager.VindicatorWoodBreak}.
 */
@Mixin(Vindicator.class)
public class VindicatorChopMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$chopWood(CallbackInfo ci) {
		Vindicator self = (Vindicator) (Object) this;
		// 4 — выше обычного удара (5), ниже ломания дверей в рейде (2).
		self.getGoalSelector().addGoal(4, new VindicatorChopGoal(self));
	}
}
