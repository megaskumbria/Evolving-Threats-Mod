package com.example.mixin;

import com.example.entity.ShulkerVariant;
import com.example.shulker.ShulkerThreats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Появление шалкера, запись редких свойств и тик.
 * Выстрел и попадание снаряда — отдельные mixin. Числа — {@link ShulkerThreats}.
 */
@Mixin(Shulker.class)
public class ShulkerThreatMixin implements ShulkerVariant {

	@Unique
	private boolean evolvingthreats$rolled;

	@Unique
	private boolean evolvingthreats$slows;

	@Unique
	private boolean evolvingthreats$doubleShot;

	@Unique
	private boolean evolvingthreats$teleportsPlayer;

	@Unique
	private int evolvingthreats$secondShotTicks;

	@Unique
	private int evolvingthreats$hopTicks = ShulkerThreats.NEIGHBOR_HOP_SECONDS * 20;

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$rollShulker(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		ShulkerThreats.rollOnSpawn((Shulker) (Object) this);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveShulker(ValueOutput output, CallbackInfo ci) {
		if (!evolvingthreats$rolled) {
			return;
		}
		output.putBoolean("EvolvingThreatsShulkerRolled", true);
		output.putBoolean("EvolvingThreatsShulkerSlowness", evolvingthreats$slows);
		output.putBoolean("EvolvingThreatsShulkerDouble", evolvingthreats$doubleShot);
		output.putBoolean("EvolvingThreatsShulkerTeleport", evolvingthreats$teleportsPlayer);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadShulker(ValueInput input, CallbackInfo ci) {
		if (!input.getBooleanOr("EvolvingThreatsShulkerRolled", false)) {
			return;
		}
		evolvingthreats$rolled = true;
		evolvingthreats$slows = input.getBooleanOr("EvolvingThreatsShulkerSlowness", false);
		evolvingthreats$doubleShot = input.getBooleanOr("EvolvingThreatsShulkerDouble", false);
		evolvingthreats$teleportsPlayer = input.getBooleanOr("EvolvingThreatsShulkerTeleport", false);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void evolvingthreats$shulkerTick(CallbackInfo ci) {
		if (((Shulker) (Object) this).level() instanceof ServerLevel) {
			ShulkerThreats.tick((Shulker) (Object) this);
		}
	}

	@Override
	public boolean evolvingthreats$rolled() {
		return evolvingthreats$rolled;
	}

	@Override
	public void evolvingthreats$setRolled(boolean rolled) {
		this.evolvingthreats$rolled = rolled;
	}

	@Override
	public boolean evolvingthreats$slows() {
		return evolvingthreats$slows;
	}

	@Override
	public void evolvingthreats$setSlows(boolean slows) {
		this.evolvingthreats$slows = slows;
	}

	@Override
	public boolean evolvingthreats$doubleShot() {
		return evolvingthreats$doubleShot;
	}

	@Override
	public void evolvingthreats$setDoubleShot(boolean doubleShot) {
		this.evolvingthreats$doubleShot = doubleShot;
	}

	@Override
	public boolean evolvingthreats$teleportsPlayer() {
		return evolvingthreats$teleportsPlayer;
	}

	@Override
	public void evolvingthreats$setTeleportsPlayer(boolean teleportsPlayer) {
		this.evolvingthreats$teleportsPlayer = teleportsPlayer;
	}

	@Override
	public int evolvingthreats$secondShotTicks() {
		return evolvingthreats$secondShotTicks;
	}

	@Override
	public void evolvingthreats$setSecondShotTicks(int ticks) {
		this.evolvingthreats$secondShotTicks = ticks;
	}

	@Override
	public int evolvingthreats$hopTicks() {
		return evolvingthreats$hopTicks;
	}

	@Override
	public void evolvingthreats$setHopTicks(int ticks) {
		this.evolvingthreats$hopTicks = ticks;
	}
}
