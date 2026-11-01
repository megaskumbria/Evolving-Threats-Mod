package com.example.mixin;

import com.example.entity.RaidParticipantEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Preservation of raid data on skeleton servants.
 * {@code addAdditionalSaveData} is available only from {@link Skeleton}, not from {@link net.minecraft.world.entity.monster.skeleton.AbstractSkeleton}.
 */
@Mixin(Skeleton.class)
public class SkeletonRaidSaveMixin {

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveRaid(ValueOutput output, CallbackInfo ci) {
		Skeleton self = (Skeleton) (Object) this;
		if (!(self instanceof RaidParticipantEntity raid) || !raid.evolvingthreats$isRaidMinion()) {
			return;
		}
		output.putBoolean("EvolvingThreatsRaidMinion", true);
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
		Skeleton self = (Skeleton) (Object) this;
		if (!(self instanceof RaidParticipantEntity raid)) {
			return;
		}
		if (!input.getBooleanOr("EvolvingThreatsRaidMinion", false)) {
			return;
		}
		raid.evolvingthreats$setRaidMinion(true);
		raid.evolvingthreats$setRaidGpsActive(input.getBooleanOr("EvolvingThreatsRaidGps", false));
		input.getString("EvolvingThreatsRaidId").ifPresent(s -> raid.evolvingthreats$setRaidId(UUID.fromString(s)));
		input.getString("EvolvingThreatsRaidHunt").ifPresent(s -> raid.evolvingthreats$setRaidHuntTargetId(UUID.fromString(s)));
	}
}
