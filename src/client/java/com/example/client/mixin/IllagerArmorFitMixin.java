package com.example.client.mixin;

import com.example.client.illager.IllagerArmorFit;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After the illager is posed, hide the robe when armor is worn. The nose is not touched.
 * The draw happens immediately after this, so the next illager's pose puts the robe back if needed.
 */
@Mixin(IllagerModel.class)
public class IllagerArmorFitMixin {

	@Inject(method = "setupAnim", at = @At("RETURN"))
	private void evolvingthreats$fitWornArmor(IllagerRenderState state, CallbackInfo ci) {
		IllagerArmorFit.apply((IllagerModel<?>) (Object) this, state);
	}
}
