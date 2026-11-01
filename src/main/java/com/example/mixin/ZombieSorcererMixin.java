package com.example.mixin;

import com.example.entity.ZombieSorcererEntity;
import com.example.zombie.ZombieSorcerer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks a normal zombie as a sorcerer and stores spell cooldown.
 * Spells — {@link com.example.mixin.ZombieSorcererGoalMixin}.
 * Bonus drop — {@link ZombieSorcererLootMixin}.
 */
@Mixin(Zombie.class)
public class ZombieSorcererMixin implements ZombieSorcererEntity {

	@Unique
	private boolean evolvingthreats$sorcerer;

	@Unique
	private int evolvingthreats$sorcererCooldown;

	@Override
	public boolean evolvingthreats$isSorcerer() {
		return evolvingthreats$sorcerer;
	}

	@Override
	public void evolvingthreats$setSorcerer(boolean sorcerer) {
		this.evolvingthreats$sorcerer = sorcerer;
	}

	@Override
	public int evolvingthreats$getSorcererCooldown() {
		return evolvingthreats$sorcererCooldown;
	}

	@Override
	public void evolvingthreats$setSorcererCooldown(int ticks) {
		this.evolvingthreats$sorcererCooldown = Math.max(0, ticks);
	}

	@Inject(method = "getBaseExperienceReward", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$sorcererXp(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
		int xp = ZombieSorcerer.experienceReward((Zombie) (Object) this);
		if (xp >= 0) {
			cir.setReturnValue(xp);
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveSorcerer(ValueOutput output, CallbackInfo ci) {
		if (!evolvingthreats$sorcerer) {
			return;
		}
		output.putBoolean("EvolvingThreatsZombieSorcerer", true);
		output.putInt("EvolvingThreatsZombieSorcererCooldown", evolvingthreats$sorcererCooldown);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadSorcerer(ValueInput input, CallbackInfo ci) {
		evolvingthreats$sorcerer = input.getBooleanOr("EvolvingThreatsZombieSorcerer", false);
		evolvingthreats$sorcererCooldown = input.getIntOr("EvolvingThreatsZombieSorcererCooldown", 0);
	}
}
