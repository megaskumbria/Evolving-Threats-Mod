package com.example.mixin;

import com.example.entity.TwoLeadHandsSkeleton;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers two lead hands after the chunk reloads.
 * Without this, a saved skeleton would swap bow and sword again.
 */
@Mixin(Mob.class)
public class TwoLeadHandsSaveMixin {

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveTwoLeadHands(ValueOutput output, CallbackInfo ci) {
		if ((Object) this instanceof TwoLeadHandsSkeleton data && data.evolvingthreats$isTwoLeadHands()) {
			output.putBoolean("EvolvingThreatsTwoLeadHands", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadTwoLeadHands(ValueInput input, CallbackInfo ci) {
		if ((Object) this instanceof TwoLeadHandsSkeleton data) {
			data.evolvingthreats$setTwoLeadHands(input.getBooleanOr("EvolvingThreatsTwoLeadHands", false));
		}
	}
}
