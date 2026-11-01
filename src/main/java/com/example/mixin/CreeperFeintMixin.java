package com.example.mixin;

import com.example.creeper.FeintCreeper;
import com.example.entity.FeintCreeperEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creeper remembers it is a feint, at what distance it breaks cover, and whether it already revealed.
 * Spawn chance is in {@link MobDynamiteCreeperSpawnMixin}. The burst is evaluated every tick.
 */
@Mixin(Creeper.class)
public class CreeperFeintMixin implements FeintCreeperEntity {

	@Unique
	private boolean evolvingthreats$feintCreeper;

	@Unique
	private double evolvingthreats$feintDistance;

	@Unique
	private boolean evolvingthreats$feintRevealed;

	@Override
	public boolean evolvingthreats$isFeintCreeper() {
		return evolvingthreats$feintCreeper;
	}

	@Override
	public void evolvingthreats$setFeintCreeper(boolean feint) {
		this.evolvingthreats$feintCreeper = feint;
	}

	@Override
	public double evolvingthreats$getTriggerDistance() {
		return evolvingthreats$feintDistance;
	}

	@Override
	public void evolvingthreats$setTriggerDistance(double blocks) {
		this.evolvingthreats$feintDistance = blocks;
	}

	@Override
	public boolean evolvingthreats$isFeintRevealed() {
		return evolvingthreats$feintRevealed;
	}

	@Override
	public void evolvingthreats$setFeintRevealed(boolean revealed) {
		this.evolvingthreats$feintRevealed = revealed;
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void evolvingthreats$feintTick(CallbackInfo ci) {
		FeintCreeper.tick((Creeper) (Object) this);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveFeintCreeper(ValueOutput output, CallbackInfo ci) {
		if (!evolvingthreats$feintCreeper) {
			return;
		}
		output.putBoolean("EvolvingThreatsFeintCreeper", true);
		output.putDouble("EvolvingThreatsFeintDistance", evolvingthreats$feintDistance);
		if (evolvingthreats$feintRevealed) {
			output.putBoolean("EvolvingThreatsFeintRevealed", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadFeintCreeper(ValueInput input, CallbackInfo ci) {
		evolvingthreats$feintCreeper = input.getBooleanOr("EvolvingThreatsFeintCreeper", false);
		evolvingthreats$feintDistance = input.getDoubleOr("EvolvingThreatsFeintDistance", 0.0);
		evolvingthreats$feintRevealed = input.getBooleanOr("EvolvingThreatsFeintRevealed", false);
	}
}
