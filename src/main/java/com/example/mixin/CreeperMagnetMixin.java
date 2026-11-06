package com.example.mixin;

import com.example.creeper.MagnetCreeper;
import com.example.entity.MagnetCreeperEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creeper remembers it is a magnet and how long it has already been pulling.
 * Spawn chance is in {@link MobDynamiteCreeperSpawnMixin}. The pull runs every tick.
 */
@Mixin(Creeper.class)
public class CreeperMagnetMixin implements MagnetCreeperEntity {

	@Unique
	private boolean evolvingthreats$magnetCreeper;

	@Unique
	private int evolvingthreats$magnetPullTicks;

	@Unique
	private int evolvingthreats$magnetLostTicks;

	@Override
	public boolean evolvingthreats$isMagnetCreeper() {
		return evolvingthreats$magnetCreeper;
	}

	@Override
	public void evolvingthreats$setMagnetCreeper(boolean magnet) {
		this.evolvingthreats$magnetCreeper = magnet;
	}

	@Override
	public int evolvingthreats$getMagnetPullTicks() {
		return evolvingthreats$magnetPullTicks;
	}

	@Override
	public void evolvingthreats$setMagnetPullTicks(int ticks) {
		this.evolvingthreats$magnetPullTicks = Math.max(0, ticks);
	}

	@Override
	public int evolvingthreats$getMagnetLostTicks() {
		return evolvingthreats$magnetLostTicks;
	}

	@Override
	public void evolvingthreats$setMagnetLostTicks(int ticks) {
		this.evolvingthreats$magnetLostTicks = Math.max(0, ticks);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void evolvingthreats$magnetTick(CallbackInfo ci) {
		MagnetCreeper.tick((Creeper) (Object) this);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveMagnetCreeper(ValueOutput output, CallbackInfo ci) {
		if (!evolvingthreats$magnetCreeper) {
			return;
		}
		output.putBoolean("EvolvingThreatsMagnetCreeper", true);
		if (evolvingthreats$magnetPullTicks > 0) {
			output.putInt("EvolvingThreatsMagnetPullTicks", evolvingthreats$magnetPullTicks);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadMagnetCreeper(ValueInput input, CallbackInfo ci) {
		evolvingthreats$magnetCreeper = input.getBooleanOr("EvolvingThreatsMagnetCreeper", false);
		evolvingthreats$magnetPullTicks = input.getIntOr("EvolvingThreatsMagnetPullTicks", 0);
	}
}
