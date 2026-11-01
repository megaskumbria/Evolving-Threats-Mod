package com.example.mixin;

import com.example.entity.NullifierWitchEntity;
import com.example.witch.WitchNullifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The witch keeps the flag "zero" and a pause between white potions.
 * Chance to appear and record in the world - {@link WitchNullifierMobMixin}
 * The bottle itself — @link ThrownSplashPotionNullifyMixin.
 */
@Mixin(Witch.class)
public class WitchNullifierMixin implements NullifierWitchEntity {

	@Unique
	private boolean evolvingthreats$nullifier;

	@Unique
	private int evolvingthreats$nullifyCooldown;

	@Unique
	private boolean evolvingthreats$nullifyArmed;

	@Override
	public boolean evolvingthreats$isNullifier() {
		return evolvingthreats$nullifier;
	}

	@Override
	public void evolvingthreats$setNullifier(boolean nullifier) {
		this.evolvingthreats$nullifier = nullifier;
	}

	@Override
	public int evolvingthreats$getNullifyCooldown() {
		return evolvingthreats$nullifyCooldown;
	}

	@Override
	public void evolvingthreats$setNullifyCooldown(int ticks) {
		this.evolvingthreats$nullifyCooldown = Math.max(0, ticks);
	}

	@Override
	public boolean evolvingthreats$isNullifyArmed() {
		return evolvingthreats$nullifyArmed;
	}

	@Override
	public void evolvingthreats$setNullifyArmed(boolean armed) {
		this.evolvingthreats$nullifyArmed = armed;
	}

	@Inject(method = "aiStep", at = @At("RETURN"))
	private void evolvingthreats$nullifierCooldown(CallbackInfo ci) {
		WitchNullifier.tickCooldown((Witch) (Object) this);
	}

	@Inject(method = "performRangedAttack", at = @At("HEAD"))
	private void evolvingthreats$armNullifyThrow(LivingEntity target, float power, CallbackInfo ci) {
		WitchNullifier.armThrow((Witch) (Object) this, target);
	}
}
