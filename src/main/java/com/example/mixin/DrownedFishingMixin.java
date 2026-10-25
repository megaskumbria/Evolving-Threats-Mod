package com.example.mixin;

import com.example.entity.ai.DrownedFishingGoal;
import net.minecraft.world.entity.monster.zombie.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * К списку поведения утопленника добавляется заброс удочки.
 * Удар вблизи остаётся его обычной атакой.
 */
@Mixin(Drowned.class)
public class DrownedFishingMixin {

	@Inject(method = "addBehaviourGoals", at = @At("RETURN"))
	private void evolvingthreats$fishingRod(CallbackInfo ci) {
		Drowned drowned = (Drowned) (Object) this;
		drowned.goalSelector.addGoal(2, new DrownedFishingGoal(drowned));
	}
}
