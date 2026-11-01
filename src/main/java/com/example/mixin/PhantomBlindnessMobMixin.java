package com.example.mixin;

import com.example.entity.BlindingPhantomEntity;
import com.example.phantom.PhantomBlindness;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 The appearance, preservation and bite of a phantom with blindness.
 Appearance and record in the world announced by Mob, Phantom has no copies of its own - so mixin on Mob.
 The bite is triggered when the dive has already hit the target. If the blow didn't pass, there's no blindness.
 */
@Mixin(Mob.class)
public class PhantomBlindnessMobMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$rollPhantomBlindness(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		if ((Object) this instanceof Phantom phantom) {
			PhantomBlindness.rollOnSpawn(phantom);
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$savePhantomBlindness(ValueOutput output, CallbackInfo ci) {
		if ((Object) this instanceof BlindingPhantomEntity data && data.evolvingthreats$blindsOnBite()) {
			output.putBoolean("EvolvingThreatsPhantomBlindness", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadPhantomBlindness(ValueInput input, CallbackInfo ci) {
		if ((Object) this instanceof BlindingPhantomEntity data) {
			data.evolvingthreats$setBlindsOnBite(input.getBooleanOr("EvolvingThreatsPhantomBlindness", false));
		}
	}

	@Inject(method = "doHurtTarget", at = @At("RETURN"))
	private void evolvingthreats$phantomBlindness(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		if ((Object) this instanceof Phantom phantom && target instanceof LivingEntity living) {
			PhantomBlindness.onBite(phantom, living);
		}
	}
}
