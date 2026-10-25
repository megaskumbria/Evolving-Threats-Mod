package com.example.mixin;

import com.example.entity.WeakeningPhantomEntity;
import com.example.phantom.PhantomWeakness;
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
 * Появление, сохранение и укус фантома со слабостью.
 * Появление и запись в мир объявлены у Mob, у Phantom своих копий нет — поэтому mixin на Mob.
 * Укус срабатывает, когда пикирование уже ударило цель. Если удар не прошёл, слабости нет.
 */
@Mixin(Mob.class)
public class PhantomWeaknessMobMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$rollPhantomWeakness(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		if ((Object) this instanceof Phantom phantom) {
			PhantomWeakness.rollOnSpawn(phantom);
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$savePhantomWeakness(ValueOutput output, CallbackInfo ci) {
		if ((Object) this instanceof WeakeningPhantomEntity data && data.evolvingthreats$weakensOnBite()) {
			output.putBoolean("EvolvingThreatsPhantomWeakness", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadPhantomWeakness(ValueInput input, CallbackInfo ci) {
		if ((Object) this instanceof WeakeningPhantomEntity data) {
			data.evolvingthreats$setWeakensOnBite(input.getBooleanOr("EvolvingThreatsPhantomWeakness", false));
		}
	}

	@Inject(method = "doHurtTarget", at = @At("RETURN"))
	private void evolvingthreats$phantomWeakness(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		if ((Object) this instanceof Phantom phantom && target instanceof LivingEntity living) {
			PhantomWeakness.onBite(phantom, living);
		}
	}
}
