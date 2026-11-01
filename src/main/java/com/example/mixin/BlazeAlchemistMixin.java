package com.example.mixin;

import com.example.blaze.BlazeAlchemist;
import com.example.entity.AlchemistBlazeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 Blaze keeps the flag "Alchemist" and a pause between the balls that cut resistance.
 * Chance to appear and record in the world - {@link BlazeAlchemistMobMixin}
 * The ball itself — {@link SmallFireballAlchemyMixin}
 */
@Mixin(Blaze.class)
public class BlazeAlchemistMixin implements AlchemistBlazeEntity {

	@Unique
	private boolean evolvingthreats$alchemist;

	@Unique
	private int evolvingthreats$alchemistCooldown;

	@Override
	public boolean evolvingthreats$isAlchemist() {
		return evolvingthreats$alchemist;
	}

	@Override
	public void evolvingthreats$setAlchemist(boolean alchemist) {
		this.evolvingthreats$alchemist = alchemist;
	}

	@Override
	public int evolvingthreats$getAlchemistCooldown() {
		return evolvingthreats$alchemistCooldown;
	}

	@Override
	public void evolvingthreats$setAlchemistCooldown(int ticks) {
		this.evolvingthreats$alchemistCooldown = Math.max(0, ticks);
	}

	@Inject(method = "customServerAiStep", at = @At("RETURN"))
	private void evolvingthreats$alchemistCooldown(ServerLevel level, CallbackInfo ci) {
		BlazeAlchemist.tickCooldown((Blaze) (Object) this);
	}
}
