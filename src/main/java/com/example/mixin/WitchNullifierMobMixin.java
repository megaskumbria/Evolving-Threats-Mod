package com.example.mixin;

import com.example.entity.NullifierWitchEntity;
import com.example.witch.WitchNullifier;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 The emergence and preservation of the Zero Witch.
 FinalizeSpawn and record in the world announced by Mob, Witch does not have its own copies - so mixin on Mob.
 */
@Mixin(Mob.class)
public class WitchNullifierMobMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$rollNullifierWitch(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		if ((Object) this instanceof Witch witch) {
			WitchNullifier.rollOnSpawn(witch, spawnReason);
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveNullifierWitch(ValueOutput output, CallbackInfo ci) {
		if (!((Object) this instanceof NullifierWitchEntity data) || !data.evolvingthreats$isNullifier()) {
			return;
		}
		output.putBoolean("EvolvingThreatsNullifierWitch", true);
		output.putInt("EvolvingThreatsNullifierCooldown", data.evolvingthreats$getNullifyCooldown());
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadNullifierWitch(ValueInput input, CallbackInfo ci) {
		if (!((Object) this instanceof NullifierWitchEntity data)) {
			return;
		}
		if (!input.getBooleanOr("EvolvingThreatsNullifierWitch", false)) {
			return;
		}
		data.evolvingthreats$setNullifier(true);
		data.evolvingthreats$setNullifyCooldown(input.getIntOr("EvolvingThreatsNullifierCooldown", 0));
	}
}
