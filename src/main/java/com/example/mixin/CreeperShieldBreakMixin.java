package com.example.mixin;

import com.example.entity.ShieldBreakCreeperEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 Creeper: Flag breaks shield and preservation
 * Chance upon appearance - in {@link MobDynamiteCreeperSpawnMixin}, after the bomber and thrower.
 Acts like an ordinary cryper: he runs up and explodes. The shield breaks at the moment of the explosion block.
 */
@Mixin(Creeper.class)
public class CreeperShieldBreakMixin implements ShieldBreakCreeperEntity {

	@Unique
	private boolean evolvingthreats$shieldBreakCreeper;

	@Override
	public boolean evolvingthreats$isShieldBreakCreeper() {
		return evolvingthreats$shieldBreakCreeper;
	}

	@Override
	public void evolvingthreats$setShieldBreakCreeper(boolean shieldBreak) {
		this.evolvingthreats$shieldBreakCreeper = shieldBreak;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveShieldBreakCreeper(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$shieldBreakCreeper) {
			output.putBoolean("EvolvingThreatsShieldBreakCreeper", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadShieldBreakCreeper(ValueInput input, CallbackInfo ci) {
		evolvingthreats$shieldBreakCreeper = input.getBooleanOr("EvolvingThreatsShieldBreakCreeper", false);
	}
}
