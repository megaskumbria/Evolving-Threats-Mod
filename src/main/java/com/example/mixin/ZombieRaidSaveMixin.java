package com.example.mixin;

import com.example.MobNames;
import com.example.entity.RaidParticipantEntity;
import com.example.raid.RaidAnchor;
import com.example.raid.RaidRegistry;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Zombie.class)
public class ZombieRaidSaveMixin {

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveRaid(ValueOutput output, CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		if (!(self instanceof RaidParticipantEntity raid) || !raid.evolvingthreats$isRaidParticipant()) {
			return;
		}
		output.putBoolean("EvolvingThreatsRaidAnchor", raid.evolvingthreats$isRaidAnchor());
		output.putBoolean("EvolvingThreatsRaidMinion", raid.evolvingthreats$isRaidMinion());
		output.putBoolean("EvolvingThreatsRaidGps", raid.evolvingthreats$isRaidGpsActive());
		if (raid.evolvingthreats$getRaidId() != null) {
			output.putString("EvolvingThreatsRaidId", raid.evolvingthreats$getRaidId().toString());
		}
		if (raid.evolvingthreats$getRaidHuntTargetId() != null) {
			output.putString("EvolvingThreatsRaidHunt", raid.evolvingthreats$getRaidHuntTargetId().toString());
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadRaid(ValueInput input, CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		if (!(self instanceof RaidParticipantEntity raid)) {
			return;
		}
		raid.evolvingthreats$setRaidAnchor(input.getBooleanOr("EvolvingThreatsRaidAnchor", false));
		raid.evolvingthreats$setRaidMinion(input.getBooleanOr("EvolvingThreatsRaidMinion", false));
		raid.evolvingthreats$setRaidGpsActive(input.getBooleanOr("EvolvingThreatsRaidGps", false));
		input.getString("EvolvingThreatsRaidId").ifPresent(s -> raid.evolvingthreats$setRaidId(UUID.fromString(s)));
		input.getString("EvolvingThreatsRaidHunt").ifPresent(s -> raid.evolvingthreats$setRaidHuntTargetId(UUID.fromString(s)));
		if (raid.evolvingthreats$isRaidAnchor()) {
			MobNames.showOnlyWhenLookedAt(self);
			RaidAnchor.raiseStoredHealth(self);
			if (raid.evolvingthreats$getRaidId() != null) {
				RaidRegistry.registerAnchor(raid.evolvingthreats$getRaidId(), self.getId());
			}
		}
	}
}
