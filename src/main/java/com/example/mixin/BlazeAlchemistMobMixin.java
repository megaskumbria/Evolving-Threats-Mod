package com.example.mixin;

import com.example.blaze.BlazeAlchemist;
import com.example.entity.AlchemistBlazeEntity;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 The emergence and preservation of the alchemist.
 FinalizeSpawn and record in the world announced by Mob, Blaze has no copies of its own - so mixin on Mob.
 */
@Mixin(Mob.class)
public class BlazeAlchemistMobMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$rollAlchemistBlaze(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		if ((Object) this instanceof Blaze blaze) {
			BlazeAlchemist.rollOnSpawn(blaze, spawnReason);
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveAlchemistBlaze(ValueOutput output, CallbackInfo ci) {
		if (!((Object) this instanceof AlchemistBlazeEntity data) || !data.evolvingthreats$isAlchemist()) {
			return;
		}
		output.putBoolean("EvolvingThreatsAlchemistBlaze", true);
		output.putInt("EvolvingThreatsAlchemistCooldown", data.evolvingthreats$getAlchemistCooldown());
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadAlchemistBlaze(ValueInput input, CallbackInfo ci) {
		if (!((Object) this instanceof AlchemistBlazeEntity data)) {
			return;
		}
		if (!input.getBooleanOr("EvolvingThreatsAlchemistBlaze", false)) {
			return;
		}
		data.evolvingthreats$setAlchemist(true);
		data.evolvingthreats$setAlchemistCooldown(input.getIntOr("EvolvingThreatsAlchemistCooldown", 0));
	}
}
