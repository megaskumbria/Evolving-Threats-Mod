package com.example.mixin;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.ai.RaidHuntGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSkeleton.class)
public class AbstractSkeletonRaidGoalsMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerRaidGoals(CallbackInfo ci) {
		AbstractSkeleton self = (AbstractSkeleton) (Object) this;
		if (!(self instanceof RaidParticipantEntity raid) || !raid.evolvingthreats$isRaidMinion()) {
			return;
		}
		self.getGoalSelector().addGoal(1, new RaidHuntGoal(self, raid));
	}
}
