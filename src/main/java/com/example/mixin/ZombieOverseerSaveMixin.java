package com.example.mixin;

import com.example.MobNames;
import com.example.entity.ZombieOverseerEntity;
import com.example.overseer.ZombieOverseer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Zombie.class)
public class ZombieOverseerSaveMixin {

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveOverseer(ValueOutput output, CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		if (!(self instanceof ZombieOverseerEntity data)) {
			return;
		}
		output.putBoolean("EvolvingThreatsOverseer", data.evolvingthreats$isOverseer());
		if (data.evolvingthreats$getHuntTargetId() != null) {
			output.putString("EvolvingThreatsHuntTarget", data.evolvingthreats$getHuntTargetId().toString());
		}
		output.putLong("EvolvingThreatsHuntStarted", data.evolvingthreats$getHuntStartedGameTime());
		output.putInt("EvolvingThreatsServants", data.evolvingthreats$getActiveServantCount());
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadOverseer(ValueInput input, CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		if (!(self instanceof ZombieOverseerEntity data)) {
			return;
		}
		data.evolvingthreats$setOverseer(input.getBooleanOr("EvolvingThreatsOverseer", false));
		input.getString("EvolvingThreatsHuntTarget").ifPresent(s -> data.evolvingthreats$setHuntTargetId(UUID.fromString(s)));
		data.evolvingthreats$setHuntStartedGameTime(input.getLongOr("EvolvingThreatsHuntStarted", 0L));
		data.evolvingthreats$setActiveServantCount(input.getIntOr("EvolvingThreatsServants", 0));
		if (data.evolvingthreats$isOverseer()) {
			MobNames.showOnlyWhenLookedAt(self);
			ZombieOverseer.raiseStoredHealth(self);
		}
	}
}
