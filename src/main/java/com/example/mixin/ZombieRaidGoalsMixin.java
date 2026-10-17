package com.example.mixin;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.ai.RaidHuntGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public class ZombieRaidGoalsMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerRaidGoals(CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		if (!(self instanceof RaidParticipantEntity raid) || !raid.evolvingthreats$isRaidParticipant()) {
			return;
		}
		self.getGoalSelector().addGoal(1, new RaidHuntGoal(self, raid));
	}
}
